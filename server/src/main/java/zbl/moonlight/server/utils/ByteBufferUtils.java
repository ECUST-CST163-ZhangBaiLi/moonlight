package zbl.moonlight.server.utils;

import java.nio.ByteBuffer;

public class ByteBufferUtils {
    /* 判断ByteBuffer是否读结束（或写结束） */
    public static boolean isOver(ByteBuffer byteBuffer) {
        return byteBuffer.position() == byteBuffer.limit();
    }
}
