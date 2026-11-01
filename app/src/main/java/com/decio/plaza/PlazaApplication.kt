package com.decio.plaza

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import org.schabi.newpipe.extractor.NewPipe

class PlazaApplication : Application(), ImageLoaderFactory {

    /** The Shorts feed, players and bank: one per process, outliving any window. */
    val shorts by lazy { ShortsState(this, kotlinx.coroutines.MainScope()) }

    override fun onCreate() {
        super.onCreate()
        installCrashReporter()
        NewPipe.init(NewPipeDownloader.instance)
        // The iOS client only adds HLS livestream formats; Plaza plays VOD, so skip that request.
        org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setFetchIosClient(false)
        ThemeSettings.init(this)
        LocaleSettings.init(this)
        AppLanguage.init(this)
        QualityPreference.init(this)
        PlaybackSpeed.init(this)
        AudioOnly.init(this)
        SearchHistory.init(this)
        ResumeStore.init(this)
        SavedSession.init(this)
        Account.init(this)
        SponsorBlock.init(this)
        ShortsSetting.init(this)
        ShortsBank.init(this)
        AppUpdate.init(this)
        // Stream URLs are bound to the network they were fetched on: forget them on a switch.
        getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                private var last: android.net.Network? = null
                override fun onAvailable(network: android.net.Network) {
                    if (last != null && last != network) StreamCache.invalidateAll()
                    last = network
                }
            },
        )
    }

    /**
     * Saves the stack trace of any crash to a file and shows it in a dialog the next time the
     * app opens, so the cause can be read (and copied) without a computer or a logcat app.
     */
    private fun installCrashReporter() {
        val file = java.io.File(filesDir, "crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = java.io.StringWriter()
                error.printStackTrace(java.io.PrintWriter(trace))
                file.writeText(
                    "Thread: ${thread.name}\n" +
                        "HP: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n\n" +
                        trace.toString(),
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (!file.exists()) return
                val text = try { file.readText() } catch (_: Throwable) { return }
                file.delete()
                val shown = text.take(4000)
                AlertDialog.Builder(activity)
                    .setTitle("Plaza crash")
                    .setMessage(shown)
                    .setPositiveButton("Salin") { _, _ ->
                        val clipboard = activity.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Plaza crash", text))
                    }
                    .setNegativeButton("Tutup", null)
                    .show()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    // Thumbnails are in every list and the player; a short crossfade replaces pop-in.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(180)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .build()
}
