package com.holub.life.host;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardState;
import com.holub.life.api.ModelListener;
import com.holub.life.api.ModelPort;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.ViewPlugin;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Serializes extension windows without blocking the Swing or simulation thread. */
final class DesktopPluginViews {
    private static final System.Logger LOG = System.getLogger(DesktopPluginViews.class.getName());

    private final HostController host;
    private final Consumer<Throwable> onFailure;
    private final ExecutorService operations = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("life-view-lifecycle").factory());
    private final AtomicLong revision = new AtomicLong();
    private final LinkedHashMap<String, AttachedView> attached = new LinkedHashMap<>();

    DesktopPluginViews(HostController host, Consumer<Throwable> onFailure) {
        this.host = host;
        this.onFailure = onFailure;
    }

    void update(SimulationPlan plan) {
        long requested = revision.incrementAndGet();
        operations.execute(() -> {
            if (revision.get() == requested) {
                reconcile(plan);
            }
        });
    }

    private void reconcile(SimulationPlan plan) {
        var active = host.registry().activeRegistrations(plan, ApiPoint.VIEW);
        var desired = new HashSet<String>();
        active.forEach(registration -> desired.add(registration.id()));
        for (Iterator<AttachedView> iterator = attached.values().iterator(); iterator.hasNext();) {
            AttachedView next = iterator.next();
            if (!desired.contains(next.view().id())) {
                iterator.remove();
                detach(next);
            }
        }
        for (var registration : active) {
            var current = attached.get(registration.id());
            if (current != null) {
                current.model().refresh();
            } else {
                host.registry().view(registration.id()).ifPresent(this::attach);
            }
        }
    }

    private void attach(ViewPlugin view) {
        var model = new ViewModel(host.model());
        var activation = new AttachedView(view, model);
        ExtensionCalls.failureOf(() -> {
            view.attach(model, host);
            model.connect();
            attached.put(view.id(), activation);
            model.refresh();
        }).ifPresent(failure -> {
            detach(activation);
            onFailure.accept(failure);
        });
    }

    private void detach(AttachedView attachment) {
        attachment.model().disconnect();
        ExtensionCalls.failureOf(attachment.view()::detach).ifPresent(failure ->
                LOG.log(System.Logger.Level.WARNING, "Could not detach view " + attachment.view().id(), failure));
    }

    void close(Runnable afterViewsClosed) {
        revision.incrementAndGet();
        operations.execute(() -> {
            try {
                attached.values().forEach(this::detach);
                attached.clear();
            } finally {
                afterViewsClosed.run();
            }
        });
        operations.shutdown();
    }

    private record AttachedView(ViewPlugin view, ViewModel model) {}

    /**
     * Proxy in front of the host Model for one attached view.
     * Gives only this view a refresh after plan changes, using the current committed board.
     * The host Model does not publish a board change for plan-only operations.
     */
    private static final class ViewModel implements ModelPort {
        private final ModelPort source;
        private final CopyOnWriteArrayList<ModelListener> listeners = new CopyOnWriteArrayList<>();
        private final ModelListener relay = this::publish;
        private volatile boolean connected;

        ViewModel(ModelPort source) {
            this.source = source;
        }

        void connect() {
            connected = true;
            source.addListener(relay);
        }

        void disconnect() {
            connected = false;
            source.removeListener(relay);
            listeners.clear();
        }

        synchronized void refresh() {
            publish(snapshot());
        }

        private synchronized void publish(BoardState board) {
            if (board != source.snapshot()) {
                return;
            }
            for (ModelListener listener : listeners) {
                if (!connected) {
                    return;
                }
                ExtensionCalls.failureOf(() -> listener.onChanged(board)).ifPresent(failure ->
                        LOG.log(System.Logger.Level.WARNING, "A view could not refresh", failure));
            }
        }

        @Override
        public BoardState snapshot() {
            return source.snapshot();
        }

        @Override
        public void addListener(ModelListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(ModelListener listener) {
            listeners.remove(listener);
        }
    }
}
