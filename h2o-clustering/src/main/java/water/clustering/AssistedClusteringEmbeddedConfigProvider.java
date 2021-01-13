package water.clustering;

import org.apache.log4j.Logger;
import water.clustering.api.AssistedClusteringRestApi;
import water.clustering.api.GracefulAsyncRunner;
import water.init.AbstractEmbeddedH2OConfig;
import water.init.EmbeddedConfigProvider;
import water.util.Log;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Provides {@link AssistedClusteringEmbeddedConfig} as a result of external assist. The resulting
 * embedded config provides a flatfile assembled by external service, given to H2O. 
 * This EmbeddedConfig has no timeout and will wait indefinitely until a flatfifle is received.
 */
public class AssistedClusteringEmbeddedConfigProvider implements EmbeddedConfigProvider {
    private static final Logger LOG = Logger.getLogger(AssistedClusteringEmbeddedConfigProvider.class);

    private String flatfile;
    private final Object notification = new Object();

    @Override
    public void init() {
    }

    /**
     * Start REST API listening to incoming request with a flatfile
     */
    private Optional<AssistedClusteringRestApi> startAssistedClusteringRestApi(final Consumer<String> flatFileCallback) {
        Log.info("Starting assisted clustering REST API services");
        try {
            final AssistedClusteringRestApi assistedClusteringRestApi = new AssistedClusteringRestApi(flatFileCallback);
            assistedClusteringRestApi.setAsyncRunner(new GracefulAsyncRunner());
            assistedClusteringRestApi.start();
            Log.info("Assisted clustering REST API services successfully started.");
            return Optional.of(assistedClusteringRestApi);
        } catch (IOException e) {
            Log.err("Unable to start H2O assisted clustering REST API", e);
            System.exit(1);
            return Optional.empty();
        }
    }

    @Override
    public boolean isActive() {
        return Boolean.parseBoolean(System.getenv("H2O_ASSISTED_CLUSTERING_REST"));
    }

    @Override
    public AbstractEmbeddedH2OConfig getConfig() {
        final Consumer<String> flatFileCallback = s -> {
            // Make sure to set the flatfile first and then notify, as the flatfile is immediately used
            // to produce a resulting config
            flatfile = s;
            synchronized (notification) {
                notification.notify();
            }
        };

        try (final AssistedClusteringRestApi assistedClusteringRestApi = startAssistedClusteringRestApi(flatFileCallback)
                .orElseThrow(() -> new IllegalStateException("Assisted clustering Rest API unable to start."))) {
            try {
                // Blocks until a flatfile is received. 
                synchronized (notification) {
                    notification.wait();
                }
                return new AssistedClusteringEmbeddedConfig(flatfile);
            } catch (InterruptedException e) {
                LOG.error(e.getMessage(), e);
                throw new IllegalStateException(e);
            }
        }
    }
}
