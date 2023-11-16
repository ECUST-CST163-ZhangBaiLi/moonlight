package com.bailizhang.lynxdb.core.common;

import com.bailizhang.lynxdb.core.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import static com.bailizhang.lynxdb.core.utils.PrimitiveTypeUtils.*;

public class BytesList implements BytesConvertible {
    public static final byte RAW = (byte) 0x01;
    public static final byte VAR = (byte) 0x02;

    private final LinkedList<BytesNode<?>> bytesNodes = new LinkedList<>();
    private final boolean withLength;

    private int length;
    private int bufferCount;

    public BytesList() {
        withLength = true;
        length = INT_LENGTH;
        bufferCount = 1;
    }

    public BytesList(boolean withLen) {
        withLength = withLen;
        length = withLen ? INT_LENGTH : 0;
        bufferCount = withLen ? 1 : 0;
    }

    public void appendRawByte(byte value) {
        append(RAW, value);
    }

    public void appendRawBytes(byte[] value) {
        append(RAW, value);
    }

    public void appendRawStr(String s) {
        append(RAW, G.I.toBytes(s));
    }

    public void appendRawInt(int value) {
        append(RAW, value);
    }

    public void appendRawLong(long value) {
        append(RAW, value);
    }

    public void appendVarBytes(byte[] value) {
        append(VAR, value);
    }

    public void appendVarStr(String s) {
        append(VAR, G.I.toBytes(s));
    }

    public <V> void append(byte type, V value) {
        bytesNodes.add(new BytesNode<>(type, value));
        length += type == VAR ? INT_LENGTH : 0;

        switch (value) {
            case Integer i -> length += INT_LENGTH;
            case Long l -> length += LONG_LENGTH;
            case Byte b -> length += BYTE_LENGTH;
            case byte[] bytes -> length += bytes.length;
            default -> throw new RuntimeException("Undefined value type");
        }

        bufferCount = type == VAR ? 2 : 1;
    }

    public void append(BytesList list) {
        bytesNodes.addAll(list.bytesNodes);
        length += list.length;
    }

    public void append(BytesListConvertible convertible) {
        BytesList list = convertible.toBytesList();
        append(list);
    }

    @Override
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(length);

        if(withLength) {
            buffer.putInt(length - INT_LENGTH);
        }

        for(BytesNode<?> node : bytesNodes) {
            if(node.type == VAR) {
                if(node.value instanceof byte[] bytes) {
                    buffer.putInt(bytes.length);
                } else {
                    throw new RuntimeException("Undefined value type");
                }
            }

            switch (node.value) {
                case Integer i -> buffer.putInt(i);
                case Long l -> buffer.putLong(l);
                case Byte b -> buffer.put(b);
                case byte[] bytes -> buffer.put(bytes);
                default -> throw new RuntimeException("Undefined value type");
            }
        }

        return buffer.array();
    }

    public ByteBuffer[] toBuffers() {
        ByteBuffer[] buffers = new ByteBuffer[bufferCount];

        int idx = 0;
        if(withLength) {
            buffers[0] = BufferUtils.intByteBuffer(length);
            idx = 1;
        }

        for(BytesNode<?> node : bytesNodes) {
            if(node.type == VAR) {
                if(node.value instanceof byte[] bytes) {
                    buffers[idx++] = BufferUtils.intByteBuffer(bytes.length);
                } else {
                    throw new RuntimeException("Undefined value type");
                }
            }

            switch (node.value) {
                case Integer intValue -> buffers[idx++] = BufferUtils.intByteBuffer(intValue);
                case Long longValue -> buffers[idx++] = BufferUtils.longByteBuffer(longValue);
                case Byte byteValue -> buffers[idx++] = BufferUtils.byteByteBuffer(byteValue);
                case byte[] bytes -> buffers[idx++] = ByteBuffer.wrap(bytes);
                default -> throw new RuntimeException("Undefined value type");
            }
        }

        return buffers;
    }

    private static class BytesNode<V> {
        private final byte type;
        private final V value;

        private BytesNode(byte t, V v) {
            type = t;
            value = v;
        }
    }
}
