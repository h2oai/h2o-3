package water.k8s;

import org.apache.log4j.Logger;
import water.k8s.api.KubernetesRestApi;
import water.k8s.lookup.KubernetesDnsLookup;
import water.k8s.lookup.KubernetesLookup;
import water.k8s.lookup.LookupConstraintsBuilder;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public class H2OCluster {

    private static final Logger LOG = Logger.getLogger(H2OCluster.class);

    private volatile static boolean clustered = false;
    private volatile static H2ONodeInfo nodeInfo;

    public static final String K8S_NODE_LOOKUP_TIMEOUT_KEY = "H2O_NODE_LOOKUP_TIMEOUT";
    public static final String K8S_DESIRED_CLUSTER_SIZE_KEY = "H2O_NODE_EXPECTED_COUNT";

    public static boolean isClustered() {
        return clustered;
    }

    public static H2ONodeInfo getCurrentNodeInfo() {
        return nodeInfo;
    }

    public static void setCurrentNodeInfo(H2ONodeInfo ni) {
        nodeInfo = ni;
    }
    
    /**
     * @return True if there are environment variables indicating H2O is running inside a container managed by
     * Kubernetes. Otherwise false.
     */
    public static boolean isRunningOnKubernetes() {
        return KubernetesDnsLookup.isLookupPossible();
    }

    public static Collection<String> resolveNodeIPs() {
        startKubernetesRestApi();

        LOG.info("Initializing H2O Kubernetes cluster");
        final Collection<String> nodeIPs = resolveInternalNodeIPs()
                .orElseThrow(() -> new IllegalStateException("Unable to resolve Node IPs from DNS service."));

        LOG.info(String.format("Using the following pods to form H2O cluster: [%s]",
                String.join(",", nodeIPs)));
        
        clustered = true;
        return nodeIPs;
    }

    /**
     * @return A Set of node addresses. The adresses are internal adresses/IPs to the Kubernetes cluster.
     */
    private static Optional<Set<String>> resolveInternalNodeIPs() {
        final LookupConstraintsBuilder lookupConstraintsBuilder = new LookupConstraintsBuilder();

        try {
            final int timeoutSeconds = Integer.parseInt(System.getenv(K8S_NODE_LOOKUP_TIMEOUT_KEY));
            LOG.info(String.format("Timeout contraint: %d seconds.", timeoutSeconds));
            lookupConstraintsBuilder.withTimeoutSeconds(timeoutSeconds);
        } catch (NumberFormatException e) {
            LOG.info(String.format("'%s' environment variable not set.", K8S_NODE_LOOKUP_TIMEOUT_KEY));
        }

        try {
            final int desiredClusterSize = Integer.parseInt(System.getenv(K8S_DESIRED_CLUSTER_SIZE_KEY));
            LOG.info(String.format("Cluster size constraint: %d nodes.", desiredClusterSize));
            lookupConstraintsBuilder.withDesiredClusterSize(desiredClusterSize);
        } catch (NumberFormatException e) {
            LOG.info(String.format("'%s' environment variable not set.", K8S_DESIRED_CLUSTER_SIZE_KEY));
        }

        final KubernetesLookup kubernetesDnsDiscovery = KubernetesDnsLookup.fromH2ODefaults();
        return kubernetesDnsDiscovery.lookupNodes(lookupConstraintsBuilder.build());
    }

    /**
     * Start Kubernetes-only REST API services
     */
    private static void startKubernetesRestApi() {
        LOG.info("Starting Kubernetes-related REST API services");
        try {
            final KubernetesRestApi kubernetesRestApi = new KubernetesRestApi();
            kubernetesRestApi.start();
            LOG.info("Kubernetes REST API services successfully started.");
        } catch (IOException e) {
            LOG.error("Unable to start H2O Kubernetes REST API", e);
            System.exit(1);
        }
    }

    public interface H2ONodeInfo {
        boolean isLeader();
    }
    
}
