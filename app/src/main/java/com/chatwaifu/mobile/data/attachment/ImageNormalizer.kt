package com.chatwaifu.mobile.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.ExifOrientationStrategy
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Precision
import coil3.size.Scale
import com.chatwaifu.log.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Description: 图像归一化。解码交给 Coil，编码交给 [BitmapEncoder]。
 *
 * ## 为什么用 Coil 而不是继续手写 BitmapFactory
 *
 * 老实现是手写的两段式解码（先 `inJustDecodeBounds` 算 `inSampleSize`，再真解码）
 * 加八分支的 EXIF 方向矩阵。这段代码本身没错，但它只覆盖了 `BitmapFactory` 能吃的东西，
 * 而附件的来源是相册和分享面板，实际会拿到：
 *
 * - **HEIC / HEIF**：iPhone 和很多国产相机的默认格式
 * - **AVIF**：解码器要 API 31+，28~30 上必须能干净地失败而不是解出一张空图
 * - **动图 GIF / WebP**：只该取第一帧，三家都不收动画
 * - **各种畸形文件**：EXIF 段损坏、声明的尺寸和实际不符
 *
 * Coil 把这些都收在 `Decoder` 链后面，并且它的 `Fetcher` 直接吃 `content://`、
 * 内部按 okio 的 `BufferedSource` 流式读——**不会把整个文件读成 `ByteArray`**，
 * 这正是老实现最大的洞。
 *
 * 另一个理由是它本来就要进来：附件缩略图那步 UI 需要一个图片加载库
 * （加 `coil-compose` 即可），与其为入库单独维护一套解码代码，不如两边共用同一条链。
 *
 * ## EXIF 方向
 *
 * 相机拍的 JPEG 靠 EXIF 记方向，像素本身没转；重编码会丢掉 EXIF，
 * 所以必须在解码时把旋转烧进像素，否则喂给视觉模型的是一张躺着的图。
 * 这件事现在由 [ExifOrientationStrategy.RESPECT_ALL] 负责——
 * 默认的 `RESPECT_PERFORMANCE` 会为了省一次读跳过部分格式的 EXIF 解析，
 * 而 HEIC 恰好是既常见又带方向的那一类，所以这里显式要求全都处理。
 * **[defaultLoader] 里那一行是这件事的唯一开关**，删了就会静默回归成躺着的图。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
class ImageNormalizer(
    private val context: Context,
    store: AttachmentStore,
    private val loader: ImageLoader,
) : MediaNormalizer {

    override val kind: AttachmentKind = AttachmentKind.IMAGE

    private val encoder = BitmapEncoder(store)

    override fun normalize(uri: Uri, info: MediaProbe.MediaInfo): Flow<NormalizeProgress> = flow {
        if (info.byteSize > MediaLimits.IMAGE_SOURCE_MAX_BYTES) {
            emit(
                NormalizeProgress.Failed(
                    NormalizeError.TooLarge(info.byteSize, MediaLimits.IMAGE_SOURCE_MAX_BYTES)
                )
            )
            return@flow
        }

        emit(NormalizeProgress.Working(10, NormalizeStage.DECODING))
        val bitmap = decode(uri, MediaLimits.IMAGE_MAX_EDGE)
        if (bitmap == null) {
            emit(NormalizeProgress.Failed(NormalizeError.DecodeFailed(info.mime)))
            return@flow
        }

        emit(NormalizeProgress.Working(60, NormalizeStage.ENCODING))
        // encode 内部无论成败都会 recycle bitmap，这里之后不要再碰它
        val media = encoder.encode(bitmap, MediaLimits.IMAGE_MAX_EDGE)
        if (media == null) {
            emit(NormalizeProgress.Failed(NormalizeError.WriteFailed))
            return@flow
        }

        emit(NormalizeProgress.Success(NormalizeResult(primary = media)))
    }.flowOn(Dispatchers.IO)

    /**
     * 解出一张长边不超过 [maxEdge] 的 bitmap。
     *
     * 几个参数不是可选项：
     * - `Precision.INEXACT` + `Scale.FIT`：让 Coil 用 `inSampleSize` 做**廉价**的降采样。
     *   `inSampleSize` 只能是 2 的幂，所以结果可能仍然大于 [maxEdge]，
     *   由 [BitmapEncoder] 再精确缩一次。反过来用 `EXACT` 会把小图**放大**到 maxEdge，
     *   白烧 token
     * - `allowHardware(false)`：硬件 bitmap 的像素在 GPU 内存里，
     *   `createScaledBitmap` 和 `hasAlpha` 判定都用不了
     * - 关掉两级缓存：这是一次性的入库解码，缓存一张 1568 的全尺寸图没有复用价值
     */
    private suspend fun decode(uri: Uri, maxEdge: Int): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(maxEdge, maxEdge)
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()

        val result = loader.execute(request)
        if (result !is SuccessResult) {
            Log.w(TAG, "coil decode failed for $uri", (result as? coil3.request.ErrorResult)?.throwable)
            return null
        }
        return (result.image as? BitmapImage)?.bitmap.also {
            if (it == null) Log.w(TAG, "decoded image is not a bitmap: ${result.image}")
        }
    }

    companion object {
        private const val TAG = "ImageNormalizer"

        /**
         * 入库专用的 `ImageLoader`。
         *
         * 和 UI 用的加载器**刻意分开**：这个关掉了两级缓存（一次性解码，缓存无意义），
         * 而 UI 那个恰恰要靠缓存。共用一个实例会让列表滚动把入库用的大图挤进内存缓存。
         */
        fun defaultLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
            .components {
                // 放在最前面，盖住默认那个 RESPECT_PERFORMANCE 的实例
                add(
                    BitmapFactoryDecoder.Factory(
                        exifOrientationStrategy = ExifOrientationStrategy.RESPECT_ALL,
                    )
                )
            }
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
}
