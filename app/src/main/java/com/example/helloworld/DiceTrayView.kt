package com.example.helloworld

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class DiceTrayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val ANIMATION_DURATION_MILLIS = 2850L
        const val ANIMATION_DURATION_SECONDS = ANIMATION_DURATION_MILLIS / 1000f
        const val LANDING_START_PROGRESS = 0.58f
    }

    private data class Vec3(val x: Float, val y: Float, val z: Float) {
        operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
        operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
    }

    private data class DieFace(
        val value: Int,
        val center: Vec3,
        val u: Vec3,
        val v: Vec3,
        val normal: Vec3
    )

    private data class VisibleFace(
        val face: DieFace,
        val corners: List<Vec3>,
        val normal: Vec3,
        val depth: Float
    )

    private data class DicePalette(
        val face: Int,
        val side: Int,
        val edge: Int,
        val pip: Int,
        val scratch: Int,
        val accent: Int,
        val corrosion: Int
    )

    private data class DieBody(
        val index: Int,
        var value: Int,
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        var orientationX: Float,
        var orientationY: Float,
        var orientationZ: Float,
        var angularVelocityX: Float,
        var angularVelocityY: Float,
        var angularVelocityZ: Float,
        var height: Float,
        var verticalVelocity: Float,
        var selected: Boolean = false,
        var settled: Boolean = false,
        var finalizing: Boolean = false,
        var targetOrientationX: Float = 0f,
        var targetOrientationY: Float = 0f,
        var targetOrientationZ: Float = 0f,
        var landingStartOrientationX: Float = 0f,
        var landingStartOrientationY: Float = 0f,
        var landingStartOrientationZ: Float = 0f
    )

    private val density = resources.displayMetrics.density
    private val dicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(216, 203, 168)
        setShadowLayer(8f * density, 0f, 5f * density, 0x88000000.toInt())
    }
    private val tumblingFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(6f * density, 0f, 4f * density, 0x76000000)
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.rgb(112, 96, 66)
    }
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 23, 19) }
    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(103, 89, 62) }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.rgb(255, 190, 48)
        setShadowLayer(12f * density, 0f, 0f, 0xDDFF9D00.toInt())
    }
    private val scratchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4081724F
        strokeWidth = 0.7f * density
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.15f * density
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val faces = listOf(
        DieFace(1, Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f)),
        DieFace(6, Vec3(0f, 0f, -1f), Vec3(1f, 0f, 0f), Vec3(0f, -1f, 0f), Vec3(0f, 0f, -1f)),
        DieFace(2, Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f)),
        DieFace(5, Vec3(0f, -1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, -1f, 0f)),
        DieFace(3, Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f)),
        DieFace(4, Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, -1f), Vec3(-1f, 0f, 0f))
    )

    private val bodies = mutableListOf<DieBody>()
    private var animator: ValueAnimator? = null
    private var renderedDieSize = 0f
    private var lastFrameNanos = 0L
    private var selectionChanged: ((Int) -> Unit)? = null
    private var throwRequested: (() -> Unit)? = null
    private var resetRequested: (() -> Unit)? = null
    private var horizontalSwipeRequested: ((Int) -> Unit)? = null
    private var diceSelectionEnabled = true
    private var downX = 0f
    private var downY = 0f
    private var maxPointers = 1
    private var multiStartY = 0f
    private var multiLastY = 0f
    private var lastEmptyTapAt = 0L
    private var diceTheme = DiceTheme.ANGELS_OF_DEATH
    private var rerollRule = RerollRule.MANUAL

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = false
        applyThemePalette()
    }

    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        selectionChanged = listener
    }

    fun setOnTrayGestureListener(onThrow: () -> Unit, onReset: () -> Unit) {
        throwRequested = onThrow
        resetRequested = onReset
    }

    fun setOnHorizontalSwipeListener(listener: (Int) -> Unit) {
        horizontalSwipeRequested = listener
    }

    fun setDiceSelectionEnabled(enabled: Boolean) {
        diceSelectionEnabled = enabled
        if (!enabled) clearSelection()
    }

    fun setDiceTheme(theme: DiceTheme) {
        if (diceTheme == theme) return
        diceTheme = theme
        applyThemePalette()
        invalidate()
    }

    fun setRerollRule(rule: RerollRule) {
        if (rerollRule == rule) return
        rerollRule = rule
        clearSelection()
    }

    fun selectedIndices(): List<Int> = bodies.filter { it.selected }.map { it.index }.sorted()

    fun clearSelection() {
        bodies.forEach { it.selected = false }
        selectionChanged?.invoke(0)
        invalidate()
    }

    fun restoreResults(results: List<Int>) {
        animator?.cancel()
        bodies.clear()
        if (results.isEmpty()) {
            selectionChanged?.invoke(0)
            invalidate()
            return
        }
        val tray = resultArea()
        renderedDieSize = fixedDieSize(tray)
        val columns = ceil(sqrt(results.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(results.size / columns.toFloat()).toInt()
        val cell = renderedDieSize * 1.45f
        val startX = tray.centerX() - (columns - 1) * cell / 2f
        val startY = tray.centerY() - (rows - 1) * cell / 2f
        results.forEachIndexed { index, value ->
            val row = index / columns
            val column = index % columns
            bodies += DieBody(
                index = index,
                value = value,
                x = startX + column * cell,
                y = startY + row * cell,
                velocityX = 0f,
                velocityY = 0f,
                orientationX = canonicalOrientation(value).first,
                orientationY = canonicalOrientation(value).second,
                orientationZ = ((index * 17) % 23 - 11).toFloat(),
                angularVelocityX = 0f,
                angularVelocityY = 0f,
                angularVelocityZ = 0f,
                height = 0f,
                verticalVelocity = 0f,
                settled = true
            )
        }
        selectionChanged?.invoke(0)
        invalidate()
    }

    fun roll(count: Int, onComplete: (List<Int>) -> Unit) {
        if (width == 0 || height == 0) {
            post { roll(count, onComplete) }
            return
        }
        animator?.cancel()
        bodies.clear()
        if (count <= 0) {
            invalidate()
            onComplete(emptyList())
            return
        }

        val tray = resultArea()
        renderedDieSize = fixedDieSize(tray)
        val random = Random(System.nanoTime())
        repeat(count) { index ->
            val angle = Math.PI * 2.0 * index / count + random.nextDouble(-0.45, 0.45)
            val startRadius = tray.width() * random.nextDouble(0.08, 0.22).toFloat()
            val speed = tray.width() * random.nextDouble(0.9, 1.25).toFloat()
            bodies += DieBody(
                index = index,
                value = 1,
                x = tray.centerX() + cos(angle).toFloat() * startRadius,
                y = tray.centerY() + sin(angle).toFloat() * startRadius,
                velocityX = cos(angle).toFloat() * speed + random.nextFloat() * 180f - 90f,
                velocityY = sin(angle).toFloat() * speed + random.nextFloat() * 180f - 90f,
                orientationX = random.nextFloat() * 360f,
                orientationY = random.nextFloat() * 360f,
                orientationZ = random.nextFloat() * 360f,
                angularVelocityX = signedSpin(random, 720f, 1180f),
                angularVelocityY = signedSpin(random, 760f, 1220f),
                angularVelocityZ = signedSpin(random, 980f, 1480f),
                height = random.nextFloat() * 1.2f + 3.2f,
                verticalVelocity = -(random.nextFloat() * 0.9f + 0.25f)
            )
        }
        selectionChanged?.invoke(0)
        startAnimation(tray) {
            onComplete(bodies.sortedBy { it.index }.map { it.value })
        }
    }

    fun rerollSelected(onComplete: (List<Int>) -> Unit) {
        val selectedBodies = bodies.filter { it.selected }.sortedBy { it.index }
        if (selectedBodies.isEmpty()) return

        animator?.cancel()
        val random = Random(System.nanoTime())
        val tray = resultArea()
        selectedBodies.forEach { body ->
            val angle = random.nextDouble(0.0, Math.PI * 2.0)
            val speed = tray.width() * random.nextDouble(0.72, 1.05).toFloat()
            body.velocityX = cos(angle).toFloat() * speed
            body.velocityY = sin(angle).toFloat() * speed
            body.orientationX = random.nextFloat() * 360f
            body.orientationY = random.nextFloat() * 360f
            body.angularVelocityX = signedSpin(random, 760f, 1220f)
            body.angularVelocityY = signedSpin(random, 780f, 1260f)
            body.angularVelocityZ = signedSpin(random, 1050f, 1550f)
            body.height = random.nextFloat() * 1.2f + 3.3f
            body.verticalVelocity = -(random.nextFloat() * 0.9f + 0.3f)
            body.selected = false
            body.settled = false
            body.finalizing = false
        }
        selectionChanged?.invoke(0)
        startAnimation(tray) { onComplete(selectedBodies.map { it.value }) }
    }

    private fun startAnimation(tray: RectF, onComplete: () -> Unit) {
        lastFrameNanos = System.nanoTime()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MILLIS
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val now = System.nanoTime()
                val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.008f, 0.033f)
                lastFrameNanos = now
                simulate(dt, tray, animation.animatedValue as Float)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    bodies.filterNot { it.settled }.forEach { finishLanding(it) }
                    bodies.forEach {
                        it.velocityX = 0f
                        it.velocityY = 0f
                        it.angularVelocityX = 0f
                        it.angularVelocityY = 0f
                        it.angularVelocityZ = 0f
                        it.height = 0f
                        it.verticalVelocity = 0f
                    }
                    invalidate()
                    onComplete()
                }
            })
            start()
        }
    }

    fun clearResults() {
        animator?.cancel()
        bodies.clear()
        selectionChanged?.invoke(0)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                maxPointers = 1
                multiStartY = event.y
                multiLastY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, event.pointerCount)
                multiStartY = averagePointerY(event)
                multiLastY = multiStartY
            }
            MotionEvent.ACTION_MOVE -> {
                maxPointers = maxOf(maxPointers, event.pointerCount)
                if (event.pointerCount >= 2) multiLastY = averagePointerY(event)
            }
            MotionEvent.ACTION_UP -> {
                if (animator?.isRunning == true) return true
                val gestureDistance = min(width, height) * 0.1f
                val horizontalDistance = event.x - downX
                val verticalDistance = event.y - downY
                if (
                    maxPointers == 1 &&
                    abs(horizontalDistance) > gestureDistance &&
                    abs(horizontalDistance) > abs(verticalDistance) * 1.25f
                ) {
                    horizontalSwipeRequested?.invoke(if (horizontalDistance < 0f) 1 else -1)
                    performClick()
                    return true
                }
                if (maxPointers >= 2) {
                    if (multiLastY - multiStartY > gestureDistance) resetRequested?.invoke()
                    return true
                }
                if (event.y - downY < -gestureDistance) {
                    throwRequested?.invoke()
                    return true
                }
                if (!diceSelectionEnabled) return true
                val hitRadius = renderedDieSize * 0.72f
                val hit = bodies.filter { body ->
                    val dx = event.x - body.x
                    val dy = event.y - body.y
                    dx * dx + dy * dy <= hitRadius * hitRadius
                }.minByOrNull { body ->
                    val dx = event.x - body.x
                    val dy = event.y - body.y
                    dx * dx + dy * dy
                }
                if (hit != null) {
                    if (hit.selected) {
                        hit.selected = false
                    } else {
                        when (rerollRule) {
                            RerollRule.BALANCED -> bodies.forEach { it.selected = false }
                            RerollRule.CEASELESS -> bodies
                                .filter { it.value != hit.value }
                                .forEach { it.selected = false }
                            RerollRule.MANUAL,
                            RerollRule.RELENTLESS -> Unit
                        }
                        hit.selected = true
                    }
                    performClick()
                    selectionChanged?.invoke(selectedIndices().size)
                    invalidate()
                } else {
                    clearSelection()
                    val now = System.currentTimeMillis()
                    if (now - lastEmptyTapAt <= 320L) throwRequested?.invoke()
                    lastEmptyTapAt = now
                }
            }
        }
        return true
    }

    private fun averagePointerY(event: MotionEvent): Float {
        var total = 0f
        for (index in 0 until event.pointerCount) total += event.getY(index)
        return total / event.pointerCount.coerceAtLeast(1)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw <= 0 || oldh <= 0 || bodies.isEmpty()) return

        val oldTray = resultArea(oldw, oldh)
        val newTray = resultArea(w, h)
        val scale = if (oldTray.width() > 0f) newTray.width() / oldTray.width() else 1f
        bodies.forEach { body ->
            body.x = newTray.centerX() + (body.x - oldTray.centerX()) * scale
            body.y = newTray.centerY() + (body.y - oldTray.centerY()) * scale
        }
        renderedDieSize = fixedDieSize(newTray)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bodies.forEach { body ->
            val visibleHeight = if (body.settled) 0f else body.height.coerceAtLeast(0f)
            val airborneScale = 1f + visibleHeight * 0.07f
            drawTumblingDie(
                canvas,
                body.x,
                body.y - visibleHeight * renderedDieSize * 0.18f,
                renderedDieSize * airborneScale,
                body
            )
        }
    }

    private fun simulate(dt: Float, tray: RectF, progress: Float) {
        val centerX = tray.centerX()
        val centerY = tray.centerY()
        val boundaryRadius = tray.width() / 2f - renderedDieSize * 0.72f

        bodies.forEach { body ->
            if (!body.settled) {
                body.verticalVelocity -= 9.8f * dt
                body.height += body.verticalVelocity * dt
                if (body.height < 0f) {
                    body.height = 0f
                    body.verticalVelocity = if (body.verticalVelocity < -0.65f) {
                        -body.verticalVelocity * 0.46f
                    } else 0f
                }

                if (!body.finalizing) {
                    body.orientationX += body.angularVelocityX * dt
                    body.orientationY += body.angularVelocityY * dt
                    body.orientationZ += body.angularVelocityZ * dt
                }

                val planarDamping = (if (body.height > 0.02f) 0.998f else 0.988f).pow(dt * 60f)
                val spinDamping = (if (body.height > 0.02f) 0.999f else 0.994f).pow(dt * 60f)
                body.velocityX *= planarDamping
                body.velocityY *= planarDamping
                body.angularVelocityX *= spinDamping
                body.angularVelocityY *= spinDamping
                body.angularVelocityZ *= spinDamping

                if (progress >= LANDING_START_PROGRESS && !body.finalizing) beginLanding(body)
                if (body.finalizing) {
                    val landingProgress = landingProgress(progress)
                    val easedProgress = easeOutCubic(landingProgress)
                    body.orientationX = lerp(
                        body.landingStartOrientationX, body.targetOrientationX, easedProgress
                    )
                    body.orientationY = lerp(
                        body.landingStartOrientationY, body.targetOrientationY, easedProgress
                    )
                    body.orientationZ = lerp(
                        body.landingStartOrientationZ, body.targetOrientationZ, easedProgress
                    )
                    body.height *= 0.82f
                    body.verticalVelocity *= 0.72f
                    body.angularVelocityX *= 0.72f
                    body.angularVelocityY *= 0.72f
                    body.angularVelocityZ *= 0.72f

                    // Once the result is known, friction must only remove energy. A strong,
                    // frame-rate-independent drag prevents a late collision from making a die
                    // visibly speed up just before it settles.
                    val settlingDamping = 0.955f.pow(dt * 60f)
                    body.velocityX *= settlingDamping
                    body.velocityY *= settlingDamping
                }
            }

            body.x += body.velocityX * dt
            body.y += body.velocityY * dt
            val dx = body.x - centerX
            val dy = body.y - centerY
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            if (distance > boundaryRadius) {
                val nx = dx / distance
                val ny = dy / distance
                body.x = centerX + nx * boundaryRadius
                body.y = centerY + ny * boundaryRadius
                val outwardSpeed = body.velocityX * nx + body.velocityY * ny
                if (outwardSpeed > 0f) {
                    val wallRestitution = lerp(0.68f, 0.04f, landingProgress(progress))
                    body.velocityX -= (1f + wallRestitution) * outwardSpeed * nx
                    body.velocityY -= (1f + wallRestitution) * outwardSpeed * ny
                    body.angularVelocityX *= 0.92f
                    body.angularVelocityY *= 0.92f
                    body.angularVelocityZ *= -0.78f
                }
            }
        }
        resolvePairCollisions(centerX, centerY, boundaryRadius, progress)
    }

    private fun resolvePairCollisions(
        centerX: Float,
        centerY: Float,
        boundaryRadius: Float,
        progress: Float
    ) {
        val minimumDistance = renderedDieSize * 1.16f
        val collisionRestitution = lerp(0.68f, 0.04f, landingProgress(progress))
        repeat(2) {
            for (firstIndex in 0 until bodies.lastIndex) {
                for (secondIndex in firstIndex + 1 until bodies.size) {
                    val first = bodies[firstIndex]
                    val second = bodies[secondIndex]
                    var dx = second.x - first.x
                    var dy = second.y - first.y
                    var distance = sqrt(dx * dx + dy * dy)
                    if (distance < 0.001f) {
                        dx = 1f
                        dy = 0f
                        distance = 1f
                    }
                    if (distance >= minimumDistance) continue
                    val nx = dx / distance
                    val ny = dy / distance
                    val overlap = (minimumDistance - distance) / 2f
                    first.x -= nx * overlap
                    first.y -= ny * overlap
                    second.x += nx * overlap
                    second.y += ny * overlap
                    val relativeSpeed = (second.velocityX - first.velocityX) * nx +
                        (second.velocityY - first.velocityY) * ny
                    if (relativeSpeed < 0f) {
                        val impulse = -(1f + collisionRestitution) * relativeSpeed / 2f
                        first.velocityX -= impulse * nx
                        first.velocityY -= impulse * ny
                        second.velocityX += impulse * nx
                        second.velocityY += impulse * ny
                    }
                }
            }
        }

        bodies.forEach { body ->
            val dx = body.x - centerX
            val dy = body.y - centerY
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            if (distance > boundaryRadius) {
                body.x = centerX + dx / distance * boundaryRadius
                body.y = centerY + dy / distance * boundaryRadius
            }

            if (body.finalizing) {
                val remaining = 1f - landingProgress(progress)
                limitPlanarSpeed(body, renderedDieSize * 5f * remaining * remaining)
            }
        }
    }

    private fun beginLanding(body: DieBody) {
        body.value = upwardFaceValue(body)
        val target = canonicalOrientation(body.value)
        body.landingStartOrientationX = body.orientationX
        body.landingStartOrientationY = body.orientationY
        body.landingStartOrientationZ = body.orientationZ
        body.targetOrientationX = nearestEquivalentAngle(body.orientationX, target.first)
        body.targetOrientationY = nearestEquivalentAngle(body.orientationY, target.second)

        // Preserve the current Z-axis motion at the start of the ease-out, then let it decay
        // continuously to zero. This avoids the old hard freeze of the third rotation axis.
        val remainingSeconds = ANIMATION_DURATION_SECONDS * (1f - LANDING_START_PROGRESS)
        val zTravel = (body.angularVelocityZ * remainingSeconds / 3f).coerceIn(-270f, 270f)
        body.targetOrientationZ = body.orientationZ + zTravel
        body.finalizing = true
    }

    private fun finishLanding(body: DieBody) {
        if (!body.finalizing) beginLanding(body)
        body.orientationX = body.targetOrientationX
        body.orientationY = body.targetOrientationY
        body.orientationZ = body.targetOrientationZ
        body.height = 0f
        body.verticalVelocity = 0f
        body.settled = true
        body.finalizing = false
    }

    private fun upwardFaceValue(body: DieBody): Int = faces.maxByOrNull { face ->
        rotate(face.normal, body.orientationX, body.orientationY, body.orientationZ).z
    }?.value ?: 1

    private fun canonicalOrientation(value: Int): Pair<Float, Float> = when (value) {
        1 -> 0f to 0f
        6 -> 180f to 0f
        2 -> 90f to 0f
        5 -> -90f to 0f
        3 -> 0f to -90f
        4 -> 0f to 90f
        else -> 0f to 0f
    }

    private fun landingProgress(progress: Float): Float =
        ((progress - LANDING_START_PROGRESS) / (1f - LANDING_START_PROGRESS)).coerceIn(0f, 1f)

    private fun easeOutCubic(progress: Float): Float {
        val remaining = 1f - progress
        return 1f - remaining * remaining * remaining
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    private fun nearestEquivalentAngle(current: Float, target: Float): Float {
        val delta = ((target - current + 540f) % 360f) - 180f
        return current + delta
    }

    private fun limitPlanarSpeed(body: DieBody, maximumSpeed: Float) {
        val speed = sqrt(body.velocityX * body.velocityX + body.velocityY * body.velocityY)
        if (speed <= maximumSpeed || speed < 0.001f) return
        val scale = maximumSpeed / speed
        body.velocityX *= scale
        body.velocityY *= scale
    }

    private fun resultArea(): RectF = resultArea(width, height)

    private fun resultArea(areaWidth: Int, areaHeight: Int): RectF {
        val diameter = min(areaWidth.toFloat(), areaHeight.toFloat()) * 0.68f
        return RectF(
            areaWidth / 2f - diameter / 2f,
            areaHeight / 2f - diameter / 2f,
            areaWidth / 2f + diameter / 2f,
            areaHeight / 2f + diameter / 2f
        )
    }

    private fun fixedDieSize(tray: RectF): Float = min(tray.width(), tray.height()) * 0.11f

    private fun palette(): DicePalette = when (diceTheme) {
        DiceTheme.ANGELS_OF_DEATH -> DicePalette(
            face = Color.rgb(54, 98, 164),
            side = Color.rgb(29, 53, 92),
            edge = Color.rgb(222, 176, 78),
            pip = Color.rgb(246, 235, 205),
            scratch = 0x78B6CAE2,
            accent = Color.rgb(236, 195, 91),
            corrosion = 0x3A101C32
        )
        DiceTheme.PLAGUE_MARINES -> DicePalette(
            face = Color.rgb(119, 139, 78),
            side = Color.rgb(62, 75, 42),
            edge = Color.rgb(187, 133, 55),
            pip = Color.rgb(238, 226, 181),
            scratch = 0x8C392F1E.toInt(),
            accent = Color.rgb(207, 164, 68),
            corrosion = 0x74413720
        )
        DiceTheme.ORK_KOMMANDOS -> DicePalette(
            face = Color.rgb(123, 137, 61),
            side = Color.rgb(58, 68, 31),
            edge = Color.rgb(231, 181, 42),
            pip = Color.rgb(245, 228, 177),
            scratch = 0xA62A2B19.toInt(),
            accent = Color.rgb(242, 194, 55),
            corrosion = 0x8A24261A.toInt()
        )
        DiceTheme.CORSAIR_VOIDSCARRED -> DicePalette(
            face = Color.rgb(42, 158, 160),
            side = Color.rgb(22, 76, 96),
            edge = Color.rgb(205, 226, 231),
            pip = Color.rgb(252, 241, 213),
            scratch = 0x8CC5F5F0.toInt(),
            accent = Color.rgb(128, 239, 224),
            corrosion = 0x64304D67
        )
        DiceTheme.DEATH_KORPS -> DicePalette(
            face = Color.rgb(103, 132, 158),
            side = Color.rgb(48, 62, 80),
            edge = Color.rgb(204, 157, 77),
            pip = Color.rgb(241, 232, 203),
            scratch = 0x8CB9CBD8.toInt(),
            accent = Color.rgb(217, 174, 91),
            corrosion = 0x70414A52
        )
    }

    private fun applyThemePalette() {
        val palette = palette()
        dicePaint.color = palette.face
        sidePaint.color = palette.side
        edgePaint.color = palette.edge
        pipPaint.color = palette.pip
        scratchPaint.color = palette.scratch
        accentPaint.color = palette.accent
    }

    private fun shade(color: Int, light: Float): Int = Color.rgb(
        (Color.red(color) * light).toInt().coerceIn(0, 255),
        (Color.green(color) * light).toInt().coerceIn(0, 255),
        (Color.blue(color) * light).toInt().coerceIn(0, 255)
    )

    private fun signedSpin(random: Random, minimum: Float, maximum: Float): Float {
        val magnitude = random.nextFloat() * (maximum - minimum) + minimum
        return if (random.nextBoolean()) magnitude else -magnitude
    }

    private fun drawTumblingDie(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
        body: DieBody
    ) {
        val visibleFaces = faces.mapNotNull { face ->
            val normal = rotate(face.normal, body.orientationX, body.orientationY, body.orientationZ)
            if (normal.z <= 0.02f) return@mapNotNull null
            val localCorners = listOf(
                face.center + face.u * -1f + face.v * -1f,
                face.center + face.u + face.v * -1f,
                face.center + face.u + face.v,
                face.center + face.u * -1f + face.v
            )
            val corners = localCorners.map {
                rotate(it, body.orientationX, body.orientationY, body.orientationZ)
            }
            VisibleFace(
                face, corners, normal,
                corners.sumOf { it.z.toDouble() }.toFloat() / corners.size
            )
        }.sortedBy { it.depth }

        visibleFaces.forEach { visible ->
            val path = Path()
            visible.corners.forEachIndexed { index, point ->
                val x = centerX + point.x * size / 2f
                val y = centerY + point.y * size / 2f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawMappedFaceSkin(canvas, path, centerX, centerY, size, visible)

            val light = 0.70f + visible.normal.z.coerceIn(0f, 1f) * 0.30f
            tumblingFacePaint.color = Color.argb(
                ((1f - light) * 110f).toInt().coerceIn(0, 255), 0, 0, 0
            )
            canvas.drawPath(path, tumblingFacePaint)
            canvas.drawPath(path, edgePaint)
            if (body.selected) canvas.drawPath(path, selectedPaint)
        }
    }

    private fun drawMappedFaceSkin(
        canvas: Canvas,
        facePath: Path,
        centerX: Float,
        centerY: Float,
        size: Float,
        visible: VisibleFace
    ) {
        val half = size / 2f
        val source = floatArrayOf(
            centerX - half, centerY - half,
            centerX + half, centerY - half,
            centerX + half, centerY + half,
            centerX - half, centerY + half
        )
        val destination = FloatArray(8)
        visible.corners.forEachIndexed { index, point ->
            destination[index * 2] = centerX + point.x * half
            destination[index * 2 + 1] = centerY + point.y * half
        }
        val faceTransform = Matrix()
        if (!faceTransform.setPolyToPoly(source, 0, destination, 0, 4)) return

        canvas.save()
        canvas.clipPath(facePath)
        canvas.concat(faceTransform)
        drawSettledDie(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            size = size,
            value = visible.face.value,
            rotation = 0f,
            selected = false
        )
        canvas.restore()
    }

    private fun drawFacePips(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
        visible: VisibleFace,
        body: DieBody
    ) {
        pipPositions(visible.face.value).forEach { (pipX, pipY) ->
            val local = visible.face.center + visible.face.u * pipX + visible.face.v * pipY
            val point = rotate(local, body.orientationX, body.orientationY, body.orientationZ)
            val radius = size * 0.064f * (0.62f + visible.normal.z * 0.38f)
            canvas.drawCircle(
                centerX + point.x * size / 2f,
                centerY + point.y * size / 2f,
                radius,
                pipPaint
            )
        }
    }

    private fun pipPositions(value: Int): List<Pair<Float, Float>> {
        val o = 0.48f
        return when (value.coerceIn(1, 6)) {
            1 -> listOf(0f to 0f)
            2 -> listOf(-o to -o, o to o)
            3 -> listOf(-o to -o, 0f to 0f, o to o)
            4 -> listOf(-o to -o, o to -o, -o to o, o to o)
            5 -> listOf(-o to -o, o to -o, 0f to 0f, -o to o, o to o)
            else -> listOf(-o to -o, o to -o, -o to 0f, o to 0f, -o to o, o to o)
        }
    }

    private fun rotate(point: Vec3, xDegrees: Float, yDegrees: Float, zDegrees: Float): Vec3 {
        val xRadians = Math.toRadians(xDegrees.toDouble())
        val yRadians = Math.toRadians(yDegrees.toDouble())
        val zRadians = Math.toRadians(zDegrees.toDouble())
        val cosX = cos(xRadians).toFloat()
        val sinX = sin(xRadians).toFloat()
        val cosY = cos(yRadians).toFloat()
        val sinY = sin(yRadians).toFloat()
        val cosZ = cos(zRadians).toFloat()
        val sinZ = sin(zRadians).toFloat()

        val xAfterX = point.x
        val yAfterX = point.y * cosX - point.z * sinX
        val zAfterX = point.y * sinX + point.z * cosX
        val xAfterY = xAfterX * cosY + zAfterX * sinY
        val yAfterY = yAfterX
        val zAfterY = -xAfterX * sinY + zAfterX * cosY
        return Vec3(
            xAfterY * cosZ - yAfterY * sinZ,
            xAfterY * sinZ + yAfterY * cosZ,
            zAfterY
        )
    }

    private fun drawSettledDie(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
        value: Int,
        rotation: Float,
        selected: Boolean
    ) {
        canvas.save()
        canvas.rotate(rotation, centerX, centerY)
        val half = size / 2f
        val rect = RectF(centerX - half, centerY - half, centerX + half, centerY + half)
        val radius = size * 0.16f
        val sideRect = RectF(rect).apply { offset(0f, size * 0.08f) }
        canvas.drawRoundRect(sideRect, radius, radius, sidePaint)
        canvas.drawRoundRect(rect, radius, radius, dicePaint)
        canvas.drawRoundRect(rect, radius, radius, edgePaint)
        if (selected) canvas.drawRoundRect(rect, radius, radius, selectedPaint)
        drawThemeSurfaceDetails(canvas, rect, size)
        canvas.drawLine(
            rect.left + size * 0.18f, rect.top + size * 0.28f,
            rect.left + size * 0.38f, rect.top + size * 0.2f, scratchPaint
        )
        canvas.drawLine(
            rect.right - size * 0.3f, rect.bottom - size * 0.18f,
            rect.right - size * 0.12f, rect.bottom - size * 0.3f, scratchPaint
        )
        if (value.coerceIn(1, 6) == 6) {
            drawThemeSymbol(canvas, centerX, centerY, size)
        } else {
        val offset = size * 0.23f
        val pipRadius = size * 0.075f
        fun pip(x: Float, y: Float) = canvas.drawCircle(
            centerX + x, centerY + y, pipRadius, pipPaint
        )
        when (value.coerceIn(1, 6)) {
            1 -> pip(0f, 0f)
            2 -> { pip(-offset, -offset); pip(offset, offset) }
            3 -> { pip(-offset, -offset); pip(0f, 0f); pip(offset, offset) }
            4 -> {
                pip(-offset, -offset); pip(offset, -offset)
                pip(-offset, offset); pip(offset, offset)
            }
            5 -> {
                pip(-offset, -offset); pip(offset, -offset); pip(0f, 0f)
                pip(-offset, offset); pip(offset, offset)
            }
            6 -> {
                pip(-offset, -offset); pip(offset, -offset)
                pip(-offset, 0f); pip(offset, 0f)
                pip(-offset, offset); pip(offset, offset)
            }
        }
        }
        canvas.restore()
    }

    private fun drawThemeSurfaceDetails(canvas: Canvas, rect: RectF, size: Float) {
        val palette = palette()
        when (diceTheme) {
            DiceTheme.ANGELS_OF_DEATH -> {
                val inset = size * 0.075f
                val inner = RectF(rect).apply { inset(inset, inset) }
                accentPaint.strokeWidth = size * 0.018f
                accentPaint.alpha = 190
                canvas.drawRoundRect(inner, size * 0.11f, size * 0.11f, accentPaint)

                detailPaint.style = Paint.Style.FILL
                detailPaint.color = palette.accent
                detailPaint.alpha = 210
                val rivet = size * 0.026f
                val corner = size * 0.135f
                canvas.drawCircle(rect.left + corner, rect.top + corner, rivet, detailPaint)
                canvas.drawCircle(rect.right - corner, rect.top + corner, rivet, detailPaint)
                canvas.drawCircle(rect.left + corner, rect.bottom - corner, rivet, detailPaint)
                canvas.drawCircle(rect.right - corner, rect.bottom - corner, rivet, detailPaint)
            }
            DiceTheme.PLAGUE_MARINES -> {
                detailPaint.style = Paint.Style.FILL
                detailPaint.color = palette.corrosion
                detailPaint.alpha = 205
                canvas.drawCircle(
                    rect.left + size * 0.18f,
                    rect.top + size * 0.2f,
                    size * 0.055f,
                    detailPaint
                )
                canvas.drawCircle(
                    rect.right - size * 0.22f,
                    rect.top + size * 0.32f,
                    size * 0.035f,
                    detailPaint
                )
                canvas.drawCircle(
                    rect.left + size * 0.28f,
                    rect.bottom - size * 0.16f,
                    size * 0.032f,
                    detailPaint
                )
                canvas.drawCircle(
                    rect.right - size * 0.14f,
                    rect.bottom - size * 0.2f,
                    size * 0.047f,
                    detailPaint
                )
            }
            DiceTheme.ORK_KOMMANDOS -> {
                detailPaint.style = Paint.Style.STROKE
                detailPaint.strokeWidth = size * 0.026f
                detailPaint.color = palette.corrosion
                detailPaint.alpha = 220
                repeat(4) { index ->
                    val startX = rect.left + size * (0.08f + index * 0.095f)
                    canvas.drawLine(
                        startX,
                        rect.top + size * 0.08f,
                        startX + size * 0.10f,
                        rect.top + size * 0.19f,
                        detailPaint
                    )
                }
                detailPaint.style = Paint.Style.FILL
                val rivet = size * 0.027f
                canvas.drawCircle(rect.left + size * 0.13f, rect.bottom - size * 0.13f, rivet, detailPaint)
                canvas.drawCircle(rect.right - size * 0.13f, rect.top + size * 0.13f, rivet, detailPaint)
            }
            DiceTheme.CORSAIR_VOIDSCARRED -> {
                detailPaint.style = Paint.Style.STROKE
                detailPaint.strokeWidth = size * 0.018f
                detailPaint.color = palette.accent
                detailPaint.alpha = 210
                val inset = size * 0.09f
                val runeFrame = Path().apply {
                    moveTo(rect.centerX(), rect.top + inset)
                    lineTo(rect.right - inset, rect.centerY())
                    lineTo(rect.centerX(), rect.bottom - inset)
                    lineTo(rect.left + inset, rect.centerY())
                    close()
                }
                canvas.drawPath(runeFrame, detailPaint)
                detailPaint.style = Paint.Style.FILL
                canvas.drawCircle(
                    rect.right - size * 0.15f,
                    rect.bottom - size * 0.15f,
                    size * 0.028f,
                    detailPaint
                )
            }
            DiceTheme.DEATH_KORPS -> {
                detailPaint.style = Paint.Style.STROKE
                detailPaint.strokeWidth = size * 0.022f
                detailPaint.color = palette.accent
                detailPaint.alpha = 190
                val inset = size * 0.09f
                canvas.drawRect(RectF(rect).apply { inset(inset, inset) }, detailPaint)
                detailPaint.style = Paint.Style.FILL
                detailPaint.color = palette.corrosion
                detailPaint.alpha = 150
                canvas.drawCircle(
                    rect.left + size * 0.18f,
                    rect.top + size * 0.22f,
                    size * 0.040f,
                    detailPaint
                )
                canvas.drawCircle(
                    rect.right - size * 0.20f,
                    rect.bottom - size * 0.17f,
                    size * 0.030f,
                    detailPaint
                )
            }
        }
        accentPaint.alpha = 255
        detailPaint.alpha = 255
    }

    private fun drawThemeSymbol(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        detailPaint.color = palette().pip
        detailPaint.alpha = 255
        when (diceTheme) {
            DiceTheme.ANGELS_OF_DEATH -> drawWingedBladeSymbol(
                canvas,
                centerX,
                centerY,
                size
            )
            DiceTheme.PLAGUE_MARINES -> drawPlagueTriadSymbol(
                canvas,
                centerX,
                centerY,
                size
            )
            DiceTheme.ORK_KOMMANDOS -> drawOrkSkullSymbol(
                canvas,
                centerX,
                centerY,
                size
            )
            DiceTheme.CORSAIR_VOIDSCARRED -> drawCorsairRuneSymbol(
                canvas,
                centerX,
                centerY,
                size
            )
            DiceTheme.DEATH_KORPS -> drawGasMaskSymbol(
                canvas,
                centerX,
                centerY,
                size
            )
        }
    }

    private fun drawWingedBladeSymbol(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        detailPaint.style = Paint.Style.FILL
        val blade = Path().apply {
            moveTo(centerX, centerY - size * 0.34f)
            lineTo(centerX + size * 0.065f, centerY + size * 0.17f)
            lineTo(centerX, centerY + size * 0.29f)
            lineTo(centerX - size * 0.065f, centerY + size * 0.17f)
            close()
        }
        canvas.drawPath(blade, detailPaint)
        canvas.drawRect(
            centerX - size * 0.17f,
            centerY + size * 0.08f,
            centerX + size * 0.17f,
            centerY + size * 0.13f,
            detailPaint
        )
        val leftWing = Path().apply {
            moveTo(centerX - size * 0.08f, centerY - size * 0.08f)
            lineTo(centerX - size * 0.36f, centerY - size * 0.22f)
            lineTo(centerX - size * 0.27f, centerY - size * 0.04f)
            lineTo(centerX - size * 0.35f, centerY + size * 0.02f)
            lineTo(centerX - size * 0.08f, centerY + size * 0.04f)
            close()
        }
        val rightWing = Path().apply {
            moveTo(centerX + size * 0.08f, centerY - size * 0.08f)
            lineTo(centerX + size * 0.36f, centerY - size * 0.22f)
            lineTo(centerX + size * 0.27f, centerY - size * 0.04f)
            lineTo(centerX + size * 0.35f, centerY + size * 0.02f)
            lineTo(centerX + size * 0.08f, centerY + size * 0.04f)
            close()
        }
        canvas.drawPath(leftWing, detailPaint)
        canvas.drawPath(rightWing, detailPaint)
        canvas.drawCircle(centerX, centerY + size * 0.31f, size * 0.047f, detailPaint)
    }

    private fun drawPlagueTriadSymbol(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        detailPaint.style = Paint.Style.STROKE
        detailPaint.strokeWidth = size * 0.055f
        val radius = size * 0.125f
        canvas.drawCircle(centerX, centerY - size * 0.16f, radius, detailPaint)
        canvas.drawCircle(centerX - size * 0.15f, centerY + size * 0.11f, radius, detailPaint)
        canvas.drawCircle(centerX + size * 0.15f, centerY + size * 0.11f, radius, detailPaint)
        detailPaint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY + size * 0.035f, size * 0.055f, detailPaint)
    }

    private fun drawOrkSkullSymbol(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        detailPaint.style = Paint.Style.STROKE
        detailPaint.strokeWidth = size * 0.055f
        val skull = RectF(
            centerX - size * 0.22f,
            centerY - size * 0.27f,
            centerX + size * 0.22f,
            centerY + size * 0.13f
        )
        canvas.drawOval(skull, detailPaint)
        canvas.drawCircle(centerX - size * 0.085f, centerY - size * 0.08f, size * 0.045f, detailPaint)
        canvas.drawCircle(centerX + size * 0.085f, centerY - size * 0.08f, size * 0.045f, detailPaint)
        canvas.drawLine(
            centerX - size * 0.12f,
            centerY + size * 0.12f,
            centerX - size * 0.21f,
            centerY + size * 0.30f,
            detailPaint
        )
        canvas.drawLine(
            centerX + size * 0.12f,
            centerY + size * 0.12f,
            centerX + size * 0.21f,
            centerY + size * 0.30f,
            detailPaint
        )
        canvas.drawLine(
            centerX - size * 0.12f,
            centerY + size * 0.16f,
            centerX + size * 0.12f,
            centerY + size * 0.16f,
            detailPaint
        )
    }

    private fun drawCorsairRuneSymbol(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        detailPaint.style = Paint.Style.STROKE
        detailPaint.strokeWidth = size * 0.052f
        canvas.drawArc(
            RectF(
                centerX - size * 0.25f,
                centerY - size * 0.28f,
                centerX + size * 0.18f,
                centerY + size * 0.27f
            ),
            -70f,
            245f,
            false,
            detailPaint
        )
        detailPaint.style = Paint.Style.FILL
        val star = Path().apply {
            moveTo(centerX + size * 0.20f, centerY - size * 0.28f)
            lineTo(centerX + size * 0.24f, centerY - size * 0.10f)
            lineTo(centerX + size * 0.37f, centerY - size * 0.05f)
            lineTo(centerX + size * 0.24f, centerY)
            lineTo(centerX + size * 0.20f, centerY + size * 0.18f)
            lineTo(centerX + size * 0.16f, centerY)
            lineTo(centerX + size * 0.03f, centerY - size * 0.05f)
            lineTo(centerX + size * 0.16f, centerY - size * 0.10f)
            close()
        }
        canvas.drawPath(star, detailPaint)
    }

    private fun drawGasMaskSymbol(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        detailPaint.style = Paint.Style.STROKE
        detailPaint.strokeWidth = size * 0.050f
        val mask = Path().apply {
            moveTo(centerX, centerY - size * 0.31f)
            lineTo(centerX + size * 0.24f, centerY - size * 0.16f)
            lineTo(centerX + size * 0.20f, centerY + size * 0.10f)
            lineTo(centerX + size * 0.10f, centerY + size * 0.20f)
            lineTo(centerX - size * 0.10f, centerY + size * 0.20f)
            lineTo(centerX - size * 0.20f, centerY + size * 0.10f)
            lineTo(centerX - size * 0.24f, centerY - size * 0.16f)
            close()
        }
        canvas.drawPath(mask, detailPaint)
        canvas.drawCircle(centerX - size * 0.10f, centerY - size * 0.10f, size * 0.060f, detailPaint)
        canvas.drawCircle(centerX + size * 0.10f, centerY - size * 0.10f, size * 0.060f, detailPaint)
        canvas.drawRect(
            centerX - size * 0.075f,
            centerY + size * 0.07f,
            centerX + size * 0.075f,
            centerY + size * 0.24f,
            detailPaint
        )
        canvas.drawLine(
            centerX - size * 0.17f,
            centerY + size * 0.05f,
            centerX - size * 0.28f,
            centerY + size * 0.29f,
            detailPaint
        )
        canvas.drawLine(
            centerX + size * 0.17f,
            centerY + size * 0.05f,
            centerX + size * 0.28f,
            centerY + size * 0.29f,
            detailPaint
        )
    }
}
