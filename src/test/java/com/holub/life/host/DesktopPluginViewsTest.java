package com.holub.life.host;

import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.ControllerPort;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.ModelListener;
import com.holub.life.api.ModelPort;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.ViewPlugin;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopPluginViewsTest {
    @Test
    void activeViewRefreshesTopologyWithoutPublishingAnExtraHostBoardChange() throws Exception {
        var probe = new TopologyView();
        var toroidal = ServiceLoader.load(LifePlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(plugin -> "toroidal".equals(plugin.id()))
                .findFirst().orElseThrow();
        try (var host = CoreGame.newSession(new BoardSize(4, 3), 42, List.of(probe, toroidal))) {
            await(host.setPluginEnabled(probe.id(), true));
            var checkpoint = await(host.session());
            var ordinaryBoardEvents = new AtomicInteger();
            host.model().addListener(board -> ordinaryBoardEvents.incrementAndGet());
            var failure = new AtomicReference<Throwable>();
            var views = new DesktopPluginViews(host, failure::set);
            Consumer<SimulationPlan> plans = views::update;
            host.addPlanListener(plans);
            views.update(checkpoint.plan());
            try {
                assertEquals(checkpoint.board(), probe.awaitAccessor("core:bounded"));
                await(host.setPluginEnabled("toroidal", true));
                assertEquals(checkpoint.board(), probe.awaitAccessor("toroidal:wrap"));
                assertEquals(0, ordinaryBoardEvents.get());

                await(host.restore(checkpoint));
                assertEquals(checkpoint.board(), probe.awaitAccessor("core:bounded"));
                assertEquals(1, ordinaryBoardEvents.get(), "Restore publishes just its actual board commit");
                assertNull(failure.get());
            } finally {
                host.removePlanListener(plans);
                var closed = new CountDownLatch(1);
                views.close(closed::countDown);
                assertTrue(closed.await(5, TimeUnit.SECONDS));
            }
        }
    }

    private static <T> T await(CompletionStage<T> operation) throws Exception {
        return operation.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static final class TopologyView implements LifePlugin, ViewPlugin {
        private final BlockingQueue<Update> updates = new LinkedBlockingQueue<>();
        private ModelPort model;
        private ModelListener listener;

        @Override
        public String id() {
            return "test:topology-view";
        }

        @Override
        public void register(PluginRegistrar registrar) {
            registrar.registerView(this);
        }

        @Override
        public void attach(ModelPort model, ControllerPort controller) {
            this.model = model;
            listener = board -> controller.session().thenAccept(session ->
                    updates.add(new Update(session.plan().accessorId(), board)));
            model.addListener(listener);
        }

        @Override
        public void detach() {
            model.removeListener(listener);
        }

        BoardState awaitAccessor(String accessor) throws InterruptedException {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            long remaining;
            while ((remaining = until - System.nanoTime()) > 0) {
                var update = updates.poll(remaining, TimeUnit.NANOSECONDS);
                if (update != null && accessor.equals(update.accessor())) {
                    return update.board();
                }
            }
            throw new AssertionError("The view did not receive the " + accessor + " layout");
        }

        private record Update(String accessor, BoardState board) {}
    }
}
