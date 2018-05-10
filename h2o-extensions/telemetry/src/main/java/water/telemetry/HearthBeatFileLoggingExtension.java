package water.telemetry;
import org.apache.log4j.*;
import water.H2OTelemetryExtension;
import water.HeartBeat;
import water.util.Log;
import java.io.File;


public class HearthBeatFileLoggingExtension implements H2OTelemetryExtension {
    //sampling period in miliseconds
    //TODO: allow to configure
    private int samplingTimeout = 10000;
    private boolean initializedLogger = false;
    @Override
    public String getName() {
        return "HearthBeatFileLoggingExtension";
    }

    @Override
    public void init() {
    }

    private static void registerRollingFileAppenderToAsyncAppender() {
        AsyncAppender asyncAppender = (AsyncAppender) LogManager.getLogger("Telemetry").getAppender("AsyncTelemetryAppender");
        RollingFileAppender telemetryAppender = new RollingFileAppender();
        telemetryAppender.setThreshold(Priority.INFO);
        try {
            telemetryAppender.setFile(Log.getLogDir() + File.separator + Log.getLogFileName("telemetry"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        telemetryAppender.setName("Telemetry");
        telemetryAppender.setMaxFileSize("1MB");
        telemetryAppender.setMaxBackupIndex(3);
        telemetryAppender.setLayout(new PatternLayout("%m%n"));
        telemetryAppender.activateOptions();
        asyncAppender.addAppender(telemetryAppender);
    }

    @Override
    public void report(HeartBeat data) {
        if (!initializedLogger){
            registerRollingFileAppenderToAsyncAppender();
            initializedLogger = false;
        }
        Log.telemetry(data.toJsonString());
    }
}

