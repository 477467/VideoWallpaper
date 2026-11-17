package com.example.videowallpaper

import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val prefs = VideoStore.prefs(applicationContext)
        private var player: ExoPlayer? = null
        private var holderRef: SurfaceHolder? = null
        private var shown = false
        private var errorCount = 0

        // Запам'ятовуємо місце відтворення, щоб після повернення на екран
        // відео продовжувалось, а не починалось з нуля.
        private var resumeIndex = 0
        private var resumePos = 0L

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            holderRef = holder
            ensurePlayer()
            updatePlayback()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            player?.clearVideoSurfaceHolder(holder)
            releasePlayer()
            holderRef = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            shown = visible
            updatePlayback()
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            releasePlayer()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            when (key) {
                VideoStore.KEY_MUTED -> player?.let { applyMute(it) }
                VideoStore.KEY_VERSION -> {
                    // Список відео змінився: збираємо плеєр заново з початку.
                    resumeIndex = 0
                    resumePos = 0L
                    releasePlayer()
                    if (holderRef != null) {
                        ensurePlayer()
                        updatePlayback()
                    }
                }
            }
        }

        private fun ensurePlayer() {
            if (player != null) return
            val files = VideoStore.list(applicationContext)
            if (files.isEmpty()) return

            val p = ExoPlayer.Builder(applicationContext).build()
            // false означає, що шпалери не перехоплюють аудіофокус і не зупиняють чужу музику.
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false
            )
            // Fit the complete video on the screen. Letterboxing is preferable to
            // cutting off people or other important parts of the frame.
            p.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            p.repeatMode = if (files.size == 1) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
            applyMute(p)

            errorCount = 0
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Битий файл пропускаємо, але не зациклюємось, якщо зламані всі.
                    errorCount++
                    if (errorCount <= p.mediaItemCount) {
                        p.seekToNextMediaItem()
                        p.prepare()
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) errorCount = 0
                }
            })

            val items = files.map { MediaItem.fromUri(android.net.Uri.fromFile(it)) }
            p.setMediaItems(items, resumeIndex.coerceIn(0, items.size - 1), resumePos)
            holderRef?.let { p.setVideoSurfaceHolder(it) }
            p.playWhenReady = false
            p.prepare()
            player = p
        }

        private fun releasePlayer() {
            player?.let {
                resumeIndex = it.currentMediaItemIndex
                resumePos = it.currentPosition
                it.release()
            }
            player = null
        }

        private fun updatePlayback() {
            // Поки екран вимкнено або шпалери не видно, відео стоїть на паузі:
            // це економить батарею і прибирає навантаження.
            player?.playWhenReady = shown && holderRef != null
        }

        private fun applyMute(p: ExoPlayer) {
            val muted = VideoStore.isMuted(applicationContext)
            // Звукову доріжку вимикаємо повністю, а не просто ставимо гучність 0.
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, muted)
                .build()
            p.volume = if (muted) 0f else 1f
        }
    }
}
