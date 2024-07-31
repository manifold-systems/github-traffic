package manifold.github.traffic;

import org.fusesource.jansi.AnsiConsole;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility supplying ANSI color code directives and methods for using Jansi
 */
public class AnsiColor {

    public static void colorize() {
        if (!isRunningIntelliJConsole()) {
            System.setProperty("jansi.colors", "256");
            AnsiConsole.systemInstall();
        }
    }

    private static boolean isRunningIntelliJConsole() {
        List<String> values = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String value : values) {
            if (value.contains("JetBrains")) {
                // crude check for running in IntelliJ console, which supports ansi color
                return true;
            }
        }
        return false;
    }

    // For stripping color chars
    static final Pattern ANSI_COLOR_PATTERN = Pattern.compile("\\u001B\\[[0-9;]*m");

    // Bold style
    public static final String BOLD = "\u001B[1m";

    // Foreground prefix
    private static final String FG = "\u001B[38;5;";
    // Background prefix
    private static final String BG = "\u001B[48;5;";

    // Foreground color codes
    public static final String RED = FG + "9m";
    public static final String COPPER = FG + "173m";
    public static final String YELLOW = FG + "227m";
    public static final String GREEN = FG + "36m";
    public static final String BLUE = FG + "33m";
    public static final String PURPLE = FG + "105m";
    public static final String GREY = FG + "247m";
    public static final String DKGREY = FG + "242m";
    public static final String WHITE = FG + "255m";
    public static final String BLACK = FG + "232m";

    // Background color codes
    static String BG_WHITE = BG + "255m";

    // Reset
    public static String RESET = "\u001B[0m";


    public static String stripColors(String string) {
        return AnsiColor.ANSI_COLOR_PATTERN.matcher(string).replaceAll("");
    }
}
