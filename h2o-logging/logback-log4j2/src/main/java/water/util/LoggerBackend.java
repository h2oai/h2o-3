package water.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.util.function.Function;

public class LoggerBackend {

    public static final Level[] L4J_LVLS = { Level.FATAL, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE };

    public int _level;
    public String _prefix;
    public String _maxLogFileSize;
    public boolean _launchedWithHadoopJar;
    public boolean _haveInheritedLog4jConfiguration;
    public Function<String, String> _getLogFilePath;

    public Logger createLog4j() {
        return Logger.getLogger("water.default");
    }

}
