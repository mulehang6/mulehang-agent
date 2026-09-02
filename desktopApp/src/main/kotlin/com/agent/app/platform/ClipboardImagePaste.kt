package com.agent.app.platform

import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** 从系统剪贴板读取并统一转码后的图片。 */
data class ClipboardPngImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

/**
 * 若剪贴板含有图片则转为 PNG；普通文本返回 null，让 TextArea 使用其原生粘贴行为。
 *
 * 输出最长边上限为 2000px，保持纵横比并使用双线性缩放。图片不会被写入项目目录，调用方负责
 * 将 bytes 交给会话媒体库。
 */
fun readClipboardImageAsPng(
    maxDimension: Int = DEFAULT_MAX_IMAGE_DIMENSION,
): ClipboardPngImage? = runCatching {
    val transferable = Toolkit.getDefaultToolkit().systemClipboard.getContents(null) ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) return null
    val image = transferable.getTransferData(DataFlavor.imageFlavor) as? Image ?: return null
    val source = image.toBufferedImage()
    val normalized = source.resizeLongestSide(maxDimension)
    val bytes = ByteArrayOutputStream().use { output ->
        check(ImageIO.write(normalized, "png", output)) { "无法将剪贴板图片编码为 PNG。" }
        output.toByteArray()
    }
    ClipboardPngImage(bytes = bytes, width = normalized.width, height = normalized.height)
}.getOrNull()

/** 将任意 AWT Image 绘制为可稳定访问像素的 BufferedImage。 */
private fun Image.toBufferedImage(): BufferedImage {
    val width = getWidth(null)
    val height = getHeight(null)
    require(width > 0 && height > 0) { "剪贴板图片尺寸无效。" }
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { target ->
        val graphics = target.createGraphics()
        try {
            graphics.drawImage(this, 0, 0, null)
        } finally {
            graphics.dispose()
        }
    }
}

/** 仅在最长边超过阈值时缩放，避免无意义地重新采样小图片。 */
private fun BufferedImage.resizeLongestSide(maxDimension: Int): BufferedImage {
    require(maxDimension > 0) { "图片尺寸上限必须大于 0。" }
    val longest = maxOf(width, height)
    if (longest <= maxDimension) return this
    val scale = maxDimension.toDouble() / longest
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB).also { target ->
        val graphics = target.createGraphics()
        try {
            graphics.configureImageScaling()
            graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
    }
}

/** 使用适合截图、UI 和照片的平滑缩放 hint。 */
private fun Graphics2D.configureImageScaling() {
    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
}

private const val DEFAULT_MAX_IMAGE_DIMENSION = 2_000
