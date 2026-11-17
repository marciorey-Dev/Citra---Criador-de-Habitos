package com.example

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import java.io.IOException

/**
 * Application entry point configuring Citra's offline-safe Coil ImageLoader so
 * any remote http/https URL never triggers an uncaught SecurityException on OkHttp
 * background dispatcher threads when Wi-Fi or mobile data is active without INTERNET permission.
 */
class CitraApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val hasInternetPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED

        val builder = ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .components {
                add(OfflineNetworkBlockingInterceptor(hasInternetPermission))
            }

        if (!hasInternetPermission) {
            builder.callFactory(OfflineBlockingCallFactory)
        }

        return builder.build()
    }
}

internal class OfflineNetworkBlockingInterceptor(
    private val allowNetwork: Boolean
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        if (!allowNetwork && isRemoteImageData(chain.request.data)) {
            return ErrorResult(
                drawable = null,
                request = chain.request,
                throwable = IOException("Modo offline ativo: imagens remotas desativadas.")
            )
        }
        return chain.proceed(chain.request)
    }

    companion object {
        fun isRemoteImageData(data: Any?): Boolean {
            val raw = when (data) {
                is String -> data
                is android.net.Uri -> data.toString()
                else -> data?.toString() ?: return false
            }.trim()
            return raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true)
        }
    }
}

internal object OfflineBlockingCallFactory : Call.Factory {
    override fun newCall(request: Request): Call = OfflineBlockingCall(request)

    private class OfflineBlockingCall(
        private val req: Request
    ) : Call {
        @Volatile
        private var executed = false

        @Volatile
        private var canceled = false

        override fun request(): Request = req

        override fun execute(): Response {
            executed = true
            throw IOException("Modo offline: acesso à rede desativado.")
        }

        override fun enqueue(responseCallback: Callback) {
            executed = true
            responseCallback.onFailure(this, IOException("Modo offline: acesso à rede desativado."))
        }

        override fun cancel() {
            canceled = true
        }

        override fun isExecuted(): Boolean = executed

        override fun isCanceled(): Boolean = canceled

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = OfflineBlockingCall(req)
    }
}
