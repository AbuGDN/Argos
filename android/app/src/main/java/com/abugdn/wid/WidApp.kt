package com.abugdn.wid

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.abugdn.wid.data.WIKI_USER_AGENT
import okhttp3.OkHttpClient
import com.abugdn.wid.data.Repository
import com.abugdn.wid.sync.DigestWorker
import com.abugdn.wid.sync.Notifier
import com.abugdn.wid.sync.SyncWorker

class WidApp : Application(), ImageLoaderFactory {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        Notifier.createChannels(this)
        SyncWorker.schedule(this)
        DigestWorker.schedule(this)
    }

    /** Imagens (Coil): a Wikimedia recusa pedidos sem um user-agent que identifique o app. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    if (host.endsWith("wikimedia.org") || host.endsWith("wikipedia.org")) {
                        chain.proceed(request.newBuilder().header("User-Agent", WIKI_USER_AGENT).build())
                    } else {
                        chain.proceed(request)
                    }
                }
                .build()
        }
        .build()
}

val android.content.Context.repository: Repository
    get() = (applicationContext as WidApp).repository
