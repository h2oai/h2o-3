package water.clustering;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import water.init.AbstractEmbeddedH2OConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class AssistedClusteringEmbeddedConfigProviderTest {

    
    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void setUp() {
        environmentVariables.set("H2O_ASSISTED_CLUSTERING_REST", "true");
        environmentVariables.set("H2O_ASSISTED_CLUSTERING_API_PORT", "9401");
    }

    @Test
    public void testEmbeddedConfigActivation() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(1);

        final String flatfile = "1200:0000:AB00:1234:0000:2552:7777:1313\n" +
                "[1200:0000:AB00:1234:0000:2552:7777:1313]:54321\n" +
                "9.255.255.255\n" +
                "9.255.255.255:54321";

        final Future<String> flatFileTest = executor.submit(() -> {
            final AssistedClusteringEmbeddedConfigProvider provider = new AssistedClusteringEmbeddedConfigProvider();
            assertTrue(provider.isActive());
            provider.init();

            try {
                final AbstractEmbeddedH2OConfig config = provider.getConfig();
                assertNotNull(config);
                assertTrue(config.providesFlatfile());
                return config.fetchFlatfile();
            } finally {
                provider.close();
            }
        });

        final int responseCode = callFlatfileEndpoint(flatfile);
        assertEquals(200, responseCode);
        
        String fetched = flatFileTest.get();
        assertEquals(flatfile, fetched);
    }

    private static int callFlatfileEndpoint(final String flatfile) throws IOException, InterruptedException {
        // REST API must be started first. This is a matter of milliseconds, therefore 10 retries in 10 seconds
        // are more than enough.
        for (int i = 0; i < 10; i++) {
            try {
                final URL statusURL = new URL("http://localhost:9401/cluster/status");
                InputStream con = (InputStream) statusURL.getContent();
                con.close();
            } catch (Exception e) {
                e.printStackTrace();
                Thread.sleep(1000);
            }
        }
        
        final URL url = new URL("http://localhost:9401/clustering/flatfile");
        final HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Content-Length", String.valueOf(flatfile.length()));
        con.setRequestProperty("Content-Type", "text/plain");
        con.setDoOutput(true);
        con.setRequestMethod("POST");

        try (OutputStream outputStream = con.getOutputStream()) {
            outputStream.write(flatfile.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            outputStream.close();
            con.connect();
            return con.getResponseCode();
        } finally {
            con.disconnect();
        }
    }

}
