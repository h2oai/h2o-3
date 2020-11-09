package water;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;
import water.util.JavaVersionUtils;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(Suite.class)
@Suite.SuiteClasses({JavaTest.SupportedJavasTest.class, JavaTest.UnsupportedJavasTest.class, JavaTest.SupportedVersionsRangeTest.class})
public class JavaTest {

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
                    {8}, {9}, {10}, {11}, {12}, {13}, {14}, {15}
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
                assertTrue(Java.runningOnSupportedVersion());
            } finally {
                try {
                    majorVersion.setInt(JavaVersionUtils.JAVA_VERSION, originalJavaVersion);
                } finally {
                    majorVersion.setAccessible(false);
                }
            }
        }

        @Test
        public void testGetSupportedJavaVersions() {
            final Set<Integer> supportedJavaVersions = Java.getSupportedJavaVersions();
            assertTrue(supportedJavaVersions.contains(javaVersion));
        }
    }

    @RunWith(Parameterized.class)
    public static class UnsupportedJavasTest {
        private static int originalJavaVersion;

        @BeforeClass
        public static void beforeClass() throws Exception {
            originalJavaVersion = JavaVersionUtils.JAVA_VERSION.getMajor();
        }

        @Parameterized.Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][]{
                    {7}, {16}
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
                assertFalse(Java.runningOnSupportedVersion());
            } finally {
                try {
                    majorVersion.setInt(JavaVersionUtils.JAVA_VERSION, originalJavaVersion);
                } finally {
                    majorVersion.setAccessible(false);
                }
            }
        }

        @Test
        public void testGetSupportedJavaVersions() {
            final Set<Integer> supportedJavaVersions = Java.getSupportedJavaVersions();
            assertFalse(supportedJavaVersions.contains(unsupportedJavaVersion));
        }
    }
    
    
    public static class SupportedVersionsRangeTest{
        @Test
        public void testGetSupportedJavaVersions() {
            final Set<Integer> supportedJavaVersions = Java.getSupportedJavaVersions();
            assertEquals((Java.MAX_SUPPORTED_JAVA_VERSION  - Java.MIN_SUPPORTED_JAVA_VERSION + 1) - Java.UNSUPPORTED_VERSIONS.size(), supportedJavaVersions.size());
            for (int version = Java.MIN_SUPPORTED_JAVA_VERSION; version <= Java.MAX_SUPPORTED_JAVA_VERSION ; version++) {
                if(Java.UNSUPPORTED_VERSIONS.contains(version)){
                    assertFalse(supportedJavaVersions.contains(version));
                } else {
                    supportedJavaVersions.contains(version);
                }
            }
        }

        @Test
        public void testSupportedJavaVersionsAscendingOrder() {
            final Set<Integer> supportedJavaVersions = Java.getSupportedJavaVersions();
            int previousVersion = Integer.MIN_VALUE;
            for (Integer version : supportedJavaVersions){
                assertTrue(version > previousVersion);
                previousVersion = version;
            }
        }
    }


}
