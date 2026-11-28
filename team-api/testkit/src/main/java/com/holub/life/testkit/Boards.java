package com.holub.life.testkit;

import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.api.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Small builders so test fixtures can be drawn as text. */
public final class Boards {
    private Boards() {}

    public static BoardState filled(int width, int height, CellState fill) {
        return new BoardState(new BoardSize(width, height), 0, 0, Collections.nCopies(width * height, fill));
    }

    /** One string per row; '.' is the fake host's empty cell, '#' its alive cell, others come from legend. */
    public static BoardState parse(Map<Character, CellState> legend, String... rows) {
        List<CellState> cells = new ArrayList<>();
        for (String row : rows) {
            if (row.length() != rows[0].length()) throw new IllegalArgumentException("Ragged rows");
            for (char symbol : row.toCharArray()) {
                CellState state = symbol == '.' ? FakeHost.EMPTY_STATE
                        : symbol == '#' ? FakeHost.ALIVE_STATE : legend.get(symbol);
                if (state == null) throw new IllegalArgumentException("Unknown symbol " + symbol);
                cells.add(state);
            }
        }
        return new BoardState(new BoardSize(rows[0].length(), rows.length), 0, 0, cells);
    }

    public static BoardState parse(String... rows) { return parse(Map.of(), rows); }

    public static BoardState with(BoardState board, int x, int y, CellState state) {
        board.cellAt(new GridPos(x, y));
        List<CellState> cells = new ArrayList<>(board.cells());
        cells.set(y * board.size().width() + x, state);
        return new BoardState(board.size(), board.generation(), board.seed(), cells);
    }

    public static BoardState withSeed(BoardState board, long seed) {
        return new BoardState(board.size(), board.generation(), seed, board.cells());
    }

    public static CellState at(BoardState board, int x, int y) { return board.cellAt(new GridPos(x, y)); }
}
