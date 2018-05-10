package water;
import water.util.Log;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class HearthBeatFileLoggingExtension implements H2OTelemetryExtension {
    //sampling period in seconds
    //TODO: allow to configure
    private int samplingTimeout = 10000;
    private BlockingQueue<HeartBeat> heartBeats = new LinkedBlockingQueue<>();

    @Override
    public String getName() {
        return "HearthBeatFileLoggingExtension";
    }

    @Override
    public void init() {
        new TelemetryThread().start();
    }

    @Override
    public void report(HeartBeat data) {
        try {
            heartBeats.put(data);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private class TelemetryThread extends Thread {
        public TelemetryThread(){
            super("Telemetry Thread HB Consumer");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {
                StringBuilder sb = new StringBuilder();
                while (!heartBeats.isEmpty()) {
                    try {
                        HeartBeat hb = heartBeats.poll(samplingTimeout, TimeUnit.SECONDS);
                        sb.append(hb.toJsonString());
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                try {
                    Log.telemetry(sb.toString());
                    Thread.sleep(samplingTimeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
