package water.k8s;

import water.init.AbstractEmbeddedH2OConfig;
import water.init.EmbeddedConfigProvider;
import java.util.Collection;

/**
 * A configuration provider for H2O running in Kubernetes cluster. It is able to detected H2O is being ran in K8S
 * environment, otherwise remains inactive.
 * <p>
 * Uses potentially multiple strategies to discover H2O Pods on a Kubernetes cluster.
 */
public class KubernetesEmbeddedConfigProvider implements EmbeddedConfigProvider {

    private boolean runningOnKubernetes = false;
    private KubernetesEmbeddedConfig kubernetesEmbeddedConfig;


    @Override
    public void init() {
        runningOnKubernetes = H2OCluster.isRunningOnKubernetes();

        if (!runningOnKubernetes) {
            return; // Do not initialize any configuration if H2O is not running in K8S-spawned container.
        }

        Collection<String> nodeIPs = H2OCluster.resolveNodeIPs();
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

}
