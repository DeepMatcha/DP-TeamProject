package com.holub.life.host;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/** The Grid menu's Load and Store items: file dialogs in front of {@link PatternFileCommands}. */
final class PatternMenus {
    private final PatternFileCommands commands;
    private final JFrame frame;
    private final BiConsumer<String, CompletionStage<?>> progress;
    private final JFileChooser chooser = new JFileChooser();

    PatternMenus(HostController host, JFrame frame, BiConsumer<String, CompletionStage<?>> progress) {
        commands = new PatternFileCommands(host);
        this.frame = frame;
        this.progress = progress;
        chooser.setFileFilter(new FileNameExtensionFilter("Life patterns (*.life)", "life"));
    }

    void install(JMenu grid) {
        grid.add(item("Load", this::load));
        grid.add(item("Store", this::store));
    }

    private void load() {
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        progress.accept("Loading pattern…", commands.load(chooser.getSelectedFile().toPath()));
    }

    private void store() {
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destination = PatternFileCommands.destination(chooser.getSelectedFile().toPath());
        if (Files.exists(destination) && JOptionPane.showConfirmDialog(
                frame, "Replace the existing pattern?", "Store Life pattern",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        progress.accept("Storing pattern…", commands.store(destination));
    }

    private static JMenuItem item(String name, Runnable action) {
        var item = new JMenuItem(name);
        item.addActionListener(event -> action.run());
        return item;
    }
}
