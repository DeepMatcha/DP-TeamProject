package com.holub.life.api;
import java.io.*;
/** Does not own Model state. Caller owns streams; codec must not close them. */
public interface SnapshotCodec {
    String formatId();
    void save(GameSnapshot snapshot, OutputStream out, CellTypeLookup types) throws IOException;
    GameSnapshot load(InputStream in, CellTypeLookup types) throws IOException;
}
