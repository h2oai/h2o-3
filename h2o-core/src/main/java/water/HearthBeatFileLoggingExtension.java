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
    }

    @Override
    public void report(HeartBeat data) {
        Log.telemetry(data.toJsonString());
    }
}
