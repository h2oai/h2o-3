package water.clustering;

import org.apache.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import water.init.AbstractEmbeddedH2OConfig;
import water.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class AssistedClusteringEmbeddedConfigProviderTest {

    private static final Logger LOG = Logger.getLogger(AssistedClusteringEmbeddedConfigProviderTest.class);
    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testEmbeddedConfigActivation() throws Exception {
        environmentVariables.set("H2O_ASSISTED_CLUSTERING_REST", "True");

        final ExecutorService executor = Executors.newFixedThreadPool(1);

        final String flatfile = "1200:0000:AB00:1234:0000:2552:7777:1313\n" +
                "[1200:0000:AB00:1234:0000:2552:7777:1313]:54321\n" +
                "9.255.255.255\n" +
                "9.255.255.255:54321";

        final Future<?> flatFileTest = executor.submit(() -> {
            final AssistedClusteringEmbeddedConfigProvider provider = new AssistedClusteringEmbeddedConfigProvider();
            assertTrue(provider.isActive());
            provider.init();

            final AbstractEmbeddedH2OConfig config = provider.getConfig();
            assertNotNull(config);

            assertTrue(config.providesFlatfile());
            try {
                assertEquals(flatfile, config.fetchFlatfile());
            } catch (Exception e) {
                Log.err(e.getMessage(), e);
                fail(e.getMessage());
            }

        });

        final int responseCode = callFlatfileEndpoint(flatfile);
        assertEquals(200, responseCode);
        flatFileTest.get();
    }

    private static int callFlatfileEndpoint(final String flatfile) throws IOException, InterruptedException {
        final URL url = new URL("http://localhost:8080/clustering/flatfile");
        final HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Content-Length", String.valueOf(flatfile.length()));
        con.setRequestProperty("Content-Type", "text/plain");
        con.setDoOutput(true);
        con.setRequestMethod("POST");

        // REST API must be started first. This is a matter of milliseconds, therefore 10 retries in 10 seconds
        // are more than enough.
        for (int i = 0; i < 10; i++) {
            try (OutputStream outputStream = con.getOutputStream()) {
                outputStream.write(flatfile.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                outputStream.close();
                con.connect();
                return con.getResponseCode();
            } catch (Throwable t) {
                Thread.sleep(1000);
                continue;
            } finally {
                con.disconnect();
            }
        }
        
        // If a connection is not established, report HTTP 500 status code
        return 500;
    }

}
