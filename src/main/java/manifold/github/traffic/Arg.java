package manifold.github.traffic;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command line arguments for this application.
 */
public enum Arg {
    /**
     * Github user name
     */
    user("-user", true, false, null, "Github user/org name") {
        @Override
        public void validate(String value) {
            if (value == null || value.isEmpty()) {
                throw new RuntimeException(
                        "Argument: '${getName()}' requires a valid github user name, but was: $value");
            }
        }
    },
    /**
     * Github repository name
     */
    repo("-repo", true, false, null, "Github repository name") {
        @Override
        public void validate(String value) {
            if (value == null || value.isEmpty()) {
                throw new RuntimeException(
                        "Argument: '${getName()}' requires a valid github repository name, but was: $value");
            }
        }
    },
    /**
     * Github authorization token
     */
    token("-token", true, false, null, "Github authorization token") {
        @Override
        public void validate(String value) {
        }
    },
    /**
     * Number of days to display, default is 14
     */
    days("-days", false, false, "14", "Number of days to display. Values may range from 1..14. Default is 14.") {
        @Override
        public void validate(String value) {
            int days = Integer.parseInt(value);
            if (days < 1 || days > 14) {
                throw new RuntimeException(
                        "Argument: '${getName()}' must be >=1 and <= 14, but was $value");
            }
        }
    };

    private final String _name;
    private final boolean _required;
    private final boolean _isFlag;
    private final String _description;
    private final String _defaultValue;

    Arg(String name, boolean required, boolean isFlag, String defaultValue, String description) {
        _name = name;
        _required = required;
        _isFlag = isFlag;
        _description = description;
        _defaultValue = defaultValue;
    }

    public String getName() {
        return _name;
    }

    public String getDescription() {
        return _description;
    }

    public String getDefaultValue() {
        return _defaultValue;
    }

    public boolean isRequired() {
        return _required;
    }

    public boolean isFlag() {
        return _isFlag;
    }

    public static Set<Arg> allRequired() {
        return values().stream().filter(Arg::isRequired).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<Arg> allOptional() {
        return values().stream().filter(a -> !a.isRequired()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Arg byName(String name) {
        return values().stream()
                .filter(e -> e.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public abstract void validate(String arg);

    static String usage() {
        StringBuilder sb = new StringBuilder("--Usage--\n");
        sb.append("Required parameters:\n");
        for (Arg arg : allRequired()) {
            sb.append("  -$arg: ${arg.getDescription()}\n");
        }
        sb.append("Optional parameters:\n");
        for (Arg arg : allOptional()) {
            sb.append("  -$arg: ${arg.getDescription()}\n");
        }
        sb.append("Example:\n")
                .append("  traffic -user joeuser -repo joeswidget -token xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n");
        return sb.toString();
    }
}
