package water.network.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class JavaVersionUtilsTest {

    @Test
    public void getMajorVersion() {
        String expectedJavaVersionSpec = System.getenv("JAVA_VERSION");
        assertNotNull(expectedJavaVersionSpec);

        int detectedVersion = JavaVersionUtils.getMajorVersion();

        assertEquals(Integer.parseInt(expectedJavaVersionSpec), detectedVersion);
    }

}
