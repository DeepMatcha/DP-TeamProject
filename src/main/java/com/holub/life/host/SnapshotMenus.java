package com.holub.life.host;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.SnapshotCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

/** Offers stream formats registered by enabled snapshot providers. */
final class SnapshotMenus {
    private final HostController host;
    private final JFrame frame;
    private final BiConsumer<String, CompletionStage<?>> progress;
    private final JMenu formats = new JMenu("Session files");
    private final JFileChooser chooser = new JFileChooser();

    SnapshotMenus(HostController host, JFrame frame, BiConsumer<String, CompletionStage<?>> progress) {
        this.host = host;
        this.frame = frame;
        this.progress = progress;
    }

    void install(JMenu grid) {
        formats.setVisible(false);
        grid.add(formats);
    }

    void planChanged(SimulationPlan plan) {
        formats.removeAll();
        for (var registration : host.registry().activeRegistrations(plan, ApiPoint.SNAPSHOT_CODEC)) {
            SnapshotCodec codec = host.registry().snapshotCodec(registration.id()).orElseThrow();
            var format = new JMenu(registration.id());
            var save = new JMenuItem("Save session…");
            var load = new JMenuItem("Load session…");
            save.addActionListener(event -> save(codec));
            load.addActionListener(event -> load(codec));
            format.add(save);
            format.add(load);
            formats.add(format);
        }
        formats.setVisible(formats.getItemCount() != 0);
    }

    private void save(SnapshotCodec codec) {
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        if (Files.exists(path) && JOptionPane.showConfirmDialog(frame, "Replace the existing file?",
                "Save session", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        progress.accept("Saving session…", host.session().thenAcceptAsync(snapshot -> {
            try {
                AtomicFileWriter.store(path, stream -> codec.save(snapshot, stream, host.registry()));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }));
    }

    private void load(SnapshotCodec codec) {
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        var reading = CompletableFuture.supplyAsync(() -> {
            try (var stream = Files.newInputStream(path)) {
                return codec.load(stream, host.registry());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
        progress.accept("Loading session…", reading.thenCompose(host::restore));
    }
}
