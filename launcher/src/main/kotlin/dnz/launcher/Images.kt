package dnz.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Downloads and caches images from the web (mod icons, gallery). */
object Images {
    private const val MAX_CACHED = 300
    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > MAX_CACHED
    }
    private val failed = HashSet<String>()
    private val limit = Semaphore(6)

    fun cached(url: String?): ImageBitmap? = url?.let { synchronized(cache) { cache[it] } }

    /** The image, or null if it can't be loaded (no internet, or a format like SVG that can't be shown). */
    suspend fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it }
        if (synchronized(failed) { url in failed }) return null
        return withContext(Dispatchers.IO) {
            limit.withPermit {
                runCatching { org.jetbrains.skia.Image.makeFromEncoded(Net.bytes(url)).toComposeImageBitmap() }.getOrNull()
            }
        }.also { image ->
            if (image != null) synchronized(cache) { cache[url] = image } else synchronized(failed) { failed += url }
        }
    }
}

/** An image from [url]; shows [fallback] while loading or when there is no image. */
@Composable
fun RemoteImage(url: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, fallback: @Composable () -> Unit = {}) {
    val image by produceState(Images.cached(url), url) {
        if (value == null && url != null) value = Images.load(url)
    }
    val bitmap = image
    if (bitmap != null) {
        Image(bitmap, null, modifier, contentScale = contentScale)
    } else {
        Box(modifier) { fallback() }
    }
}
