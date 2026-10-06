@file:OptIn(UnstableApi::class)

package com.crsmthw.sheliak.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.crsmthw.sheliak.data.provider.ProviderRegistry
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.IOException

/**
 * The session's artwork loader (notification, lock screen, controllers): the app's one Coil [ImageLoader], fed
 * the provider's own art model — which carries whatever auth the provider needs — instead of Media3's default
 * loader, which fetches `artworkUri` with no headers. Software bitmaps only (`allowHardware(false)`): the
 * notification and the platform session copy pixels.
 */
class CoilBitmapLoader(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val providers: ProviderRegistry,
    private val scope: CoroutineScope,
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = Util.isBitmapFactorySupportedMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = scope.future(Dispatchers.Default) {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("Undecodable artwork")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = load(uri.toString())

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val model = MediaItemFactory.artRefOf(metadata.extras)?.let { ref ->
            providers.current(ref.providerId)?.artModel(ref, MediaItemFactory.ART_SIZE_PX)
        }
        val data = metadata.artworkData
        val uri = metadata.artworkUri
        return when {
            model != null -> load(model)
            data != null  -> decodeBitmap(data)
            uri != null   -> loadBitmap(uri)
            else          -> null
        }
    }

    private fun load(model: Any): ListenableFuture<Bitmap> = scope.future {
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(MediaItemFactory.ART_SIZE_PX)
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request)
        (result as? SuccessResult)?.image?.toBitmap() ?: throw IOException("Artwork failed to load")
    }
}
