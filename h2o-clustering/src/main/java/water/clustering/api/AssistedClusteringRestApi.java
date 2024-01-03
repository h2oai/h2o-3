package water.clustering.api;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Rest API definition for the assisted clustering function.
 */
public class AssistedClusteringRestApi implements AutoCloseable {

    /**
     * Default port to bind to / listen on.
     */
    private static final int DEFAULT_PORT = 8080;
    public static final String ASSISTED_CLUSTERING_PORT_KEY = "H2O_ASSISTED_CLUSTERING_API_PORT";
    private final AssistedClusteringEndpoint assistedClusteringEndpoint;

    private final HttpServer server;

    /**
     * Creates, but not starts assisted clustering REST API. To start the REST API, please use
     * one of the start methods available.
     * <p>
     * The REST API is bound to a default port of 8080, unless specified otherwise by the H2O_ASSISTED_CLUSTERING_API_PORT environment
     * variable.
     */
    public AssistedClusteringRestApi(Consumer<String> flatFileConsumer) throws IOException {
        Objects.requireNonNull(flatFileConsumer);
        this.assistedClusteringEndpoint = new AssistedClusteringEndpoint(flatFileConsumer);
        int port = getPort();
        server = HttpServer.create(new InetSocketAddress(port), 0);
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

    private void addMappings() {
        server.createContext("/clustering/flatfile", assistedClusteringEndpoint);
        server.createContext("/cluster/status", new H2OClusterStatusEndpoint());
    }

    /**
     * From AutoCloseable - aids usage inside try-with-resources blocks.
     */
    @Override
    public void close() {
        assistedClusteringEndpoint.close();
        server.stop(0);
    }

    public void start() throws IOException {
        server.start();
    }
}
