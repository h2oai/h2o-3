package water.clustering.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.log4j.Logger;
import water.init.NetworkInit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.*;
import static water.clustering.api.HttpResponses.*;

/**
 * A REST Endpoint waiting for external assist to POST a flatfile with H2O nodes.
 * Once successfully submitted, this endpoint will no longer accept any new calls.
 * It is the caller's responsibility to submit a valid flatfile.
 * <p>
 * There is no parsing or validation done on the flatfile, except for basic emptiness checks.
 * The logic for IPv4/IPv6 parsing is hidden in {@link NetworkInit} class and is therefore hidden
 * from this class. As this module is intended to insertable onto classpath of any H2O, it does not rely on
 * specific NetworkInit implementation.
 */
public class AssistedClusteringEndpoint implements HttpHandler, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AssistedClusteringEndpoint.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean flatFileReceived;
    private final ExecutorService flatFileConsumerCallbackExecutor = Executors.newSingleThreadExecutor();
    private final Consumer<String> flatFileConsumer;

    public AssistedClusteringEndpoint(Consumer<String> flatFileConsumer) {
        this.flatFileConsumer = flatFileConsumer;
        this.flatFileReceived = new AtomicBoolean(false);
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (!POST_METHOD.equals(httpExchange.getRequestMethod())) {
            newResponseCodeOnlyResponse(httpExchange, HTTP_BAD_METHOD);
        }

        String postBody;
        try (InputStreamReader isr = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            postBody = br.lines().collect(Collectors.joining("\n"));
            if (postBody.isEmpty()) {
                newFixedLengthResponse(httpExchange, HTTP_BAD_REQUEST,
                        MIME_TYPE_TEXT_PLAIN, "Unable to parse IP addresses in body. Only one IPv4/IPv6 address per line is accepted.");
                return;
            }
        } catch (IOException e) {
            LOG.error("Received incorrect flatfile request.", e);
            newResponseCodeOnlyResponse(httpExchange, HTTP_BAD_REQUEST);
            return;
        }

        final Lock writeLock = lock.writeLock();
        try {
            writeLock.lock();
            if (flatFileReceived.get()) {
                newFixedLengthResponse(httpExchange, HTTP_BAD_REQUEST, MIME_TYPE_TEXT_PLAIN, "Flatfile already provided.");
                return;
            } else {
                // Do not block response with internal handling
                flatFileConsumerCallbackExecutor.submit(() -> flatFileConsumer.accept(postBody));
                flatFileReceived.set(true); // Do not accept any new requests once the flatfile has been received.
            }
        } finally {
            writeLock.unlock();
        }
        newResponseCodeOnlyResponse(httpExchange, HTTP_OK);
    }

    @Override
    public void close() {
        flatFileConsumerCallbackExecutor.shutdown();
    }
}
    
