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
        assertEquals(7, JAVA_VERSION.parseMajor("1.7.0_75"));
        // Oracle JDK 8
        assertEquals(8, JAVA_VERSION.parseMajor("1.8.0_151"));
    }

    @Test
    public void testJava10AndLaterParsing() {
        // OpenJDK 9
        assertEquals(9, JAVA_VERSION.parseMajor("9"));
        // Oracle JDK 10
        assertEquals(10, JAVA_VERSION.parseMajor("10.0.2"));
    }

    @Test
    public void testNegative() {
        assertEquals(JavaVersionUtils.UNKNOWN, JAVA_VERSION.parseMajor(null));
        assertEquals(JavaVersionUtils.UNKNOWN, JAVA_VERSION.parseMajor(""));
        assertEquals(JavaVersionUtils.UNKNOWN, JAVA_VERSION.parseMajor("x"));
    }
}
