package com.holub.life.host;

import com.holub.life.api.Accessor;
import com.holub.life.api.AccessorFactory;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellSample;
import com.holub.life.api.GridPos;
import java.util.Objects;
import java.util.Optional;

/** Maps coordinates into a finite rectangular board without wrapping (the default topology Strategy). */
public final class BoundedAccessor implements AccessorFactory<GridPos> {
    public static final String ID = "core:bounded";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Class<GridPos> coordinateType() {
        return GridPos.class;
    }

    @Override
    public Accessor<GridPos> bind(BoardState snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return position -> contains(snapshot.size(), position)
            ? Optional.of(new CellSample(position, snapshot.cellAt(position)))
            : Optional.empty();
    }

    static boolean contains(BoardSize size, GridPos position) {
        return position.x() >= 0 && position.x() < size.width()
            && position.y() >= 0 && position.y() < size.height();
    }
}
