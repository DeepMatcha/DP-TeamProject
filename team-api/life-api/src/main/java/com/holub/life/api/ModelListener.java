package com.holub.life.api;
@FunctionalInterface
public interface ModelListener {
    void onChanged(BoardState current);
}
