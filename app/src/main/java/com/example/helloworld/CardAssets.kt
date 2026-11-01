package com.example.helloworld

import android.content.Context
import java.io.InputStream

/**
 * Card images are shipped as WebP to keep the APK small, while rule data and code keep
 * referring to the original `.png` names. Lookups go through here so either file works.
 */
object CardAssets {
    @Volatile private var listing: Map<String, Set<String>> = emptyMap()

    private fun dir(context: Context, path: String): Set<String> =
        listing[path] ?: context.assets.list(path).orEmpty().toSet().also { listing = listing + (path to it) }

    /** File names in [path] with WebP files reported under their original `.png` name. */
    fun list(context: Context, path: String): List<String> =
        dir(context, path).map { if (it.endsWith(".webp")) it.removeSuffix(".webp") + ".png" else it }.distinct().sorted()

    fun resolve(context: Context, asset: String): String {
        if (!asset.endsWith(".png")) return asset
        val folder = asset.substringBeforeLast('/', "")
        val name = asset.substringAfterLast('/')
        val files = dir(context, folder)
        if (name in files) return asset
        val webp = name.removeSuffix(".png") + ".webp"
        return if (webp in files) (if (folder.isEmpty()) webp else "$folder/$webp") else asset
    }

    fun open(context: Context, asset: String): InputStream = context.assets.open(resolve(context, asset))
}
