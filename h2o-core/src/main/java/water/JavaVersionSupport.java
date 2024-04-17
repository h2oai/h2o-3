package water;

import static water.util.JavaVersionUtils.JAVA_VERSION;

public class JavaVersionSupport {
    // Notes: 
    // Inclusive interval for supported Java versions.
    // Make sure that the following is logically consistent with whitelist in R code - see function .h2o.check_java_version in connection.R.
    // Upgrade of the javassist library should be considered when adding support for a new java version.
    public static final int MIN_SUPPORTED_JAVA_VERSION = 8;
    public static final int MAX_SUPPORTED_JAVA_VERSION = 21;

    /**
     * Checks for the version of Java this instance of H2O was ran with and compares it with supported versions.
     *
     * @return True if the instance of H2O is running on supported JVM, otherwise false.
     */
    public static boolean runningOnSupportedVersion() {
        return JAVA_VERSION.isKnown() && (isUserEnabledJavaVersion() || isSupportedVersion());
    }

    private static boolean isSupportedVersion() {
        return JAVA_VERSION.getMajor() >= MIN_SUPPORTED_JAVA_VERSION && JAVA_VERSION.getMajor() <= MAX_SUPPORTED_JAVA_VERSION;
    }

    /**
     * @return True if the Java version this instance of H2O is currently running on has been explicitly whitelisted by
     * the user.
     */
    private static boolean isUserEnabledJavaVersion() {
        final String extraJavaVersionsStr = System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.allowJavaVersions");
        if (extraJavaVersionsStr == null || extraJavaVersionsStr.isEmpty()) {
            return false;
        }
        final String[] splitVersions = extraJavaVersionsStr.split(",");
        for (final String version : splitVersions) {
            final int majorVersion = Integer.valueOf(version);
            if (JAVA_VERSION.getMajor() == majorVersion) {
                return true;
            }
        }
        return false;
    }

    public static String describeSupportedVersions() {
        return MIN_SUPPORTED_JAVA_VERSION + "-" + MAX_SUPPORTED_JAVA_VERSION;
    }

}
