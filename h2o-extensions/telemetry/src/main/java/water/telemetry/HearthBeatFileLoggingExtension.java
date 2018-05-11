package water.telemetry;
import org.apache.log4j.*;
import water.H2OTelemetryExtension;
import water.HeartBeat;
import water.util.Log;
import java.io.File;
import java.util.Date;


public class HearthBeatFileLoggingExtension implements H2OTelemetryExtension {
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
    public void report(HeartBeat data, long timestamp, String ipAndPort) {
        if (!initializedLogger){
            registerRollingFileAppenderToAsyncAppender();
            initializedLogger = true;
        }

        String s = enhanceHeartBeatJson(data, timestamp, ipAndPort);
        Log.telemetry(s);
    }

    private String enhanceHeartBeatJson(HeartBeat hb, long timestamp, String ipAndPort){
        // remove curly brackets
        String hbstring = hb.toJsonString();
        hbstring = hbstring.substring(1, hbstring.length()-1);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"timestamp\":\"").append(new Date(timestamp)).append(timestamp).append("\",\"ip_port\":\"").append(ipAndPort).append("\",")
        .append(hbstring)
        .append("}\n");
        return sb.toString();
    }
}

