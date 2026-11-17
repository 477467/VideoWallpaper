package com.example.videowallpaper

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Сховище відео та налаштувань.
 * Відео копіюються у внутрішню пам'ять застосунку, тому шпалери не ламаються,
 * якщо оригінальний файл видалили або перемістили.
 */
object VideoStore {
    private const val PREFS = "wallpaper_prefs"
    const val KEY_MUTED = "muted"
    const val KEY_VERSION = "version"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "videos").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && !f.name.endsWith(".tmp") }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun isMuted(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_MUTED, true)

    fun setMuted(ctx: Context, muted: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_MUTED, muted).apply()
    }

    /** Повідомляє службу шпалер, що список відео змінився. */
    fun bump(ctx: Context) {
        val p = prefs(ctx)
        p.edit().putInt(KEY_VERSION, p.getInt(KEY_VERSION, 0) + 1).apply()
    }

    fun displayName(file: File): String = file.name.substringAfter('_', file.name)

    fun importUri(ctx: Context, uri: Uri): File? {
        val app = ctx.applicationContext
        val target = File(dir(app), "${System.currentTimeMillis()}_${queryName(app, uri)}")
        val tmp = File(target.parentFile, target.name + ".tmp")
        return try {
            val input = app.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> tmp.outputStream().use { out -> src.copyTo(out, 256 * 1024) } }
            if (tmp.renameTo(target)) target else {
                tmp.delete()
                null
            }
        } catch (e: Exception) {
            tmp.delete()
            null
        }
    }

    private fun queryName(ctx: Context, uri: Uri): String {
        var name: String? = null
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
        val raw = name ?: uri.lastPathSegment ?: "video.mp4"
        return raw.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").takeLast(60)
    }
}
