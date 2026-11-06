package com.holub.life.api;
import java.io.*;
public interface SnapshotCodec {
    String formatId();
    void save(GameSnapshot snapshot, OutputStream out) throws IOException;
    GameSnapshot load(InputStream in, CellTypeLookup types) throws IOException;
}
