package water;

/**
 * Service used to write to registered H2O telemetry listeners
 */
public class TelemetryService {

    private static TelemetryService service = new TelemetryService();
    private TelemetryService(){
    }

    public static TelemetryService getInstance(){
        return service;
    }

    public void report(H2ONode self){
        for (H2OTelemetryExtension ext : ExtensionManager.getInstance().getTelemetryExtensions()) {
            ext.report(self);
        }
    }
}
