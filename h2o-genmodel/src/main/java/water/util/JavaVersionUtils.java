package water.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum JavaVersionUtils {
    JAVA_VERSION;

    public static final int UNKNOWN = -1;

    private int majorVersion;

    JavaVersionUtils() {
        String javaVersion = System.getProperty("java.version");
        majorVersion = parseMajor(javaVersion);
    }

    public int getMajor() {
        return majorVersion;
    }

    public boolean isKnown() {
        return majorVersion!=UNKNOWN;
    }

    public int parseMajor(String version) {
        if(version!=null) {
            final Pattern pattern = Pattern.compile("1\\.([0-9]*).*|([0-9][0-9]*).*");
            final Matcher matcher = pattern.matcher(version);
            if(matcher.matches()) {
                return Integer.parseInt(matcher.group(matcher.group(1)!=null?1:2));
            }
        }
        return UNKNOWN;
    }

    /**
     *
     * @return True if current Java version uses unified logging (JEP 158), otherwise false.
     */
    public boolean useUnifiedLogging(){
        // Unified logging enabled since version 9, enforced in version 10.
        return isKnown() && getMajor() >= 9;
    }
}
