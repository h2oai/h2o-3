package water;

public interface H2OTelemetryExtension {
    /** Name of listener extension */
    String getName();

    /** Initialize the extension */
    void init();

    void report(HeartBeat data);
}