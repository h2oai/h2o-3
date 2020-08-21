package water.util;
import org.junit.Test;

import static org.junit.Assert.*;
import water.logging.Logger;
import water.logging.LoggerFactory;

public class WaterLoggerTest {

        @Test
        public void factoryTest() {
            Logger logger = LoggerFactory.getLogger(WaterLoggerTest.class);
            assertTrue(logger instanceof WaterLogger);
        }
}
