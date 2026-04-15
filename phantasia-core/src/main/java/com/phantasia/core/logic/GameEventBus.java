package com.phantasia.core.logic;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

public class GameEventBus {

    private static final GameEventBus INSTANCE = new GameEventBus();
    private final List<Consumer<GameEvent>> subscribers = new ArrayList<>();

    public static GameEventBus get() { return INSTANCE; }

    /**
     * Registers a subscriber and returns a {@link Subscription} that can
     * cancel the registration by calling {@link Subscription#cancel()}.
     * Preferred over {@link #register}/{@link #unregister} because it avoids
     * the lambda-identity pitfall where unregister() can't match the original.
     */
    public Subscription subscribe(Consumer<GameEvent> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    /**
     * Registers a subscriber without returning a handle.
     * Use {@link #subscribe} when you need to be able to cancel later.
     */
    public void register(Consumer<GameEvent> subscriber) {
        subscribers.add(subscriber);
    }

    public void unregister(Consumer<GameEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    public void fire(GameEvent event) {
        subscribers.forEach(s -> s.accept(event));
    }

    // -------------------------------------------------------------------------
    // Subscription handle
    // -------------------------------------------------------------------------

    /**
     * A cancellable registration returned by {@link #subscribe}.
     * Call {@link #cancel()} in {@code hide()} or {@code dispose()} to
     * prevent the subscriber from firing after the screen is gone.
     */
    @FunctionalInterface
    public interface Subscription {
        void cancel();
    }
}