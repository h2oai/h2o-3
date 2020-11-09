package water;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static water.util.JavaVersionUtils.JAVA_VERSION;

public class Java {
    // Notes: 
    // Interval for supported Java versions, inclusive
    // - make sure that the following is logically consistent with whitelist in R code - see function .h2o.check_java_version in connection.R
    // - upgrade of the javassist library should be considered when adding support for a new java version
    public static final int MIN_SUPPORTED_JAVA_VERSION = 8;
    public static final int MAX_SUPPORTED_JAVA_VERSION = 15;
    public static final Set<Integer> UNSUPPORTED_VERSIONS = Collections.emptySet(); // Specific unsupported versions


    /**
     * Checks for the version of Java this instance of H2O was ran with and compares it with supported versions.
     *
     * @return True if the instance of H2O is running on supported JVM, otherwise false.
     */
    public static boolean runningOnSupportedVersion() {
        return JAVA_VERSION.isKnown() && !isUserEnabledJavaVersion() && isSupportedVersion();
    }

    private static boolean isSupportedVersion() {
        return !UNSUPPORTED_VERSIONS.contains(JAVA_VERSION.getMajor())
                && JAVA_VERSION.getMajor() >= MIN_SUPPORTED_JAVA_VERSION && JAVA_VERSION.getMajor() <= MAX_SUPPORTED_JAVA_VERSION;
    }

    /**
     * @return True if the Java version this instance of H2O is currently running on has been explicitly whitelisted by
     * the user.
     */
    private static boolean isUserEnabledJavaVersion() {
        String extraJavaVersionsStr = System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.allowJavaVersions");
        if (extraJavaVersionsStr == null || extraJavaVersionsStr.isEmpty())
            return false;
        String[] vs = extraJavaVersionsStr.split(",");
        for (String v : vs) {
            int majorVersion = Integer.valueOf(v);
            if (JAVA_VERSION.getMajor() == majorVersion) {
                return true;
            }
        }
        return false;
    }
    
    public static Set<Integer> getSupportedJavaVersions() {
        final Set<Integer> versions = new TreeSet();
        for (int version = MIN_SUPPORTED_JAVA_VERSION; version <= MAX_SUPPORTED_JAVA_VERSION; version++) {
            if (!UNSUPPORTED_VERSIONS.contains(version)) {
                versions.add(version);
            }
        }
        return versions;
    }

}
