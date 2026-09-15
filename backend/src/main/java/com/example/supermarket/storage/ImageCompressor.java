package com.example.supermarket.storage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * 上传图片压缩：超过阈值的大图按比例缩到指定宽度并转 JPEG。
 * 目的：避免运营直接上传数 MB 的原图（如 2848×1600 / 3.8MB），
 * 导致前台轮播/列表加载慢、切换闪烁。
 */
public final class ImageCompressor {

    /** 超过该宽度则等比缩放 */
    private static final int MAX_WIDTH = 1600;
    /** 小于该体积不处理 */
    private static final long THRESHOLD_BYTES = 500 * 1024L;
    private static final float JPEG_QUALITY = 0.85f;

    private ImageCompressor() {
    }

    /**
     * @return 压缩后的 JPEG 字节；无需压缩或无法解码（如 webp）时返回 null
     */
    public static byte[] shrinkIfNeeded(byte[] source) {
        if (source == null || source.length <= THRESHOLD_BYTES) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null || image.getWidth() <= 0) {
                return null; // webp 等 JDK 原生不支持的格式，保持原样
            }
            int width = image.getWidth();
            double scale = Math.min(1.0, (double) MAX_WIDTH / width);
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));

            BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE); // JPEG 无透明通道，先铺白底避免黑块
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
            g.dispose();

            byte[] compressed = writeJpeg(canvas);
            return (compressed != null && compressed.length < source.length) ? compressed : null;
        } catch (Exception e) {
            return null; // 压缩失败不阻塞上传
        }
    }

    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = null;
        try {
            writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (writer != null) {
                writer.dispose();
            }
        }
    }
}
