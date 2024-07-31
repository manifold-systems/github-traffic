package github.traffic;

import manifold.github.traffic.Tile;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TileTest {
    @Test
    public void testManualLayout() {
        Tile p = new Tile("a\nb\nc");
        p.add(3, 0, "1\n2\n3");
        assertEquals("a  1\n" +
                     "b  2\n" +
                     "c  3", p.render());

        p = new Tile("a\nb\nc");
        p.add(3, 0, "1\n2");
        assertEquals("a  1\n" +
                     "b  2\n" +
                     "c", p.render());

        p = new Tile("a\nb");
        p.add(3, 0, "1\n2\n3");
        assertEquals("a  1\n" +
                     "b  2\n" +
                     "   3", p.render());

        p = new Tile("a\nb\nc\n");
        p.add(3, 1, "1\n2\n3");
        assertEquals("a\n" +
                     "b  1\n" +
                     "c  2\n" +
                     "   3", p.render());

        p = new Tile("a\nb\nc");
        p.add(3, 4, "1\n2\n3");
        assertEquals("a\n" +
                     "b\n" +
                     "c\n" +
                     "\n" +
                     "   1\n" +
                     "   2\n" +
                     "   3", p.render());

        p = new Tile("a\nboyhowdy\nc");
        p.add(3, 0, "1\n2\n3");
        assertEquals("a  1\n" +
                     "boy2owdy\n" +
                     "c  3", p.render());

        p = new Tile();
        p.add(2, 2, "a\nb\nc");
        p.add(3, 4, "1\n2\n3");
        assertEquals("\n" +
                     "\n" +
                     "  a\n" +
                     "  b\n" +
                     "  c1\n" +
                     "   2\n" +
                     "   3", p.render());

        p = new Tile();
        p.add(3, 2, "a\nb\nc");
        p.add(3, 4, "1\n2\n3");
        assertEquals("\n" +
                     "\n" +
                     "   a\n" +
                     "   b\n" +
                     "   1\n" +
                     "   2\n" +
                     "   3", p.render());

        p = new Tile("zzz");
        p.add(4, 3, "a\nb\nc");
        p.add(3, 4, "1\n2\n3");
        p.add(-2, 4, "qwerty");
        assertEquals("zzz\n" +
                     "\n" +
                     "\n" +
                     "    a\n" +
                     "ertyb\n" +
                     "   2c\n" +
                     "   3", p.render());
    }
}
