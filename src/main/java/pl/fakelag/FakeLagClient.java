package pl.fakelag;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class FakeLagClient implements ClientModInitializer {
    private static KeyMapping holdKey;
    private static KeyMapping delayKey;
    private static KeyMapping delayCycleKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fakelag", "main"));

        holdKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.fakelag.hold", GLFW.GLFW_KEY_L, category));
        delayKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.fakelag.delay", GLFW.GLFW_KEY_SEMICOLON, category));
        delayCycleKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.fakelag.delay_cycle", GLFW.GLFW_KEY_APOSTROPHE, category));

        ClientTickEvents.START_CLIENT_TICK.register(FakeLag::tick);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (holdKey.consumeClick()) {
                if (mc.screen == null) {
                    FakeLag.toggleHold(mc);
                }
            }
            while (delayKey.consumeClick()) {
                if (mc.screen == null) {
                    FakeLag.toggleDelay(mc);
                }
            }
            while (delayCycleKey.consumeClick()) {
                if (mc.screen == null) {
                    FakeLag.cycleDelay(mc);
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> FakeLag.reset());
    }
}
