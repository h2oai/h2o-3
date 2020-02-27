package water.k8s;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import water.H2O;
import water.H2OApp;
import water.H2OStarter;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public class H2OKubernetesStarter {

    private static final String K8S_NODE_LOOKUP_TIMEOUT_KEY = "H2O_NODE_LOOKUP_TIMEOUT";
    private static final int DEFAULT_NODE_LOOKUP_TIMEOUT_SECONDS = 60;
    private static final Logger LOGGER = LoggerFactory.getLogger(H2OKubernetesStarter.class);

    public static void main(final String[] args) {
        LOGGER.info("Initializing H2O Kubernetes cluster");
        final Collection<String> nodeIPs = resolveNodeIPsFromDNS()
                .orElseThrow(() -> new IllegalStateException("Unable to resolve Node IPs from DNS service."));

        LOGGER.info(String.format("Discovered the following pods on K8s cluster: [%s]", 
                String.join(",", nodeIPs)) );

        final KubernetesEmbeddedConfig kubernetesEmbeddedConfig = new KubernetesEmbeddedConfig(nodeIPs);
        H2O.setEmbeddedH2OConfig(kubernetesEmbeddedConfig);
        H2OStarter.start(args, System.getProperty("user.dir"));
    }

    private static final Optional<Set<String>> resolveNodeIPsFromDNS() {
        int timeoutSeconds;
        try {
            timeoutSeconds = Integer.parseInt(System.getenv(K8S_NODE_LOOKUP_TIMEOUT_KEY));
        } catch (NumberFormatException e) {
            LOGGER.info(String.format("Using default timeout of %d seconds.", DEFAULT_NODE_LOOKUP_TIMEOUT_SECONDS));
            timeoutSeconds = DEFAULT_NODE_LOOKUP_TIMEOUT_SECONDS;
        }

        final KubernetesDnsDiscovery kubernetesDnsDiscovery = KubernetesDnsDiscovery.fromH2ODefaults();
        return kubernetesDnsDiscovery.lookupNodes(3, timeoutSeconds);
    }

}
