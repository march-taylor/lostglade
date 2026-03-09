package eu.pb4.polymer.core.impl.networking.payloads;

import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.ServerMetadataKeys;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import eu.pb4.polymer.networking.api.PolymerNetworking;
import net.minecraft.class_155;
import net.minecraft.class_2497;
import net.minecraft.class_9139;

public interface PayloadUtil {
    int PROTOCOL = class_155.method_31372();

    @SuppressWarnings("unchecked")
    static <T> class_9139<ContextByteBuf, T> protocolSecured(class_9139<ContextByteBuf, T> codec) {
        var c = (class_9139<ContextByteBuf, Object>) codec;
        return (class_9139<ContextByteBuf, T>) (Object) new class_9139<ContextByteBuf, Object>() {
            @Override
            public Object decode(ContextByteBuf buf) {
                var data = PolymerNetworking.getMetadata(buf.clientConnection(), ServerMetadataKeys.MINECRAFT_PROTOCOL, class_2497.field_21037);
                if (data == null || data.method_10701() != PROTOCOL) {
                    buf.method_52994(buf.readableBytes());
                    return PolymerNoOpPayload.INSTANCE;
                }

                return codec.decode(buf);
            }

            @Override
            public void encode(ContextByteBuf buf, Object value) {
                c.encode(buf, value);
            }
        };
    }

    static boolean clientCheck() {
        if (PolymerImpl.IS_CLIENT) {
            return InternalClientRegistry.enabled;
        }

        return true;
    }
}
