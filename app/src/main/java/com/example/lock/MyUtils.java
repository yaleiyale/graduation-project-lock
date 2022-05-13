package com.example.lock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MyUtils {
    public static byte[] leIntToByteArray(int i) {

        final ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE);

        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.putInt(i);

        return bb.array();

    }
}
