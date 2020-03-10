package water.k8s;

import water.init.AbstractEmbeddedH2OConfig;
import water.init.EmbeddedConfigProvider;
import water.k8s.lookup.KubernetesDnsLookup;
import water.k8s.lookup.KubernetesLookup;
import water.k8s.lookup.LookupConstraintsBuilder;
import water.util.Log;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A configuration provider for H2O running in Kubernetes cluster. It is able to detected H2O is being ran in K8S
 * environment, otherwise remains inactive.
 * <p>
 * Uses potentially multiple strategies to discover H2O Pods on a Kubernetes cluster.
 */
public class KubernetesEmbeddedConfigProvider implements EmbeddedConfigProvider {

    private static final String K8S_NODE_LOOKUP_TIMEOUT_KEY = "H2O_NODE_LOOKUP_TIMEOUT";
    private static final String K8S_DESIRED_CLUSTER_SIZE_KEY = "H2O_NODE_EXPECTED_COUNT";

    private boolean runningOnKubernetes = false;
    private KubernetesEmbeddedConfig kubernetesEmbeddedConfig;

    /**
     * 
     * @return A Set of node addresses. The adresses are internal adresses/IPs to the Kubernetes cluster.
     */
    private static final Optional<Set<String>> resolveInternalNodeIPs() {
        final LookupConstraintsBuilder lookupConstraintsBuilder = new LookupConstraintsBuilder();

        try {
            final Integer timeoutSeconds = Integer.parseInt(System.getenv(K8S_NODE_LOOKUP_TIMEOUT_KEY));
            lookupConstraintsBuilder.withTimeoutSeconds(timeoutSeconds);
        } catch (NumberFormatException e) {
            Log.info(String.format("'%s' environment variable not set.", K8S_NODE_LOOKUP_TIMEOUT_KEY));
        }

        try {
            final Integer desiredClusterSize = Integer.parseInt(System.getenv(K8S_DESIRED_CLUSTER_SIZE_KEY));
            lookupConstraintsBuilder.withDesiredClusterSize(desiredClusterSize);
        } catch (NumberFormatException e) {
            Log.info(String.format("'%s' environment variable not set.", K8S_DESIRED_CLUSTER_SIZE_KEY));
        }

        final KubernetesLookup kubernetesDnsDiscovery = KubernetesDnsLookup.fromH2ODefaults();
        return kubernetesDnsDiscovery.lookupNodes(lookupConstraintsBuilder.build());
    }

    @Override
    public void init() {
        runningOnKubernetes = isRunningOnKubernetes();

        if (!runningOnKubernetes) {
            return; // Do not initialize any configuration if H2O is not running in K8S-spawned container.
        }

        Log.info("Initializing H2O Kubernetes cluster");
        final Collection<String> nodeIPs = resolveInternalNodeIPs()
                .orElseThrow(() -> new IllegalStateException("Unable to resolve Node IPs from DNS service."));

        Log.info(String.format("Using the following pods to form H2O cluster: [%s]",
                String.join(",", nodeIPs)));

        kubernetesEmbeddedConfig = new KubernetesEmbeddedConfig(nodeIPs);
    }

    @Override
    public boolean isActive() {
        return runningOnKubernetes;
    }

    @Override
    public AbstractEmbeddedH2OConfig getConfig() {
        return kubernetesEmbeddedConfig;
    }

    /**
     * @return True if there are environment variables indicating H2O is running inside a container managed by
     * Kubernetes. Otherwise false.
     */
    private boolean isRunningOnKubernetes() {
        return KubernetesDnsLookup.isLookupPossible();
    }
}
