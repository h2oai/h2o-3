package water.logging;


public class LoggerFactory {
    private static final String DEFAULT_SLF4J_CLASS_TO_CHECK = "org.slf4j.LoggerFactory";
    private static final String DEFAULT_WATERLOG_CLASS_TO_CHECK = "water.util.Log";
    private static final LoggerFactory INSTANCE = new LoggerFactory(DEFAULT_SLF4J_CLASS_TO_CHECK, DEFAULT_WATERLOG_CLASS_TO_CHECK);
    
    private final String slf4jClassName;
    private final boolean isSlf4JAvailable; 
    private final String waterLogClassName;
    private final boolean isWaterLogAvailable;
    private final Logger waterLogger;

    LoggerFactory(String slf4jClass, String waterLogClass) {
        slf4jClassName = slf4jClass;
        waterLogClassName = waterLogClass;
        isSlf4JAvailable = isSlf4JAvailable();
        isWaterLogAvailable = isWaterLogAvailable();
        waterLogger = (isWaterLogAvailable) ? tryToGetWaterLogger() : null;
    }
    
    /**
     * Returns new logger for each invocation. Logger logs to water.util.Log / SLF4J / console depending on whether water.util.Log / SLF4J is available.
     *
     * @param clazz class from which getLogger() is called
     * @return WaterLogger (water.util.Log adapter) if water.util.Log is on the classpath, else Slf4JLogger (SLF4J adapter) if SLF4J is on the classpath else ConsoleLogger 
     */
    public static Logger getLogger(Class<?> clazz) {
        return INSTANCE.getCustomLogger(clazz);
    }

    
    /**
     * Returns new logger for each invocation.  Logger logs to water.util.Log / SLF4J / console depending on whether water.util.Log / SLF4J is available.
     *
     * @param clazz class from which getLogger() is called
     * @return SWaterLogger (water.util.Log adapter) if water.util.Log is on the classpath, else Slf4JLogger (SLF4J adapter) if SLF4J is on the classpath else ConsoleLogger
     */
    public Logger getCustomLogger(Class<?> clazz) {
        if (isWaterLogAvailable && waterLogger != null) {
            return waterLogger;
        } else if (isSlf4JAvailable) {
            return new Slf4JLogger(clazz);
        } else {
            return new ConsoleLogger();
        }
    }
    
    private Logger tryToGetWaterLogger() {
        try {
            return (Logger) Class.forName("water.util.WaterLogger").newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            return null;
        }
    }
    
    /**
     * Checks whether SLF4J is on the classpath.
     *
     * @return true if SLF4J is on the classpath, false if not.
     */
    private boolean isSlf4JAvailable() {
        try {
            Class.forName(slf4jClassName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks whether water.util.Log is on the classpath.
     *
     * @return true if water.util.Log is on the classpath, false if not.
     */
    private boolean isWaterLogAvailable() {
        try {
            Class.forName(waterLogClassName);
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }
}
