package water.util;

import water.logging.Logger;


public class WaterLogger implements Logger {
    
    
    @Override
    public void trace(String message) {
        Log.trace(message);
    }

    @Override
    public void debug(String message) {
        Log.debug(message);
    }

    @Override
    public void info(String message) {
        Log.info(message);
    }

    @Override
    public void warn(String message) {
        Log.warn(message);
    }
    
    @Override
    public void error(String message) {
        Log.err(message);
    }

    @Override
    public void fatal(String message) {
        Log.fatal(message);
    }

    @Override
    public boolean isTraceEnabled() {
        return (Log.getLogLevel() >= Log.TRACE ? true : false);
    }

    @Override
    public boolean isDebugEnabled() { return (Log.getLogLevel() >= Log.DEBUG ? true : false); }

    @Override
    public boolean isInfoEnabled() {
        return (Log.getLogLevel() >= Log.INFO ? true : false);
    }

    @Override
    public boolean isWarnEnabled() {
        return (Log.getLogLevel() >= Log.WARN ? true : false);
    }

    @Override
    public boolean isErrorEnabled() {
        return (Log.getLogLevel() >= Log.ERRR ? true : false);
    }

    @Override
    public boolean isFatalEnabled() {
        return (Log.getLogLevel() >= Log.FATAL ? true : false);
    }

}
