package water.k8s.api;

import com.sun.net.httpserver.HttpServer;
import water.k8s.probe.KubernetesLeaderNodeProbeHandler;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * This class represents a tiny (memory, cpu, dependencies) self-contained API only for Kubernetes,
 * running separately on localhost on a specified port.
 * When the Kubernetes extension is starting and attempting to form an H2O cluster,
 * H2O's REST API is yet not fully initialized, as its startup relies on configuration obtained
 * during the clustering phase. However, REST API services (readiness probe, liveness probe, startup probe)
 * are required to be available since the very start of H2O K8S extension. Therefore, a separate REST API running
 * on a distinct port is spawned.
 * <p>
 */
public class KubernetesRestApi implements AutoCloseable {

    /**
     * Default port to bind to / listen on.
     */
    private static final int DEFAULT_PORT = 8080;
    public static final String KUBERNETES_REST_API_PORT_KEY = "H2O_KUBERNETES_API_PORT";

    private final HttpServer server;

    /**
     * Creates, but not starts Kubernetes REST API. To start the REST API, please use
     * one of the start methods available.
     * <p>
     * The REST API is bound to a default port of 8080, unless specified otherwise by H2O_KUBERNETES_API_PORT environment
     * variable.
     */
    public KubernetesRestApi() throws IOException {
        int port = getPort();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        addMappings();
    }

    private static int getPort() {
        final String customKubernetesPort = System.getenv(KUBERNETES_REST_API_PORT_KEY);

        if (customKubernetesPort == null) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(customKubernetesPort);
        } catch (NumberFormatException e) {
            final String errorMessage = String.format("Non-usable port for K8S REST API to bind to: '%s'", customKubernetesPort);
            throw new IllegalArgumentException(errorMessage, e);
        }
    }

    public void addMappings() {
        server.createContext("/kubernetes/isLeaderNode", new KubernetesLeaderNodeProbeHandler());
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public void start() throws IOException {
        server.start();
    }

}
