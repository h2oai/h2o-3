package water;
import water.util.Log;

public class HearthBeatFileLoggingExtension implements H2OTelemetryExtension {
    //sampling period in seconds
    private int samplingTimeout = -1;

    @Override
    public String getName() {
        return "HearthBeatFileLoggingExtension";
    }

    @Override
    public void init() {

    }

    @Override
    public void report(HeartBeat data) {
        if (Log.getLogLevel() == Log.DEBUG) {
            Log.debug(data.toJsonString());
        }
    }
}
