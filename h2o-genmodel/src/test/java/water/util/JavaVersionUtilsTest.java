package water.util;

import static org.junit.Assert.assertEquals;

import static water.util.JavaVersionUtils.JAVA_VERSION;

import org.junit.Test;

/**
 * Java version string parser test.
 */
public class JavaVersionUtilsTest {

    @Test
    public void testPreJava10Parsing() {
        // OpenJDK 7
        assertEquals(7, JavaVersionUtils.parseMajor("1.7.0_75"));
        // Oracle JDK 8
        assertEquals(8, JavaVersionUtils.parseMajor("1.8.0_151"));
    }

    @Test
    public void testJava10AndLaterParsing() {
        // OpenJDK 9
        assertEquals(9, JavaVersionUtils.parseMajor("9"));
        // Oracle JDK 10
        assertEquals(10, JavaVersionUtils.parseMajor("10.0.2"));
    }

    @Test
    public void testNegative() {
        assertEquals(JavaVersionUtils.UNKNOWN, JavaVersionUtils.parseMajor(null));
        assertEquals(JavaVersionUtils.UNKNOWN, JavaVersionUtils.parseMajor(""));
        assertEquals(JavaVersionUtils.UNKNOWN, JavaVersionUtils.parseMajor("x"));
    }

    @Test
    public void testGetVerboseGCFlag() {
        // Java 8
        assertEquals("-verbose:gc", JavaVersionUtils.JAVA_8.getVerboseGCFlag());
        // Java 9 and newer
        assertEquals("-Xlog:gc=info", JavaVersionUtils.JAVA_9.getVerboseGCFlag());
        if (JAVA_VERSION.useUnifiedLogging()) {
            assertEquals(JavaVersionUtils.JAVA_9.getVerboseGCFlag(), JAVA_VERSION.getVerboseGCFlag());
        } else {
            assertEquals(JavaVersionUtils.JAVA_8.getVerboseGCFlag(), JAVA_VERSION.getVerboseGCFlag());
        }
    }


}
