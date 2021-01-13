package water.clustering;

import org.apache.log4j.Logger;
import water.init.AbstractEmbeddedH2OConfig;

import java.net.InetAddress;

public class AssistedClusteringEmbeddedConfig extends AbstractEmbeddedH2OConfig {
    
    private static final Logger LOG = Logger.getLogger(AssistedClusteringEmbeddedConfig.class);
    
    private final String flatfile;

    public AssistedClusteringEmbeddedConfig(final String flatFile) {
        this.flatfile = flatFile;
    }

   
    @Override
    public void notifyAboutEmbeddedWebServerIpPort(final InetAddress ip, final int port) {
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
