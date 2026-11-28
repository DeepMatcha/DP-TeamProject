package com.holub.life;

import com.holub.life.api.BoardSize;
import com.holub.life.host.CoreGame;
import com.holub.life.host.HostController;
import com.holub.life.host.PluginDirectory;
import com.holub.life.host.SwingLifeView;
import java.io.IOException;
import java.util.ServiceConfigurationError;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/*******************************************************************
 * An implementation of Conway's Game of Life.
 *
 * @include /etc/license.txt
 */
public final class Life {
    private Life() {}

    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(Life::openGame);
    }

    private static void openGame() {
        var board = new BoardSize(64, 64);
        long seed = ThreadLocalRandom.current().nextLong();
        var thread = Thread.currentThread();
        var previous = thread.getContextClassLoader();
        PluginDirectory directory = null;
        HostController host;
        try {
            directory = PluginDirectory.open();
            thread.setContextClassLoader(directory.classLoader());
            host = CoreGame.newSession(board, seed, directory.plugins());
        } catch (IOException | RuntimeException | LinkageError | ServiceConfigurationError failure) {
            thread.setContextClassLoader(previous);
            close(directory);
            new SwingLifeView(CoreGame.newSession(board, seed)).show();
            JOptionPane.showMessageDialog(null, "Starting the default game.\n" + failure,
                    "Could not load plug-ins", JOptionPane.WARNING_MESSAGE);
            return;
        }
        var loaded = directory;
        Runnable release = () -> {
            thread.setContextClassLoader(previous);
            close(loaded);
        };
        try {
            new SwingLifeView(host, release).show();
        } catch (RuntimeException | Error failure) {
            host.close();
            release.run();
            throw failure;
        }
    }

    private static void close(PluginDirectory directory) {
        if (directory == null) return;
        try {
            directory.close();
        } catch (IOException failure) {
            System.getLogger(Life.class.getName()).log(System.Logger.Level.WARNING, "Closing plug-in JARs", failure);
        }
    }
}
