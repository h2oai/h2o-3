package water.clustering.api;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * A REST Endpoint waiting for external assist to POST a flatifle with H2O nodes.
 * Once successfully submitted, this endpoint will no longer accept any new calls. 
 * It is the caller's responsibility to submit a valid flatfile.
 * 
 * There is no parsing or validation done on the flatfile, except for basic emptiness checks.
 * The logic for IPv4/IPv6 parsing is hidden in {@link water.init.NetworkInit} class and is therefore hidden 
 * from this class. As this module is intended to insertable onto classpath of any H2O, it does not rely on
 * specific NetworkInit implementation.
 */
public class AssistedClusteringEndpoint extends RouterNanoHTTPD.DefaultHandler {

    private static final Logger LOG = Logger.getLogger(AssistedClusteringEndpoint.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean flatFileReceived;
    private final ExecutorService flatFileConsumerCallbackExecutor = Executors.newSingleThreadExecutor();

    public AssistedClusteringEndpoint() {
        flatFileReceived = new AtomicBoolean(false);
    }

    @Override
    public String getText() {
        throw new IllegalStateException(String.format("Method getText should not be called on '%s'",
                getClass().getName()));
    }

    @Override
    public String getMimeType() {
        return "text/plain";
    }

    @Override
    public NanoHTTPD.Response.IStatus getStatus() {
        return null;
    }

    public static final String RESPONSE_MIME_TYPE = "text/plain";

    @Override
    public NanoHTTPD.Response post(final RouterNanoHTTPD.UriResource uriResource, final Map<String, String> urlParams, final NanoHTTPD.IHTTPSession session) {
        final Map<String, String> map = new HashMap<>();
        try {
            session.parseBody(map);
        } catch (IOException | NanoHTTPD.ResponseException e) {
            LOG.error("Received incorrect flatfile request.", e);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, RESPONSE_MIME_TYPE, null);
        }
        // The text/plain content-type is stored as `postData` by HTTPD in the map.
        final String postBody = map.get("postData");

        if (postBody != null) {
            final Lock writeLock = lock.writeLock();
            try {
                writeLock.lock();
                if (flatFileReceived.get()) {
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, RESPONSE_MIME_TYPE,
                            "Flatfile already provided.");
                } else {
                    final Consumer<String> flatFileConsumer = (Consumer<String>) uriResource.initParameter(Consumer.class);
                    // Do not block response with internal handling
                    flatFileConsumerCallbackExecutor.submit(() -> flatFileConsumer.accept(postBody));
                    flatFileReceived.set(true); // Do not accept any new requests once the flatfile has been received.
                }
            } finally {
                writeLock.unlock();
            }
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, RESPONSE_MIME_TYPE, null);
        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, RESPONSE_MIME_TYPE,
                    "Unable to parse IP addresses in body. Only one IPv4/IPv6 address per line is accepted.");
        }
    }
}
    
