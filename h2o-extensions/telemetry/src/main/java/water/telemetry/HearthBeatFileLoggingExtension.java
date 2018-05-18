package water.telemetry;
import org.apache.log4j.*;
import water.H2OTelemetryExtension;
import water.HeartBeat;
import water.util.Log;
import java.io.File;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


public class HearthBeatFileLoggingExtension implements H2OTelemetryExtension {
    private boolean initializedLogger = false;
    private BlockingQueue<EnhancedHearBeat> hbs = new ArrayBlockingQueue<EnhancedHearBeat>(10);

    @Override
    public String getName() {
        return "HearthBeatFileLoggingExtension";
    }

    @Override
    public void init() {
        new WriteThread().start();
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
        telemetryAppender.setMaxFileSize("100MB");
        telemetryAppender.setMaxBackupIndex(3);
        telemetryAppender.setLayout(new PatternLayout("%m%n"));
        telemetryAppender.activateOptions();
        asyncAppender.addAppender(telemetryAppender);
    }

    @Override
    public void report(HeartBeat data, long timestamp, String ipAndPort) {
        if (!initializedLogger){
            synchronized (this) {
                if (!initializedLogger) {
                    registerRollingFileAppenderToAsyncAppender();
                    initializedLogger = true;
                }
            }
        }
        hbs.add(new EnhancedHearBeat(data, timestamp, ipAndPort));
    }

    private void writeLog(HeartBeat hb, long timestamp, String ipAndPort){
        StringBuilder sb = new StringBuilder();
        sb.append("{\"timestamp\":\"").append(new Date(timestamp)).append(timestamp).append("\",")
        .append("\"ip_port\":\"").append(ipAndPort).append("\",")
        .append("\"system_load_average\":\"").append(hb._system_load_average).append("\",")
        .append("\"system_idle_ticks\":\"").append(hb._system_idle_ticks).append("\",")
        .append("\"system_total_ticks\":\"").append(hb._system_total_ticks).append("\",")
        .append("\"process_total_ticks\":\"").append(hb._process_total_ticks).append("\",")
        .append("\"process_num_open_fds\":\"").append(hb._process_num_open_fds).append("\",")
        .append("\"kv_mem\":\"").append(hb.get_kv_mem()).append("\",")
        .append("\"pojo_mem\":\"").append(hb.get_pojo_mem()).append("\",")
        .append("\"free_mem\":\"").append(hb.get_free_mem()).append("\",")
        .append("\"swap_mem\":\"").append(hb.get_swap_mem()).append("\",")
        .append("\"free_disk\":\"").append(hb.get_free_disk()).append("\",")
        .append("\"max_disk\":\"").append(hb.get_max_disk()).append("\",")
        .append("\"gflops\":\"").append(hb._gflops).append("\",")
        .append("\"membw\":\"").append(hb._membw).append("\"")
        .append("}");
        Log.telemetry(sb.toString());
    }

    private class EnhancedHearBeat {
        public HeartBeat hb;
        public final long timestamp;
        public final String ip;

        private EnhancedHearBeat(HeartBeat hb, long timestamp, String ip) {
            this.timestamp = timestamp;
            this.ip = ip;
            this.hb = hb;
        }
    }

    private class WriteThread extends Thread{
        public WriteThread(){
            super("Telemetry writer thread.");
            setDaemon(true);
        }
        @Override
        public void run() {
            super.run();
            while (true){
                EnhancedHearBeat hb;
                try {
                    hb = hbs.take();
                    writeLog(hb.hb, hb.timestamp, hb.ip);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

