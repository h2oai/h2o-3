package water.k8s;

import water.H2O;
import water.init.AbstractEmbeddedH2OConfig;

import java.net.InetAddress;
import java.util.Collection;

public class KubernetesEmbeddedConfig extends AbstractEmbeddedH2OConfig {

    private final String flatfile;

    public KubernetesEmbeddedConfig(final Collection<String> nodeIPs) {
        this.flatfile = writeFlatFile(nodeIPs);
    }

    private String writeFlatFile(final Collection<String> nodeIPs) {
        final StringBuilder flatFileBuilder = new StringBuilder();

        nodeIPs.forEach(nodeIP -> {
            flatFileBuilder.append(nodeIP);
            flatFileBuilder.append(":");
            flatFileBuilder.append("54321"); // All pods are expected to utilize the default H2O port
            flatFileBuilder.append("\n");
        });
        return flatFileBuilder.toString();
        
    }

    @Override
    public void notifyAboutEmbeddedWebServerIpPort(InetAddress ip, int port) {
        if (H2O.SELF == null) {
            throw new IllegalStateException("H2O.SELF is expected to be defined at this point!");
        }
        H2OCluster.setCurrentNodeInfo(new NodeInfo());
    }

    @Override
    public void notifyAboutCloudSize(InetAddress ip, int port, InetAddress leaderIp, int leaderPort, int size) {
        System.out.println("Current leader is " + leaderIp + ":" + leaderPort + " (cloud size=" + size + ").");
    }

    @Override
    public boolean providesFlatfile() {
        return true;
    }

    @Override
    public String fetchFlatfile() {
        return flatfile;
    }

    @Override
    public void exit(int status) {
        System.exit(status);
    }

    @Override
    public void print() {
    }

    private static class NodeInfo implements H2OCluster.H2ONodeInfo {
        @Override
        public boolean isLeader() {
            if (H2O.CLOUD.size() == 0) 
                return false;
            return H2O.CLOUD.leader() != null && H2O.CLOUD.leader().equals(H2O.SELF);
        }
    }

}
