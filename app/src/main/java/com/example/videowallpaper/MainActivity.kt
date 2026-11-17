package com.example.videowallpaper

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videowallpaper.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = VideoAdapter { file -> confirmDelete(file) }
    private val executor = Executors.newSingleThreadExecutor()

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) importVideos(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.btnAdd.setOnClickListener { picker.launch(arrayOf("video/*")) }
        binding.btnMute.setOnClickListener {
            VideoStore.setMuted(this, !VideoStore.isMuted(this))
            refreshMuteButton()
        }
        binding.btnSet.setOnClickListener { openWallpaperPicker() }

        refresh()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun refresh() {
        val files = VideoStore.list(this)
        adapter.submit(files)
        binding.empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        refreshMuteButton()
    }

    private fun refreshMuteButton() {
        binding.btnMute.setText(
            if (VideoStore.isMuted(this)) R.string.sound_off else R.string.sound_on
        )
    }

    private fun importVideos(uris: List<Uri>) {
        binding.progress.visibility = View.VISIBLE
        binding.btnAdd.isEnabled = false
        val app = applicationContext
        executor.execute {
            var ok = 0
            for (uri in uris) {
                if (VideoStore.importUri(app, uri) != null) ok++
            }
            if (ok > 0) VideoStore.bump(app)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                binding.progress.visibility = View.GONE
                binding.btnAdd.isEnabled = true
                refresh()
                val msg = if (ok > 0) getString(R.string.added_fmt, ok) else getString(R.string.import_failed)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(VideoStore.displayName(file))
            .setPositiveButton(R.string.delete) { _, _ ->
                file.delete()
                VideoStore.bump(this)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openWallpaperPicker() {
        if (VideoStore.list(this).isEmpty()) {
            Toast.makeText(this, R.string.add_first, Toast.LENGTH_SHORT).show()
            return
        }
        val component = ComponentName(this, VideoWallpaperService::class.java)
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_picker, Toast.LENGTH_LONG).show()
            }
        }
    }
}
