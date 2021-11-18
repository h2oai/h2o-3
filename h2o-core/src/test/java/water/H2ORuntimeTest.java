package water;

import org.junit.Test;

import static org.junit.Assert.*;

public class H2ORuntimeTest {

    @Test
    public void availableProcessors() {
        int actualAvailable = Runtime.getRuntime().availableProcessors();
        assertEquals(actualAvailable, H2ORuntime.availableProcessors(actualAvailable));
        assertEquals(actualAvailable, H2ORuntime.availableProcessors(0));
        if (actualAvailable > 1) {
            assertEquals(actualAvailable - 1, H2ORuntime.availableProcessors(actualAvailable - 1));
        }
        assertEquals(1, H2ORuntime.availableProcessors(1));
    }

    @Test
    public void getActiveProcessorCount() {
        String oldValue = System.getProperty("sys.ai.h2o.activeProcessorCount");
        try {
            System.setProperty("sys.ai.h2o.activeProcessorCount", "42");
            assertEquals(42, H2ORuntime.getActiveProcessorCount());
        } finally {
            if (oldValue == null)
                System.clearProperty("sys.ai.h2o.activeProcessorCount");
            else 
                System.setProperty("sys.ai.h2o.activeProcessorCount", oldValue);
        }
    }
}
