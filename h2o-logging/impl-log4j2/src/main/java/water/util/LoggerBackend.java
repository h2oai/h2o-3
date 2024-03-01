package water.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class LoggerBackend {

    public static final Level[] L4J_LVLS = { Level.FATAL, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE };
    public static final org.apache.logging.log4j.Level[] L4J_LOGGING_LVLS = {
            org.apache.logging.log4j.Level.FATAL,
            org.apache.logging.log4j.Level.ERROR,
            org.apache.logging.log4j.Level.WARN,
            org.apache.logging.log4j.Level.INFO,
            org.apache.logging.log4j.Level.DEBUG,
            org.apache.logging.log4j.Level.TRACE
    };

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
            File file = new File(h2oLog4jConfiguration);
            if (file.exists()) {
                Configurator.reconfigure(file.toURI());
            } else {
                // Try to load file via classloader resource (e.g., from classpath)
                URL confUrl = LoggerBackend.class.getClassLoader().getResource(h2oLog4jConfiguration);
                if (confUrl != null) {
                    try {
                        Configurator.reconfigure(confUrl.toURI());
                    } catch (URISyntaxException e) {
                        System.err.println("ERROR: failed in createLog4j, exiting now.");
                        e.printStackTrace();
                        return null;
                    }
                }
            }
        } else {
            try {
                reconfigureLog4J();
            } catch (Exception e) {
                System.err.println("ERROR: failed in createLog4j, exiting now.");
                e.printStackTrace();
                return null;
            }

            // TODO: hadoop and sparkling water cases
        }
        return Logger.getLogger("water.default");
    }

    public void reconfigureLog4J() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(L4J_LOGGING_LVLS[_level]);
        builder.setConfigurationName("H2OLogConfiguration");
        
        // configure appenders:
        String patternTail = _prefix + " %10.10t %5.5p %c: %m%n";
        String pattern = "%d{MM-dd HH:mm:ss.SSS} " + patternTail;

        LayoutComponentBuilder layoutComponentBuilder = builder.newLayout("PatternLayout").addAttribute("pattern", pattern);

        builder.add(builder.newAppender("Console", "Console")
                .addAttribute("target", "SYSTEM_OUT")
                .add(layoutComponentBuilder));

        builder.add(builder.newAppender("stderr", "Console")
                .addAttribute("target", "SYSTEM_ERR")
                .add(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.DENY).addAttribute("level", Level.ERROR))
                .add(layoutComponentBuilder));

        builder.add(newRollingFileAppenderComponent(builder, "R1", "1MB", _getLogFilePath.apply("trace"), pattern, Level.TRACE));
        builder.add(newRollingFileAppenderComponent(builder, "R2", _maxLogFileSize, _getLogFilePath.apply("debug"), pattern, Level.DEBUG));
        builder.add(newRollingFileAppenderComponent(builder, "R3", _maxLogFileSize, _getLogFilePath.apply("info"), pattern, Level.INFO));
        builder.add(newRollingFileAppenderComponent(builder, "R4", "256KB", _getLogFilePath.apply("warn"), pattern, Level.WARN));
        builder.add(newRollingFileAppenderComponent(builder, "R5", "256KB", _getLogFilePath.apply("error"), pattern, Level.ERROR));
        builder.add(newRollingFileAppenderComponent(builder, "R6", "256KB", _getLogFilePath.apply("fatal"), pattern, Level.FATAL));
        builder.add(newRollingFileAppenderComponent(builder, "HTTPD", "1MB", _getLogFilePath.apply("httpd"), "%d{ISO8601} " + patternTail, Level.TRACE));

        AppenderRefComponentBuilder consoleAppenderRef = builder.newAppenderRef("Console");
        AppenderRefComponentBuilder stderrAppenderRef = builder.newAppenderRef("stderr");
        
        // configure loggers:
        List<AppenderRefComponentBuilder> appenderReferences = new ArrayList();
        appenderReferences.add(builder.newAppenderRef("R1"));
        appenderReferences.add(builder.newAppenderRef("R2"));
        appenderReferences.add(builder.newAppenderRef("R3"));
        appenderReferences.add(builder.newAppenderRef("R4"));
        appenderReferences.add(builder.newAppenderRef("R5"));
        appenderReferences.add(builder.newAppenderRef("R6"));
        appenderReferences.add(consoleAppenderRef);
        appenderReferences.add(stderrAppenderRef);
        
        builder.add(newLoggerComponent(builder, "hex", appenderReferences));
        builder.add(newLoggerComponent(builder, "water", appenderReferences));
        builder.add(newLoggerComponent(builder, "ai.h2o", appenderReferences));
        builder.add(builder.newRootLogger(String.valueOf(L4J_LVLS[_level])).add(consoleAppenderRef).add(stderrAppenderRef));

        // Turn down the logging for some class hierarchies.
        builder.add(newLoggerComponent(builder, "org.apache.http", appenderReferences, "WARN"));
        builder.add(newLoggerComponent(builder, "com.amazonaws", appenderReferences, "WARN"));
        builder.add(newLoggerComponent(builder, "org.apache.hadoop", appenderReferences, "WARN"));
        builder.add(newLoggerComponent(builder, "org.jets3t.service", appenderReferences, "WARN"));
        builder.add(newLoggerComponent(builder, "org.reflections.Reflections", appenderReferences, "ERROR"));
        builder.add(newLoggerComponent(builder, "com.brsanthu.googleanalytics", appenderReferences, "ERROR"));
        // Turn down the logging for external libraries that Orc parser depends on-->
        builder.add(newLoggerComponent(builder, "org.apache.hadoop.util.NativeCodeLoader", appenderReferences, "ERROR"));

        // HTTPD logging
        appenderReferences = new ArrayList();
        appenderReferences.add(builder.newAppenderRef("HTTPD"));
        builder.add(newLoggerComponent(builder, "water.api.RequestServer", appenderReferences));

        Configurator.reconfigure(builder.build());
    }

    AppenderComponentBuilder newRollingFileAppenderComponent(ConfigurationBuilder builder, String name, String sizeBasedTriggeringPolicyValue,  String fileNameValue, String filePatternValue, Level thresholdFilterLevel) {
        ComponentBuilder triggeringPolicy = builder.newComponent("Policies")
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", sizeBasedTriggeringPolicyValue));
        LayoutComponentBuilder layoutBuilder = builder.newLayout("PatternLayout")
                .addAttribute("pattern", filePatternValue);
        FilterComponentBuilder thresholdFilter = builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.DENY)
                .addAttribute("level", thresholdFilterLevel.toString());
        ComponentBuilder rolloverStrategy = builder.newComponent("DefaultRolloverStrategy").addAttribute("max", 3);

        AppenderComponentBuilder appenderBuilder = builder.newAppender(name, "RollingFile")
                .addAttribute("fileName", fileNameValue)
                .addAttribute("filePattern", fileNameValue.concat(".%i"))
                .add(thresholdFilter)
                .addComponent(triggeringPolicy)
                .addComponent(layoutBuilder)
                .addComponent(rolloverStrategy);

        return appenderBuilder;
    }

    LoggerComponentBuilder newLoggerComponent(ConfigurationBuilder builder, String name, List<AppenderRefComponentBuilder> appenderReferences) {
        LoggerComponentBuilder loggerComponentBuilder = builder.newLogger(name);
        for (AppenderRefComponentBuilder reference : appenderReferences) {
            loggerComponentBuilder.add(reference);
        }
        loggerComponentBuilder.addAttribute("additivity", false);
        return loggerComponentBuilder;
    }

    LoggerComponentBuilder newLoggerComponent(ConfigurationBuilder builder, String name, List<AppenderRefComponentBuilder> appenderReferences, String level) {
        LoggerComponentBuilder loggerComponentBuilder = builder.newLogger(name);
        for (AppenderRefComponentBuilder reference : appenderReferences) {
            loggerComponentBuilder.add(reference);
        }
        loggerComponentBuilder.addAttribute("additivity", false);
        loggerComponentBuilder.addAttribute("level", level);
        return loggerComponentBuilder;
    }
}
