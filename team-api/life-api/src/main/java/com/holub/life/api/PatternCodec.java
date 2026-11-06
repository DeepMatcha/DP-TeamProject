package com.holub.life.api;
import java.io.*;
public interface PatternCodec {
    String formatId();
    void save(Pattern pattern, OutputStream out) throws IOException;
    Pattern load(InputStream in, CellTypeLookup types) throws IOException;
}
