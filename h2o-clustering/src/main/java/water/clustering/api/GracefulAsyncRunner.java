package water.clustering.api;

import fi.iki.elonen.NanoHTTPD;
import org.apache.log4j.Logger;


public class GracefulAsyncRunner extends NanoHTTPD.DefaultAsyncRunner {
    private static final Logger LOG = Logger.getLogger(GracefulAsyncRunner.class);
    private Object notifier = new Object();

    @Override
    public void closeAll() {
        while (getRunning().size() > 0) {
            synchronized (notifier) {
                try {
                    notifier.wait();
                } catch (InterruptedException e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        }
        super.closeAll();
    }

    @Override
    public void closed(final NanoHTTPD.ClientHandler clientHandler) {
        super.closed(clientHandler);
        synchronized (notifier) {
            notifier.notify();
        }
    }
}
