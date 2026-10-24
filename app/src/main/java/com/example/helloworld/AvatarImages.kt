package com.example.helloworld

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

internal fun calculateAvatarSampleSize(
    width: Int,
    height: Int,
    targetSize: Int,
    squareCrop: Boolean
): Int {
    val relevantEdge = if (squareCrop) min(width, height) else max(width, height)
    var sampleSize = 1
    if (squareCrop) {
        while (relevantEdge / (sampleSize * 2) >= targetSize) sampleSize *= 2
    } else {
        while (relevantEdge / sampleSize > targetSize) sampleSize *= 2
    }
    return sampleSize
}

/**
 * Loads member photos without decoding camera-sized images on the UI thread.
 * List surfaces use a small, persistent square thumbnail. The original source is
 * only decoded (to a screen-sized bitmap) after the user opens the photo viewer.
 */
object AvatarImages {
    private const val THUMBNAIL_EDGE_PX = 320
    private const val MEMORY_CACHE_KB = 12 * 1024

    private val worker = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val memory = object : LruCache<String, Bitmap>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun bindThumbnail(
        context: Context,
        target: ImageView,
        sourceUri: String,
        fallbackResource: Int,
        onLoaded: ((Boolean) -> Unit)? = null
    ) {
        val requestKey = "avatar-thumb:$sourceUri"
        target.tag = requestKey
        target.setImageResource(fallbackResource)

        memory.get(requestKey)?.let { bitmap ->
            target.setImageBitmap(bitmap)
            onLoaded?.invoke(true)
            return
        }

        val appContext = context.applicationContext
        val targetReference = WeakReference(target)
        worker.execute {
            val bitmap = runCatching { loadOrCreateThumbnail(appContext, sourceUri) }.getOrNull()
            if (bitmap != null) memory.put(requestKey, bitmap)
            main.post {
                val image = targetReference.get() ?: return@post
                if (image.tag != requestKey) return@post
                if (bitmap != null) image.setImageBitmap(bitmap) else image.setImageResource(fallbackResource)
                onLoaded?.invoke(bitmap != null)
            }
        }
    }

    fun clear(target: ImageView, fallbackResource: Int) {
        target.tag = null
        target.setImageResource(fallbackResource)
    }

    fun evict(context: Context, sourceUri: String) {
        memory.remove("avatar-thumb:$sourceUri")
        thumbnailFile(context, sourceUri).delete()
    }

    fun loadOriginalPreview(
        context: Context,
        sourceUri: String,
        maxDimension: Int,
        onLoaded: (Bitmap?) -> Unit
    ) {
        val appContext = context.applicationContext
        worker.execute {
            val bitmap = runCatching {
                decodeSource(appContext, sourceUri, maxDimension.coerceAtLeast(1), squareCrop = false)
            }.getOrNull()
            main.post { onLoaded(bitmap) }
        }
    }

    private fun loadOrCreateThumbnail(context: Context, sourceUri: String): Bitmap? {
        val cachedFile = thumbnailFile(context, sourceUri)
        BitmapFactory.decodeFile(cachedFile.path)?.let { return it }
        if (cachedFile.exists()) cachedFile.delete()

        val bitmap = decodeSource(context, sourceUri, THUMBNAIL_EDGE_PX, squareCrop = true)
            ?: return null
        cachedFile.parentFile?.mkdirs()
        val temporary = File(cachedFile.parentFile, "${cachedFile.name}.${UUID.randomUUID()}.tmp")
        runCatching {
            temporary.outputStream().buffered().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
            }
            if (!temporary.renameTo(cachedFile)) {
                temporary.copyTo(cachedFile, overwrite = true)
                temporary.delete()
            }
        }.onFailure { temporary.delete() }
        return bitmap
    }

    private fun decodeSource(
        context: Context,
        sourceUri: String,
        targetSize: Int,
        squareCrop: Boolean
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openSource(context, sourceUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateAvatarSampleSize(
            bounds.outWidth,
            bounds.outHeight,
            targetSize,
            squareCrop
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = if (squareCrop) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        var bitmap = openSource(context, sourceUri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val orientation = readOrientation(context, sourceUri)
        bitmap = transformOrientation(bitmap, orientation)

        if (squareCrop) {
            val edge = min(bitmap.width, bitmap.height)
            val cropped = Bitmap.createBitmap(
                bitmap,
                (bitmap.width - edge) / 2,
                (bitmap.height - edge) / 2,
                edge,
                edge
            )
            if (cropped !== bitmap) bitmap.recycle()
            val outputEdge = min(edge, targetSize)
            if (cropped.width != outputEdge) {
                val scaled = Bitmap.createScaledBitmap(cropped, outputEdge, outputEdge, true)
                if (scaled !== cropped) cropped.recycle()
                bitmap = scaled
            } else {
                bitmap = cropped
            }
        } else {
            val longestEdge = max(bitmap.width, bitmap.height)
            if (longestEdge > targetSize) {
                val scale = targetSize.toFloat() / longestEdge
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
        }
        return bitmap
    }

    private fun readOrientation(context: Context, sourceUri: String): Int = runCatching {
        openSource(context, sourceUri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun transformOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        val transformed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (transformed !== source) source.recycle()
        return transformed
    }

    private fun openSource(context: Context, sourceUri: String): InputStream? {
        val uri = Uri.parse(sourceUri)
        return if (uri.scheme.isNullOrBlank()) {
            File(sourceUri).takeIf(File::exists)?.inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun thumbnailFile(context: Context, sourceUri: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(sourceUri.toByteArray(Charsets.UTF_8))
        val key = buildString(digest.size * 2) {
            digest.forEach { byte -> append("%02x".format(byte.toInt() and 0xff)) }
        }
        return File(File(context.cacheDir, "operative_thumbnails"), "$key.jpg")
    }
}

/** A lightweight photo viewer with pinch-to-zoom, drag and double-tap reset. */
class ZoomPhotoView(context: Context) : AppCompatImageView(context) {
    private val transform = Matrix()
    private var zoom = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTapUp = 0L
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (zoom * detector.scaleFactor).coerceIn(1f, 8f)
                transform.postScale(next / zoom, next / zoom, detector.focusX, detector.focusY)
                zoom = next
                imageMatrix = transform
                return true
            }
        }
    )

    init {
        scaleType = ImageView.ScaleType.MATRIX
    }

    fun show(bitmap: Bitmap) {
        setImageBitmap(bitmap)
        post { resetTransform() }
    }

    private fun resetTransform() {
        val image = drawable ?: return
        if (width == 0 || height == 0 || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
        val scale = min(
            width.toFloat() / image.intrinsicWidth,
            height.toFloat() / image.intrinsicHeight
        )
        transform.setScale(scale, scale)
        transform.postTranslate(
            (width - image.intrinsicWidth * scale) / 2f,
            (height - image.intrinsicHeight * scale) / 2f
        )
        zoom = 1f
        imageMatrix = transform
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        resetTransform()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> if (!scaleDetector.isInProgress && event.pointerCount == 1 && zoom > 1f) {
                transform.postTranslate(event.x - lastX, event.y - lastY)
                imageMatrix = transform
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (event.eventTime - lastTapUp < 300L) resetTransform()
                lastTapUp = event.eventTime
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
