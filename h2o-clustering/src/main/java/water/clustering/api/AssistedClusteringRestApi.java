package water.clustering.api;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Rest API definition for the assisted clustering function.
 */
public class AssistedClusteringRestApi extends RouterNanoHTTPD implements AutoCloseable {

    /**
     * Default port to bind to / listen on.
     */
    private static final int DEFAULT_PORT = 8080;
    public static final String ASSISTED_CLUSTERING_PORT_KEY = "H2O_ASSISTED_CLUSTERING_API_PORT";
    private final Consumer<String> flatFileConsumer;


    /**
     * Creates, but not starts assisted clustering REST API. To start the REST API, please use
     * one of the start methods available.
     * <p>
     * The REST API is bound to a default port of 8080, unless specified otherwise by the H2O_ASSISTED_CLUSTERING_API_PORT environment
     * variable.
     */
    public AssistedClusteringRestApi(Consumer<String> flatFileConsumer) {
        super(getPort());
        Objects.requireNonNull(flatFileConsumer);
        this.flatFileConsumer = flatFileConsumer;
        addMappings();
    }

    /**
     * @return Either user-defined port via environment variable or default port to bind the REST API to.
     */
    private static int getPort() {
        final String customPort = System.getenv(ASSISTED_CLUSTERING_PORT_KEY);

        if (customPort == null) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(customPort);
        } catch (NumberFormatException e) {
            final String errorMessage = String.format("Unusable port for Assisted clustering REST API to bind to: '%s'", customPort);
            throw new IllegalArgumentException(errorMessage, e);
        }
    }

    @Override
    public void addMappings() {
        super.addMappings();
        addRoute("/clustering/flatfile", AssistedClusteringEndpoint.class, this.flatFileConsumer);
        addRoute("/cluster/status", H2OClusterStatusEndpoint.class);
    }

    /**
     * From AutoCloseable - aids usage inside try-with-resources blocks.
     */
    @Override
    public void close() {
        stop();
    }

    @Override
    public void start() throws IOException {
        // Make sure the API is never ran as daemon and is properly terminated with the H2O JVM (the latest)
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    @Override
    public void start(int timeout) throws IOException {
        // Make sure the API is never ran as daemon and is properly terminated with the H2O JVM (the latest)
        super.start(timeout, false);
    }
}
