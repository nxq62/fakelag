package pl.fakelag.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.fakelag.FakeLag;

@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void fakelag$holdPacket(Packet<?> packet, CallbackInfo ci) {
        if (FakeLag.intercept((Connection) (Object) this, packet)) {
            ci.cancel();
        }
    }
}
