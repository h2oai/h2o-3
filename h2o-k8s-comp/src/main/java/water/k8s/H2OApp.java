package water.k8s;

import water.H2O;

import java.util.Collection;

public class H2OApp {

    public static void main(String[] args) {

        if (H2O.checkUnsupportedJava())
            System.exit(1);

        if (H2OCluster.isRunningOnKubernetes()) {
            Collection<String> nodeIPs = H2OCluster.resolveNodeIPs();
            KubernetesEmbeddedConfig config = new KubernetesEmbeddedConfig(nodeIPs);
            H2O.setEmbeddedH2OConfig(config);
        }

        water.H2OApp.start(args, System.getProperty("user.dir"));
    }

}
