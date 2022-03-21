package water.network.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaVersionUtils {
    private static final int UNKNOWN = -1;

    public static int getMajorVersion() {
        return parseMajor(System.getProperty("java.version"));
    }

    private static int parseMajor(String version) {
        if (version!=null) {
            final Pattern pattern = Pattern.compile("1\\.([0-9]*).*|([0-9][0-9]*).*");
            final Matcher matcher = pattern.matcher(version);
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group(matcher.group(1)!=null?1:2));
            }
        }
        return UNKNOWN;
    }

}
