package com.holub.life;

import com.holub.life.api.BoardSize;
import com.holub.life.host.CoreGame;
import com.holub.life.host.SwingLifeView;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.SwingUtilities;

/*******************************************************************
 * An implemenation of Conway's Game of Life.
 *
 * @include /etc/license.txt
 */
public final class Life {
    private Life() {}

    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(() -> {
            long seed = ThreadLocalRandom.current().nextLong();
            var host = CoreGame.newSession(new BoardSize(64, 64), seed);
            new SwingLifeView(host).show();
        });
    }
}
