package water.logging;
import org.junit.Test;


import static org.junit.Assert.*;

public class LoggerTest {
    
    @Test
    public void factoryTest() {
        Logger logger = LoggerFactory.getLogger(LoggerTest.class);
        assertTrue(logger instanceof Slf4JLogger);

        LoggerFactory loggerFactory = new LoggerFactory("water.test.NotExistentClass","water.util.Log");
        logger = loggerFactory.getCustomLogger(LoggerTest.class);
        assertTrue(logger instanceof ConsoleLogger);

        loggerFactory = new LoggerFactory("water.test.NotExistentClass","water.test.NotExistentClass");
        logger = loggerFactory.getCustomLogger(LoggerTest.class);
        assertTrue(logger instanceof ConsoleLogger);
    }
}
