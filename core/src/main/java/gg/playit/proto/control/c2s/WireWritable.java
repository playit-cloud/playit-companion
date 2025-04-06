package gg.playit.proto.control.c2s;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public interface WireWritable {
    void writeTo(ByteBuf buf) throws IOException;
    static void writeOptional(ByteBuf buf, Optional<? extends WireWritable> optional) throws IOException {
        if (optional.isPresent()) {
            buf.writeByte(1);
            optional.get().writeTo(buf);
        } else {
            buf.writeByte(0);
        }
    }
    static WireWritable readOptional(ByteBuf buf, Function<ByteBuf, WireWritable> parser) {
        if (buf.readByte() == 1) {
            return parser.apply(buf);
        } else {
            return null;
        }
    }
}
