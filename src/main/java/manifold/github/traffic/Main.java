package manifold.github.traffic;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static manifold.github.traffic.AnsiColor.*;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        AnsiColor.colorize();
        if (args.isNullOrEmpty()) {
            displayUsage();
            return;
        }
        Map<Arg, String> processedArgs = processArgs(args);
        if (processedArgs == null) {
            return;
        }
        try {
            new Traffic(processedArgs).report();
        } catch (ReportedException e) {
            showError(e.getMessage(), false);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnknownHostException) {
                showError("Unknown host: ${cause.getMessage()}. Check internet connection.", false);
            } else {
                showError(e);
            }
        }
    }

    private static void displayUsage() {
        System.out.println();
        System.out.println("Displays recent statistics for a specified github repository.\n");
        System.out.println(Arg.usage());
    }

    private static Map<Arg, String> processArgs(String[] args) {
        Map<Arg, String> map = new HashMap<>();
        Arg valueArg = null;
        if (args != null) {
            for (String arg : args) {
                if (valueArg != null) {
                    map.put(valueArg, arg);
                    try {
                        valueArg.validate(arg);
                    } catch (RuntimeException re) {
                        return showError("\nError:  ${re.getMessage()}.\n");
                    }
                    valueArg = null;
                } else {
                    Arg a = Arg.byName(arg);
                    if (a != null) {
                        map.put(a, null);
                        if (!a.isFlag()) {
                            valueArg = a;
                        }
                    } else {
                        return showError("Error:  Invalid argument: $arg\n");
                    }
                }
            }
            if (valueArg != null) {
                return showError("Error:  Expecting value for ${args[args.length - 1]}");
            }
        }
        List<String> missingArgs = Arg.allRequired().stream()
                .filter(arg -> !map.containsKey(arg))
                .map(arg -> "\n  " + arg.getName() + "  '" + arg.getDescription() + "'")
                .collect(Collectors.toList());
        if( !missingArgs.isEmpty() ) {
            String errorMsg = missingArgs.stream().reduce("Error:  Missing required argument[s]:", String::concat);
            return showError(errorMsg);
        } 
        assignDefaults(map);
        return map;
    }

    private static void showError(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        showError(sw.toString());
    }
    private static Map<Arg, String> showError(String x) {
        return showError(x, true);
    }
    private static Map<Arg, String> showError(String x, boolean showUsage ) {
        System.out.println(RED + x + RESET);
        if (showUsage) {
            System.out.println(Arg.usage());
        }
        return null;
    }

    private static void assignDefaults(Map<Arg, String> map) {
        for (Arg arg : Arg.values()) {
            if (!map.containsKey(arg) && !arg.isRequired()) {
                String defaultValue = arg.getDefaultValue();
                if (defaultValue != null) {
                    map.put(arg, defaultValue);
                }
            }
        }
    }
}
