package water;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ClearSystemProperties;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;
import water.util.JavaVersionUtils;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(Suite.class)
@Suite.SuiteClasses({JavaVersionSupportTest.SupportedJavasTest.class, JavaVersionSupportTest.UnsupportedJavasTest.class})
public class JavaVersionSupportTest {

    @BeforeClass
    public static void beforeClass() throws Exception {
        Assume.assumeTrue(System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.allowJavaVersions") == null);
    }

    @RunWith(Parameterized.class)
    public static class SupportedJavasTest {
        private static int originalJavaVersion;

        @BeforeClass
        public static void beforeClass() throws Exception {
            originalJavaVersion = JavaVersionUtils.JAVA_VERSION.getMajor();
        }

        @Parameterized.Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][]{
                    {8}, {9}, {10}, {11}, {12}, {13}, {14}, {15}, {16}, {17}, {21}
            });
        }

        @Parameterized.Parameter
        public int javaVersion;

        @Rule
        public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

        @Test
        public void testJavaVersionSupported() throws Exception {
            final Field majorVersion = JavaVersionUtils.class.getDeclaredField("majorVersion");
            try {
                majorVersion.setAccessible(true);
                majorVersion.setInt(JavaVersionUtils.JAVA_VERSION, javaVersion);
                assertTrue(JavaVersionSupport.runningOnSupportedVersion());
            } finally {
                try {
                    majorVersion.setInt(JavaVersionUtils.JAVA_VERSION, originalJavaVersion);
                } finally {
                    majorVersion.setAccessible(false);
                }
            }
        }
    }

    @RunWith(Parameterized.class)
    public static class UnsupportedJavasTest {
        private static int originalJavaVersion;

        @Rule
        public RestoreSystemProperties systemProperties = new RestoreSystemProperties();

        @BeforeClass
        public static void beforeClass() throws Exception {
            originalJavaVersion = JavaVersionUtils.JAVA_VERSION.getMajor();
        }

        @Parameterized.Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][]{
                    {7}, {22}
            });
        }

        @Parameterized.Parameter
        public int unsupportedJavaVersion;

        @Rule
        public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

        @Test
        public void testJavaVersionSupported() throws Exception {
            final Field majorVersion = JavaVersionUtils.class.getDeclaredField("majorVersion");
            try {
                majorVersion.setAccessible(true);
                majorVersion.setInt(JavaVersionUtils.JAVA_VERSION, unsupportedJavaVersion);
                assertFalse(JavaVersionSupport.runningOnSupportedVersion());
            } finally {
                try {
                    majorVersion.setInt(JavaVersionUtils.JAVA_VERSION, originalJavaVersion);
                } finally {
                    majorVersion.setAccessible(false);
                }
            }
        }


        /**
         * Unsupported Javas, explicitly enabled by the user are tested here
         */
        @Test
        public void testUnsupportedUserEnabled() throws Exception{
            final Field majorVersion = JavaVersionUtils.class.getDeclaredField("majorVersion");
            try {
                majorVersion.setAccessible(true);
                majorVersion.setInt(JavaVersionUtils.JAVA_VERSION, unsupportedJavaVersion);
                System.setProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.allowJavaVersions", String.valueOf(unsupportedJavaVersion));
                assertTrue(JavaVersionSupport.runningOnSupportedVersion());
            } finally {
                try {
                    majorVersion.setInt(JavaVersionUtils.JAVA_VERSION, originalJavaVersion);
                } finally {
                    majorVersion.setAccessible(false);
                }
            }
        }
    }
}
