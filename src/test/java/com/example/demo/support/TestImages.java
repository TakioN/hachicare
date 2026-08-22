package com.example.demo.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

public final class TestImages {

    private TestImages() {
    }

    /** 실제로 디코드되는 최소 크기 PNG. */
    public static byte[] png() {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * HEIC 컨테이너 헤더. ftyp 박스만 갖춘 최소 형태로, 픽셀 데이터는 없다.
     * 검증기가 디코드가 아니라 헤더만 보므로 이걸로 충분하다.
     */
    public static byte[] heicHeader(String majorBrand, String... compatibleBrands) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 16 + compatibleBrands.length * 4);   // box size
        writeAscii(out, "ftyp");
        writeAscii(out, majorBrand);
        writeInt(out, 0);                                   // minor version
        for (String brand : compatibleBrands) {
            writeAscii(out, brand);
        }
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
