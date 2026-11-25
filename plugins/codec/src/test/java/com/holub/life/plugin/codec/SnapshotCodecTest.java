package com.holub.life.plugin.codec;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.GameSnapshot;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.SnapshotCodec;
import com.holub.life.testkit.FakeHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCodecTest {
    private static final CellType SPORE = new CellType("test:spore") {
        @Override public boolean isAlive(CellState state) { return true; }
    };
    private static final LifePlugin SPORE_PLUGIN = new LifePlugin() {
        @Override public String id() { return "test:spores"; }
        @Override public void register(PluginRegistrar registrar) { registrar.registerCellType(SPORE); }
    };

    /** Host that knows the extension type, and one that does not have that plugin installed. */
    private final FakeHost host = new FakeHost().load(new CodecPlugin()).load(SPORE_PLUGIN);
    private final FakeHost hostWithoutSpores = new FakeHost().load(new CodecPlugin());

    static Stream<SnapshotCodec> codecs() {
        return Stream.of(new JsonSnapshotCodec(), new BinarySnapshotCodec());
    }

    private static GameSnapshot snapshot() {
        CellState spore = new CellState(SPORE, Map.of("age", "3", "note", "say \"hi\"\n한글 \\ tab\t"));
        CellState youngSpore = new CellState(SPORE, Map.of("age", "0"));
        BoardState board = new BoardState(new BoardSize(3, 2), 41, -7_000_000_000L,
                List.of(FakeHost.EMPTY_STATE, FakeHost.ALIVE_STATE, spore,
                        youngSpore, FakeHost.EMPTY_STATE, spore));
        SimulationPlan plan = new SimulationPlan("toroidal:wrap",
                List.of(new PluginSelection("b-first", true), new PluginSelection("a-second", false)),
                Map.of("b-first", Map.of("maxAge", "20", "mode", "x")));
        return new GameSnapshot(board, plan, Map.of("b-first", "1.2", "a-second", "0.9"));
    }

    private byte[] saved(SnapshotCodec codec) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.save(snapshot(), out, host);
        return out.toByteArray();
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void roundTripKeepsBoardPlanOrderOptionsAndVersions(SnapshotCodec codec) throws IOException {
        GameSnapshot loaded = codec.load(new ByteArrayInputStream(saved(codec)), host);

        assertEquals(snapshot(), loaded);
        assertEquals(List.of("b-first", "a-second"),
                loaded.plan().plugins().stream().map(PluginSelection::pluginId).toList(), "plan order is execution order");
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void loadedCellsUseTheRegisteredSingletons(SnapshotCodec codec) throws IOException {
        BoardState board = codec.load(new ByteArrayInputStream(saved(codec)), host).board();

        assertSame(FakeHost.EMPTY, board.cells().get(0).type());
        assertSame(FakeHost.ALIVE, board.cells().get(1).type());
        assertSame(SPORE, board.cells().get(2).type());
        assertEquals("3", board.cells().get(2).properties().get("age"));
        assertEquals("0", board.cells().get(3).properties().get("age"), "properties stay per coordinate");
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void savingATypeTheLookupDoesNotKnowFails(SnapshotCodec codec) {
        assertThrows(IOException.class, () -> codec.save(snapshot(), new ByteArrayOutputStream(), hostWithoutSpores));
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void loadingATypeIdThatIsNotRegisteredFails(SnapshotCodec codec) throws IOException {
        byte[] bytes = saved(codec);

        IOException error = assertThrows(IOException.class,
                () -> codec.load(new ByteArrayInputStream(bytes), hostWithoutSpores));
        assertTrue(error.getMessage().contains("test:spore"), error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void truncatedOrForeignDataFails(SnapshotCodec codec) throws IOException {
        byte[] bytes = saved(codec);

        assertThrows(IOException.class, () -> codec.load(new ByteArrayInputStream(Arrays.copyOf(bytes, bytes.length / 2)), host));
        assertThrows(IOException.class, () -> codec.load(new ByteArrayInputStream(new byte[0]), host));
        assertThrows(IOException.class,
                () -> codec.load(new ByteArrayInputStream("12 34\n5 6\n".getBytes(StandardCharsets.UTF_8)), host));
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void codecDoesNotCloseTheCallersStreams(SnapshotCodec codec) throws IOException {
        boolean[] closed = new boolean[2];
        ByteArrayOutputStream out = new ByteArrayOutputStream() {
            @Override public void close() { closed[0] = true; }
        };
        codec.save(snapshot(), out, host);
        InputStream in = new ByteArrayInputStream(out.toByteArray()) {
            @Override public void close() { closed[1] = true; }
        };
        codec.load(in, host);

        assertFalse(closed[0], "output stream left open");
        assertFalse(closed[1], "input stream left open");
    }

    @Test
    void formatsDoNotAcceptEachOthersData() throws IOException {
        byte[] json = saved(new JsonSnapshotCodec());
        byte[] binary = saved(new BinarySnapshotCodec());

        assertThrows(IOException.class, () -> new BinarySnapshotCodec().load(new ByteArrayInputStream(json), host));
        assertThrows(IOException.class, () -> new JsonSnapshotCodec().load(new ByteArrayInputStream(binary), host));
    }

    @Test
    void jsonIsReadableTextWithTypeIds() throws IOException {
        String text = new String(saved(new JsonSnapshotCodec()), StandardCharsets.UTF_8);

        assertTrue(text.startsWith("{\"format\":\"life-snapshot\",\"version\":1,"), text);
        assertTrue(text.contains("\"types\":[\"core:empty\",\"core:alive\",\"test:spore\"]"), text);
        assertTrue(text.contains("\"seed\":-7000000000"), text);
    }

    @Test
    void jsonRejectsAnotherVersion() throws IOException {
        String text = new String(saved(new JsonSnapshotCodec()), StandardCharsets.UTF_8).replace("\"version\":1", "\"version\":2");

        assertThrows(IOException.class,
                () -> new JsonSnapshotCodec().load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), host));
    }

    @Test
    void jsonRejectsACellCountThatDoesNotMatchTheSize() throws IOException {
        String text = new String(saved(new JsonSnapshotCodec()), StandardCharsets.UTF_8).replace("\"width\":3", "\"width\":4");

        assertThrows(IOException.class,
                () -> new JsonSnapshotCodec().load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), host));
    }

    @Test
    void binaryReadsOnlyItsOwnBytesAndIsSmallerThanJson() throws IOException {
        byte[] binary = saved(new BinarySnapshotCodec());
        ByteArrayOutputStream twice = new ByteArrayOutputStream();
        twice.write(binary);
        twice.write(binary);
        InputStream in = new ByteArrayInputStream(twice.toByteArray());

        assertEquals(snapshot(), new BinarySnapshotCodec().load(in, host));
        assertEquals(snapshot(), new BinarySnapshotCodec().load(in, host), "the second snapshot was left unread");

        // Each distinct state is stored once, so a large uniform board stays small.
        BoardState large = new BoardState(new BoardSize(64, 64), 0, 0,
                java.util.Collections.nCopies(64 * 64, FakeHost.EMPTY_STATE));
        GameSnapshot big = new GameSnapshot(large, snapshot().plan(), Map.of());
        ByteArrayOutputStream asBinary = new ByteArrayOutputStream(), asJson = new ByteArrayOutputStream();
        new BinarySnapshotCodec().save(big, asBinary, host);
        new JsonSnapshotCodec().save(big, asJson, host);
        assertEquals(big, new BinarySnapshotCodec().load(new ByteArrayInputStream(asBinary.toByteArray()), host));
        assertEquals(big, new JsonSnapshotCodec().load(new ByteArrayInputStream(asJson.toByteArray()), host));
        assertTrue(asBinary.size() < asJson.size(), asBinary.size() + " bytes binary, " + asJson.size() + " bytes JSON");
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void boardWithManyDistinctStatesRoundTrips(SnapshotCodec codec) throws IOException {
        List<CellState> cells = new java.util.ArrayList<>();
        for (int age = 0; age < 20 * 20; age++) cells.add(new CellState(SPORE, Map.of("age", Integer.toString(age))));
        GameSnapshot many = new GameSnapshot(new BoardState(new BoardSize(20, 20), 0, 0, cells), snapshot().plan(), Map.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        codec.save(many, out, host);

        assertEquals(many, codec.load(new ByteArrayInputStream(out.toByteArray()), host));
    }

    @Test
    void pluginRegistersBothFormatsAsSnapshotCodecsOnly() {
        assertTrue(host.snapshotCodec(JsonSnapshotCodec.FORMAT_ID).isPresent());
        assertTrue(host.snapshotCodec(BinarySnapshotCodec.FORMAT_ID).isPresent());
        assertTrue(host.patternCodec(JsonSnapshotCodec.FORMAT_ID).isEmpty(), "a full snapshot is not a pattern");
        assertEquals(2, host.registrations().stream()
                .filter(registration -> registration.point() == ApiPoint.SNAPSHOT_CODEC).count());
    }
}
