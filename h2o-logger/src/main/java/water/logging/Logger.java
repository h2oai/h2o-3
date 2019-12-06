package water.logging;

public interface Logger {

    void trace(String message);

    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message);
    
    void fatal(String message);

    boolean isTraceEnabled();

    boolean isDebugEnabled();

    boolean isInfoEnabled();

    boolean isWarnEnabled();

    boolean isErrorEnabled();

    boolean isFatalEnabled();

}
