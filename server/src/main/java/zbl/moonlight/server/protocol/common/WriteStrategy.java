package zbl.moonlight.server.protocol.common;

import zbl.moonlight.server.protocol.mdtp.MdtpRequestSchema;
import zbl.moonlight.server.protocol.annotations.Schema;
import zbl.moonlight.server.protocol.annotations.SchemaEntry;
import zbl.moonlight.server.utils.ByteBufferUtils;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;

public class WriteStrategy implements Writable {
    protected HashMap<String, byte[]> map = new HashMap<>();
    private final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(4);
    private ByteBuffer data;
    /* 继承MSerializable的接口 */
    private final Class<? extends MSerializable> schemaClass;

    public WriteStrategy(Class<? extends MSerializable> schemaClass) {
        this.schemaClass = schemaClass;
    }

    @Override
    public void write(SocketChannel socketChannel) throws IOException {
        if(!ByteBufferUtils.isOver(lengthByteBuffer)) {
            socketChannel.write(lengthByteBuffer);
            if(!ByteBufferUtils.isOver(lengthByteBuffer)) {
                return;
            }
        }
        if(!ByteBufferUtils.isOver(data)) {
            socketChannel.read(data);
        }
    }

    @Override
    public boolean isWriteCompleted() {
        return ByteBufferUtils.isOver(data);
    }

    /* 往map中添加数据 */
    public void put(String name, byte[] data) {
        map.put(name, data);
    }

    public void serialize() {
        MSerializable schema = (MSerializable) Proxy.newProxyInstance(WriteStrategy.class.getClassLoader(),
                new Class[]{MdtpRequestSchema.class}, new SerializeHandler());
        /* 把ByteBuffer类型的数据解析成map */
        data = schema.serialize(map);
        lengthByteBuffer.putInt(data.limit());
    }

    /* 序列化操作的JDK动态代理InvocationHandler */
    private class SerializeHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Schema schema = schemaClass.getAnnotation(Schema.class);
            SchemaEntry[] schemaEntries = schema.value();
            int length = 0;

            /* 先遍历一遍求序列化后的总长度 */
            for(SchemaEntry entry : schemaEntries) {
                if(!map.containsKey(entry.name())) {
                    throw new IllegalCallerException("map not contains key \"" + entry.name() + "\"");
                }
                length += entry.hasLengthSize()
                        ? entry.lengthSize() + map.get(entry.name()).length
                        : entry.length();
            }

            ByteBuffer data = ByteBuffer.allocate(length);
            /* 序列化数据 */
            for(SchemaEntry entry : schemaEntries) {
                byte[] bytes = map.get(entry.name());
                if(entry.hasLengthSize()) {
                    data.putInt(bytes.length).put(bytes);
                } else {
                    data.put(bytes);
                }
            }
            return data;
        }
    }
}
