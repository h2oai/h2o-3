package water.k8s;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import water.init.AbstractEmbeddedH2OConfig;
import water.init.EmbeddedConfigProvider;
import water.k8s.lookup.LookupConstraintBuilder;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class H2OKubernetesEmbeddedConfigProvider implements EmbeddedConfigProvider {

    private static final String K8S_NODE_LOOKUP_TIMEOUT_KEY = "H2O_NODE_LOOKUP_TIMEOUT";
    private static final String K8S_DESIRED_CLUSTER_SIZE_KEY = "H2O_NODE_EXPECTED_COUNT";
    private static final Logger LOGGER = LoggerFactory.getLogger(H2OKubernetesEmbeddedConfigProvider.class);

    private boolean runningOnKubernetes = false;
    private KubernetesEmbeddedConfig kubernetesEmbeddedConfig;

    private static final Optional<Set<String>> resolveNodeIPsFromDNS() {
        final LookupConstraintBuilder lookupConstraintBuilder = new LookupConstraintBuilder();

        try {
            final Integer timeoutSeconds = Integer.parseInt(System.getenv(K8S_NODE_LOOKUP_TIMEOUT_KEY));
            lookupConstraintBuilder.withTimeoutSeconds(timeoutSeconds);
        } catch (NumberFormatException e) {
            LOGGER.info(String.format("'%s' environment variable not set.", K8S_NODE_LOOKUP_TIMEOUT_KEY));
        }

        try {
            final Integer desiredClusterSize = Integer.parseInt(System.getenv(K8S_DESIRED_CLUSTER_SIZE_KEY));
            lookupConstraintBuilder.withDesiredClusterSize(desiredClusterSize);
        } catch (NumberFormatException e) {
            LOGGER.info(String.format("'%s' environment variable not set.", K8S_DESIRED_CLUSTER_SIZE_KEY));
        }

        final KubernetesDnsDiscovery kubernetesDnsDiscovery = KubernetesDnsDiscovery.fromH2ODefaults();
        return kubernetesDnsDiscovery.lookupNodes(lookupConstraintBuilder.build());
    }

    @Override
    public void init() {
        runningOnKubernetes = isRunningOnKubernetes();

        if (!runningOnKubernetes) {
            return; // Do not initialize any configuration if H2O is not running in K8S-spawned container.
        }

        LOGGER.info("Initializing H2O Kubernetes cluster");
        final Collection<String> nodeIPs = resolveNodeIPsFromDNS()
                .orElseThrow(() -> new IllegalStateException("Unable to resolve Node IPs from DNS service."));

        LOGGER.info(String.format("Using the following pods to form H2O cluster: [%s]",
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

    private boolean isRunningOnKubernetes() {
        final Pattern KUBERNETES_SERVICE_HOST = Pattern.compile(".*_SERVICE_HOST");
        final Pattern KUBERNETES_SERVICE_PORT = Pattern.compile(".*_SERVICE_PORT");
        return System.getenv().keySet()
                .stream()
                .anyMatch(key -> KUBERNETES_SERVICE_HOST.matcher(key).matches()
                        || KUBERNETES_SERVICE_PORT.matcher(key).matches());

    }
}
