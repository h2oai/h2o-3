package water.clustering.api;

import fi.iki.elonen.NanoHTTPD;
import org.apache.log4j.Logger;


/**
 * The REST API is only needed until a valid flatfile is received. Afterwards, the flatfile is handed over to
 * H2O via embedded config. Therefore, as soon as the embedded config is constructed, the REST API is shut down
 * and the objects destroyed. However, there might still be a thread running - sending HTTP response to the assist which
 * sent the flatfile. The {@link fi.iki.elonen.NanoHTTPD.DefaultAsyncRunner} does not wait for existing connections
 * to be properly terminated and shuts them down.
 * <p>
 * This implementation overrides that behavior and waits until all connections are closed before shutdown.
 */
public class GracefulAsyncRunner extends NanoHTTPD.DefaultAsyncRunner {
    private static final Logger LOG = Logger.getLogger(GracefulAsyncRunner.class);

    @Override
    public void closeAll() {
        while (getRunning().size() > 0) {
            synchronized (this) {
                try {
                    this.wait(1000);
                } catch (InterruptedException e) {
                    LOG.error("Waiting for asyncRunner to gracefully shutdown interrupted. Closing all connections now.", e);
                }
            }
        }
        super.closeAll();
    }

    @Override
    public void closed(final NanoHTTPD.ClientHandler clientHandler) {
        super.closed(clientHandler);
        synchronized (this) {
            this.notify();
        }
    }
}
