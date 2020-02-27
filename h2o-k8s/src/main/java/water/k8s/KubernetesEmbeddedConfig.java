package water.k8s;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import water.H2O;
import water.init.AbstractEmbeddedH2OConfig;

import java.net.InetAddress;
import java.util.Collection;

public class KubernetesEmbeddedConfig extends AbstractEmbeddedH2OConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesEmbeddedConfig.class);

    private final String flatfile;

    public KubernetesEmbeddedConfig(final Collection<String> nodeIPs) {
        this.flatfile = writeFlatFile(nodeIPs);
    }

    private String writeFlatFile(final Collection<String> nodeIPs) {
        final StringBuilder flatFileBuilder = new StringBuilder();

        nodeIPs.forEach(nodeIP -> {
            flatFileBuilder.append(nodeIP);
            flatFileBuilder.append(":");
            flatFileBuilder.append(H2O.H2O_DEFAULT_PORT); // All pods are expected to utilize the default H2O port
            flatFileBuilder.append("\n");
        });

        return flatFileBuilder.toString();
    }

    @Override
    public void notifyAboutEmbeddedWebServerIpPort(InetAddress ip, int port) {
    }

    @Override
    public void notifyAboutCloudSize(InetAddress ip, int port, InetAddress leaderIp, int leaderPort, int size) {
        LOGGER.info(String.format("Created cluster of size %d, leader node IP is '%s'", size, leaderIp.toString()));
    }

    @Override
    public boolean providesFlatfile() {
        return true;
    }

    @Override
    public String fetchFlatfile() throws Exception {
        return flatfile;
    }

    @Override
    public void exit(int status) {
    }

    @Override
    public void print() {
    }
}
