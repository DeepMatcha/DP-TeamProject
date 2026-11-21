package com.holub.life.host;

import com.holub.life.api.BoardState;
import com.holub.life.api.ModelListener;
import com.holub.life.api.ModelPort;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

final class HostBoardModel implements ModelPort {
    private static final System.Logger LOG = System.getLogger(HostBoardModel.class.getName());

    private final CopyOnWriteArrayList<ModelListener> listeners = new CopyOnWriteArrayList<>();
    private volatile BoardState current;

    HostBoardModel(BoardState initial) {
        current = Objects.requireNonNull(initial);
    }

    @Override
    public BoardState snapshot() {
        return current;
    }

    @Override
    public void addListener(ModelListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void removeListener(ModelListener listener) {
        listeners.remove(listener);
    }

    void commit(BoardState next) {
        current = next;
        for (ModelListener listener : listeners) {
            try {
                listener.onChanged(next);
            } catch (RuntimeException | AssertionError error) {
                LOG.log(System.Logger.Level.WARNING, "Model listener could not consume a commit", error);
            }
        }
    }
}
