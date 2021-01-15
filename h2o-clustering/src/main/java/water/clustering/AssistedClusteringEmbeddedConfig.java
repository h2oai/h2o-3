package water.clustering;

import water.init.AbstractEmbeddedH2OConfig;

import java.net.InetAddress;

/**
 * Embedded config providing flatfile as a result of external service hinting H2O cluster
 * the IP adresses of the pods.
 */
public class AssistedClusteringEmbeddedConfig extends AbstractEmbeddedH2OConfig {

    private final String flatfile;

    public AssistedClusteringEmbeddedConfig(final String flatFile) {
        this.flatfile = flatFile;
    }

    @Override
    public void notifyAboutEmbeddedWebServerIpPort(final InetAddress ip, final int port) {
    }

    @Override
    public void notifyAboutCloudSize(final InetAddress ip, final int port, final InetAddress leaderIp, final int leaderPort, final int size) {
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
}
