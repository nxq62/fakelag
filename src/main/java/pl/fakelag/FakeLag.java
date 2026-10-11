package pl.fakelag;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayDeque;

/**
 * Dwa tryby (obejmuja tylko pakiety wychodzace polaczenia klienta z serwerem):
 * DELAY - kazdy pakiet jest wysylany z opoznieniem, w tej samej kolejnosci,
 * HOLD  - pakiety sa zatrzymywane, az tryb zostanie wylaczony (albo minie limit czasu).
 */
public final class FakeLag {
    public enum Mode { OFF, DELAY, HOLD }

    private static final int[] DELAYS_MS = {100, 250, 500, 1000, 2000};
    // Serwer rozlacza klienta, ktory nie odpowiada na keepalive przez ok. 15 s,
    // wiec tryb HOLD sam sie zwalnia wczesniej.
    private static final long HOLD_LIMIT_MS = 10_000L;

    private record Held(Connection connection, Packet<?> packet, long queuedAt, long releaseAt) {}

    private static final ArrayDeque<Held> QUEUE = new ArrayDeque<>();
    private static volatile Mode mode = Mode.OFF;
    private static volatile boolean releasing;
    private static int delayIndex = 2; // 500 ms

    private FakeLag() {}

    public static int delayMs() {
        return DELAYS_MS[delayIndex];
    }

    /** Wolane z mixina w Connection.send. Zwraca true, gdy pakiet zostal przechwycony. */
    public static boolean intercept(Connection connection, Packet<?> packet) {
        Mode current = mode;
        if (current == Mode.OFF || releasing) {
            return false;
        }
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection() != connection) {
            return false; // nie jest to polaczenie klienta (np. watek serwera w singleplayerze)
        }
        long now = System.currentTimeMillis();
        long releaseAt = current == Mode.HOLD ? Long.MAX_VALUE : now + delayMs();
        synchronized (QUEUE) {
            QUEUE.addLast(new Held(connection, packet, now, releaseAt));
        }
        return true;
    }

    public static void toggleHold(Minecraft mc) {
        setMode(mc, mode == Mode.HOLD ? Mode.OFF : Mode.HOLD);
    }

    public static void toggleDelay(Minecraft mc) {
        setMode(mc, mode == Mode.DELAY ? Mode.OFF : Mode.DELAY);
    }

    public static void cycleDelay(Minecraft mc) {
        delayIndex = (delayIndex + 1) % DELAYS_MS.length;
        message(mc, "Fakelag delay: " + delayMs() + " ms");
    }

    private static void setMode(Minecraft mc, Mode newMode) {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        flushAll(); // zawsze najpierw oproznij kolejke, w kolejnosci
        mode = newMode;
        switch (newMode) {
            case OFF -> message(mc, "Fakelag: OFF");
            case DELAY -> message(mc, "Fakelag: DELAY " + delayMs() + " ms");
            case HOLD -> message(mc, "Fakelag: HOLD (limit " + (HOLD_LIMIT_MS / 1000) + " s)");
        }
    }

    /** Wolane na starcie kazdego ticka klienta. */
    public static void tick(Minecraft mc) {
        if (mc.getConnection() == null) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();

        if (mode == Mode.HOLD) {
            Held oldest;
            synchronized (QUEUE) {
                oldest = QUEUE.peekFirst();
            }
            if (oldest != null && now - oldest.queuedAt() >= HOLD_LIMIT_MS) {
                flushAll();
                mode = Mode.OFF;
                message(mc, "Fakelag: HOLD released (time limit)");
            }
            return;
        }

        while (true) {
            Held head;
            synchronized (QUEUE) {
                head = QUEUE.peekFirst();
                if (head == null || head.releaseAt() > now) {
                    return;
                }
                QUEUE.pollFirst();
            }
            send(head);
        }
    }

    private static void flushAll() {
        while (true) {
            Held head;
            synchronized (QUEUE) {
                head = QUEUE.pollFirst();
            }
            if (head == null) {
                return;
            }
            send(head);
        }
    }

    private static void send(Held held) {
        releasing = true;
        try {
            held.connection().send(held.packet());
        } catch (Exception ignored) {
            // polaczenie moglo sie zamknac - nic z tym nie robimy
        } finally {
            releasing = false;
        }
    }

    private static void message(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text), true);
        }
    }

    /** Zerowanie stanu po rozlaczeniu - wstrzymane pakiety sa porzucane. */
    public static void reset() {
        mode = Mode.OFF;
        synchronized (QUEUE) {
            QUEUE.clear();
        }
    }
}
