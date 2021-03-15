package water.util;

public class WaterLogger  {
    public void trace(String message) { Log.trace(message);  }
    public void debug(String message) { Log.debug(message);  }
    public void info (String message) { Log.info(message);   }
    public void warn (String message) { Log.warn(message);   }
    public void error(String message) { Log.err(message);    }
    public void fatal(String message) { Log.fatal(message);  }
    public boolean isTraceEnabled() { return (Log.getLogLevel() >= Log.TRACE ? true : false); }
    public boolean isDebugEnabled() { return (Log.getLogLevel() >= Log.DEBUG ? true : false); }
    public boolean isInfoEnabled () { return (Log.getLogLevel() >= Log.INFO  ? true : false); }
    public boolean isWarnEnabled () { return (Log.getLogLevel() >= Log.WARN  ? true : false); }
    public boolean isErrorEnabled() { return (Log.getLogLevel() >= Log.ERRR  ? true : false); }
    public boolean isFatalEnabled() { return (Log.getLogLevel() >= Log.FATAL ? true : false); }
}
