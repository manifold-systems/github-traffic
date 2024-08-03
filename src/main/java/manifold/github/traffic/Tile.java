package manifold.github.traffic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static manifold.github.traffic.Tile.Layout.*;

/**
 * Tile simplifies rendering blocks of text similar to a newspaper or magazine layout. It is designed with console use
 * in mind.
 * <p/>
 * A tile may contain text, margins, a layout, and child tiles. Child tiles are sequential and render horizontally as a
 * row, vertically as a column, or manually by coordinate, according to the assigned layout. Tiles may be nested to any
 * depth.
 */
public class Tile {
    private final List<String> _lines;
    private final Layout _layout;
    private final Margin _margin;
    private final List<Tile> _nest;
    private Tile _parent;
    private int _x; // horizontal offset within parent
    private int _y; // vertical offset within parent

    public Tile() {
        this(Manual, Margin.Empty);
    }

    public Tile(String content) {
        this(content, Manual, Margin.Empty);
    }

    public Tile(Layout layout, Margin margin) {
        this("", layout, margin);
    }

    public Tile(String content, Margin margin) {
        this(content, Manual, margin);
    }

    public Tile(String content, Layout layout, Margin margin) {
        _lines = content.lines().toList();
        _nest = new ArrayList<>();
        _layout = layout;
        _margin = margin;
    }

    public void append(String content) {
        append(content, Margin.Empty);
    }

    public void append(String content, Margin margin) {
        append(new Tile(content, Manual, margin));
    }

    public void append(Tile tile) {
        if (_layout == Manual) {
            throw new RuntimeException("Append not allowed in '$_layout' layout, " +
                                       "use x,y coordinates with add(), or use a different layout");
        }
        _layout.append(this, tile);
    }

    public void add(int x, int y, String content) {
        add(x, y, new Tile(content, Manual, Margin.Empty));
    }

    public void add(int x, int y, String tile, Margin margin) {
        add(x, y, new Tile(tile, Manual, margin));
    }

    public void add(int x, int y, Tile tile) {
        if (_layout != Manual) {
            throw new RuntimeException("x,y positioning not allowed in '$_layout' layout, " +
                                       "use append() instead, or use a different layout");
        }
        _add(x, y, tile);
    }

    private void _add(int x, int y, Tile tile) {
        tile._x = x;
        tile._y = y;
        _nest.add(tile);
        tile._parent = this;
    }
    
    public String render() {
        String result = render(_lines, 0, 0, "");
        for (Tile child : _nest) {
            result = render(child.render().lines().toList(), child._x, child._y, result);
        }
        return includeMargin(result);
    }

    private String render(List<String> lines, int x, int y, String background) {
        StringBuilder result = new StringBuilder();
        List<String> bgLines = new ArrayList<>(background.lines().toList());
        List<String> itLines = new ArrayList<>(lines);
        for (int i = y; i < 0; i++) {
            itLines.remove(0);
        }
        while (bgLines.size() < y) {
            bgLines.add("");
        }
        for (int i = 0; i < bgLines.size(); i++) {
            String bgLine = bgLines.get(i);
            result.append(bgLine);
            if (!itLines.isEmpty() && i >= y) {
                String itLine = itLines.remove(0);
                if (x >= length(bgLine)) {
                    result.append(" ".repeat(x - length(bgLine))).append(itLine);
                } else if (x < 0) {
                    //todo: handle overwriting text having ansi color codes, prob remove these chars and make map of location/code and then fixup result accordingly because we know the layout of the tiles.
                    int lineStart = result.length() - bgLine.length();
                    result.replace(lineStart, lineStart + itLine.length() + x, itLine.substring(-x));
                } else {
                    //todo: handle overwriting text having ansi color codes...
                    int offset = result.length() - bgLine.length() + x;
                    result.replace(offset, offset + itLine.length(), itLine);
                }
            }
            result.append('\n');
        }
        for (String itLine : itLines) {
            if (x < 0) {
                result.append(itLine.substring(-x)).append('\n');
            } else {
                result.append(" ".repeat(x)).append(itLine).append('\n');
            }
        }
        removeTerminatingNewline(result);
        return result.toString();
    }

    private static void removeTerminatingNewline(StringBuilder result) {
        if (result.length() != 0 && result.charAt(result.length() - 1) == '\n') {
            result.deleteCharAt(result.length() - 1);
        }
    }

    private String includeMargin(String result) {
        List<String> lines = new ArrayList<>(result.lines().toList());
        if (lines.isEmpty() || _margin.equals(Margin.Empty)) {
            return result;
        }
        int maxLength = length(lines.stream().max(Comparator.comparingInt(this::length)).get());
//        if (_margin.left != 0 || _margin.right != 0) {
//            String leftMargin = _margin.left > 0 ? ("│" + spaces(_margin.left - 1)) : "";
//            String rightMargin = _margin.right > 0 ? (spaces(_margin.right - 1) + "│") : "";
//            for (int i = 0; i < lines.size(); i++) {
//                String line = lines.get(i);
//                lines.set(i, leftMargin + line + spaces(maxLength - length(line)) + rightMargin);
//            }
//        }
        maxLength += _margin.left + _margin.right;
        for (int i = 0; i < _margin.top; i++) {
//            String line = (_margin.left > 0 ? "╭" : "─") + "─".repeat(maxLength-2) + (_margin.right > 0 ? "╮" : "─");
            String line = " ".repeat(maxLength);
            lines.add(0, line);
        }
        for (int i = 0; i < _margin.bottom; i++) {
//            String line = (_margin.left > 0 ? "╰" : "─") + "─".repeat(maxLength-2) + (_margin.right > 0 ? "╯" : "─");
            String line = " ".repeat(maxLength);
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    private String spaces(int n) {
        return n <= 0 ? "" : " ".repeat(n);
    }

    /**
     * ANSI color codes are not counted
     */
    private int length(String line) {
        return AnsiColor.stripColors(line).length();
    }

    public int width() {
        int maxWidth = 0;
        for (String line : _lines) {
            maxWidth = Math.max(maxWidth, length(line));
        }
        maxWidth = Math.max(maxWidth, childWidth());
        return _margin.left + maxWidth + _margin.right;
    }

    public int height() {
        int maxHeight = _lines.size();
        maxHeight = Math.max(maxHeight, childHeight());
        return _margin.top + maxHeight + _margin.bottom;
    }

    private int childWidth() {
        if (_nest.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (Tile child : _nest) {
            width = Math.max(width, child._x + child.width());
        }
        return width;
    }

    private int childHeight() {
        if (_nest.isEmpty()) {
            return 0;
        }
        int height = 0;
        for (Tile child : _nest) {
            height = Math.max(height, child._y + child.height());
        }
        return height;
    }

    public enum Layout {
        Row {
            @Override
            void append(Tile parent, Tile child) {
                parent._add(parent.childWidth(), 0, child);
            }
        },
        Column {
            @Override
            void append(Tile parent, Tile child) {
                parent._add(0, parent.childHeight(), child);
            }
        },
        Manual {
            @Override
            void append(Tile parent, Tile child) {
                throw new IllegalStateException("append() cannot be called here.");
            }
        };

        abstract void append(Tile parent, Tile child);
    }

    public static class Margin {
        static final Margin Empty = new Margin(0, 0, 0, 0);
        private final int top;
        private final int left;
        private final int bottom;
        private final int right;

        public Margin(int top, int left, int bottom, int right) {
            this.top = top;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
        }
    }
}
