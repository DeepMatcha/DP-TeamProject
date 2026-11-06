package com.holub.life.api;
public record BoardSize(int width, int height) {
    public BoardSize {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
    }
    public int area() { return Math.multiplyExact(width, height); }
}
