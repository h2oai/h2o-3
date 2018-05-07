package water;

//TODO: think about placing it to some directory (eventhough H2OListenerExtension is not)
public interface H2OTelemetryExtension {
    /** Name of listener extension */
    String getName();

    /** Initialize the extension */
    void init();

    void report(HeartBeat data);
}