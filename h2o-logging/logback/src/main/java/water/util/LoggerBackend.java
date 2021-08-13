package water.util;

import org.apache.log4j.H2OPropertyConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.net.URL;
import java.util.Properties;
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
        String h2oLog4jConfiguration = System.getProperty("h2o.log4j.configuration");

        if (h2oLog4jConfiguration != null) {
            // Try to configure via a file on local filesystem
            if (new File(h2oLog4jConfiguration).exists()) {
                PropertyConfigurator.configure(h2oLog4jConfiguration);
            } else {
                // Try to load file via classloader resource (e.g., from classpath)
                URL confUrl = LoggerBackend.class.getClassLoader().getResource(h2oLog4jConfiguration);
                if (confUrl != null) {
                    PropertyConfigurator.configure(confUrl);
                }
            }
        } else {
            // Create some default properties on the fly if we aren't using a provided configuration.
            // H2O creates the log setup itself on the fly in code.
            Properties p = new Properties();
            try {
                setLog4jProperties(p);
            }
            catch (Exception e) {
                System.err.println("ERROR: failed in createLog4j, exiting now.");
                e.printStackTrace();
                return null;
            }

            // For the Hadoop case, force H2O to specify the logging setup since we don't care
            // about any hadoop log setup, anyway.
            //
            // For the Sparkling Water case, we will have inherited the log4j configuration,
            // so append to it rather than whack it.
            if (!_launchedWithHadoopJar && _haveInheritedLog4jConfiguration) {
                // Use a modified log4j property configurator to append rather than create a new log4j configuration.
                H2OPropertyConfigurator.configure(p);
            } else {
                PropertyConfigurator.configure(p);
            }
        }

        return Logger.getLogger("water.default");
    }

    private void setLog4jProperties(Properties p) {
        String patternTail = _prefix + " %10.10t %5.5p %c: %m%n";
        String pattern = "%d{MM-dd HH:mm:ss.SSS} " + patternTail;

        p.setProperty("log4j.rootLogger", L4J_LVLS[_level] + ", console");

        // H2O-wide logging
        String appenders = L4J_LVLS[_level] + ", R1, R2, R3, R4, R5, R6";
        for (String packageName : new String[] {"water", "ai.h2o", "hex"}) {
            p.setProperty("log4j.logger." + packageName, appenders);
            p.setProperty("log4j.logger.additivity." + packageName, "false");
        }

        p.setProperty("log4j.appender.console",                     "org.apache.log4j.ConsoleAppender");
        p.setProperty("log4j.appender.console.Threshold",           L4J_LVLS[_level].toString());
        p.setProperty("log4j.appender.console.layout",              "org.apache.log4j.PatternLayout");
        p.setProperty("log4j.appender.console.layout.ConversionPattern", pattern);

        p.setProperty("log4j.appender.R1",                          "org.apache.log4j.RollingFileAppender");
        p.setProperty("log4j.appender.R1.Threshold",                "TRACE");
        p.setProperty("log4j.appender.R1.File",                     _getLogFilePath.apply("trace"));
        p.setProperty("log4j.appender.R1.MaxFileSize",              "1MB");
        p.setProperty("log4j.appender.R1.MaxBackupIndex",           "3");
        p.setProperty("log4j.appender.R1.layout",                   "org.apache.log4j.PatternLayout");
        p.setProperty("log4j.appender.R1.layout.ConversionPattern", pattern);

        p.setProperty("log4j.appender.R2",                          "org.apache.log4j.RollingFileAppender");
        p.setProperty("log4j.appender.R2.Threshold",                "DEBUG");
        p.setProperty("log4j.appender.R2.File",                     _getLogFilePath.apply("debug"));
        p.setProperty("log4j.appender.R2.MaxFileSize",              _maxLogFileSize);
        p.setProperty("log4j.appender.R2.MaxBackupIndex",           "3");
        p.setProperty("log4j.appender.R2.layout",                   "org.apache.log4j.PatternLayout");
        p.setProperty("log4j.appender.R2.layout.ConversionPattern", pattern);

        p.setProperty("log4j.appender.R3",                          "org.apache.log4j.RollingFileAppender");
        p.setProperty("log4j.appender.R3.Threshold",                "INFO");
        p.setProperty("log4j.appender.R3.File",                     _getLogFilePath.apply("info"));
        p.setProperty("log4j.appender.R3.MaxFileSize",              _maxLogFileSize);
        p.setProperty("log4j.appender.R3.MaxBackupIndex",           "3");
        p.setProperty("log4j.appender.R3.layout",                   "org.apache.log4j.PatternLayout");
        p.setProperty("log4j.appender.R3.layout.ConversionPattern", pattern);

        p.setProperty("log4j.appender.R4",                          "org.apache.log4j.RollingFileAppender");
        p.setProperty("log4j.appender.R4.Threshold",                "WARN");
        p.setProperty("log4j.appender.R4.File",                     _getLogFilePath.apply("warn"));
        p.setProperty("log4j.appender.R4.MaxFileSize",              "256KB");
        p.setProperty("log4j.appender.R4.MaxBackupIndex",           "3");
        p.setProperty("log4j.appender.R4.layout",                   "org.apache.log4j.PatternLayout");
        p.setProperty("log4j.appender.R4.layout.ConversionPattern", pattern);

        p.setProperty("log4j.appender.R5",                          "org.apache.log4j.RollingFileAppender");
        p.setProperty("log4j.appender.R5.Threshold",                "ERROR");
        p.setProperty("log4j.appender.R5.File",                     _getLogFilePath.apply("error"));
        p.setProperty("log4j.appender.R5.MaxFileSize",              "256KB");
        p.setProperty("log4j.appender.R5.MaxBackupIndex",           "3");
        p.setProperty("log4j.appender.R5.layout",                   "org.apache.log4j.PatternLayout");
        p.setProperty("log4j.appender.R5.layout.ConversionPattern", pattern);

        p.setProperty("log4j.appender.R6",                          "org.apache.log4j.RollingFileAppender");
        p.setProperty("log4j.appender.R6.Threshold",                "FATAL");
        p.setProperty("log4j.appender.R6.File",                     _getLogFilePath.apply("fatal"));
        p.setProperty("log4j.appender.R6.MaxFileSize",              "256KB");
        p.setProperty("log4j.appender.R6.MaxBackupIndex",           "3");
        p.setProperty("log4j.appender.R6.layout",                   "org.apache.log4j.PatternLayout");
        p.setProperty("log4j.appender.R6.layout.ConversionPattern", pattern);

        // HTTPD logging
        p.setProperty("log4j.logger.water.api.RequestServer",       "TRACE, HTTPD");
        p.setProperty("log4j.additivity.water.api.RequestServer",   "false");

        p.setProperty("log4j.appender.HTTPD",                       "org.apache.log4j.RollingFileAppender");
        p.setProperty("log4j.appender.HTTPD.Threshold",             "TRACE");
        p.setProperty("log4j.appender.HTTPD.File",                  _getLogFilePath.apply("httpd"));
        p.setProperty("log4j.appender.HTTPD.MaxFileSize",           "1MB");
        p.setProperty("log4j.appender.HTTPD.MaxBackupIndex",        "3");
        p.setProperty("log4j.appender.HTTPD.layout",                "org.apache.log4j.PatternLayout");
        p.setProperty("log4j.appender.HTTPD.layout.ConversionPattern", "%d{ISO8601} " + patternTail);

        // Turn down the logging for some class hierarchies.
        p.setProperty("log4j.logger.org.apache.http",               "WARN");
        p.setProperty("log4j.logger.com.amazonaws",                 "WARN");
        p.setProperty("log4j.logger.org.apache.hadoop",             "WARN");
        p.setProperty("log4j.logger.org.jets3t.service",            "WARN");
        p.setProperty("log4j.logger.org.reflections.Reflections",   "ERROR");
        p.setProperty("log4j.logger.com.brsanthu.googleanalytics",  "ERROR");

        // Turn down the logging for external libraries that Orc parser depends on
        p.setProperty("log4j.logger.org.apache.hadoop.util.NativeCodeLoader", "ERROR");
    }

}
