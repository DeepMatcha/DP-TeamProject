package com.holub.life.api;
import java.io.*;
/** Brush data only. Caller owns streams; codec must not close them. */
public interface PatternCodec {
    String formatId();
    void save(Pattern pattern, OutputStream out, CellTypeLookup types) throws IOException;
    Pattern load(InputStream in, CellTypeLookup types) throws IOException;
}
