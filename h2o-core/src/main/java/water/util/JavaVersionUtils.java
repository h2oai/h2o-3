package water.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum JavaVersionUtils {
    JAVA_VERSION;

    public static final int UNKNOWN = -1;

    private int majorVersion;

    private JavaVersionUtils() {
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
            Pattern p = Pattern.compile("1\\.([0-9]*).*|([0-9][0-9]*).*");
            Matcher m = p.matcher(version);
            boolean b = m.matches();
            if(b) {
                return Integer.parseInt(m.group(m.group(1)!=null?1:2));
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
