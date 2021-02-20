package water.clustering.api;

import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class AssistedClusteringEndpointTest {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();
    
    private AssistedClusteringRestApi assistedClusteringRestApi;
    private FlatFileEventConsumer flatFileEventConsumer;

    @Before
    public void setUp() throws Exception {
        environmentVariables.set("H2O_ASSISTED_CLUSTERING_REST", "true");
        environmentVariables.set("H2O_ASSISTED_CLUSTERING_API_PORT", "9402");

        this.flatFileEventConsumer = new FlatFileEventConsumer();
        assistedClusteringRestApi = new AssistedClusteringRestApi(this.flatFileEventConsumer);
        assistedClusteringRestApi.start();
    }

    @After
    public void tearDown() {
        assistedClusteringRestApi.close();
    }

    @Test
    public void testFlatFileParsing() throws Exception {
        
        final String flatfile = "1200:0000:AB00:1234:0000:2552:7777:1313\n" +
                "[1200:0000:AB00:1234:0000:2552:7777:1313]:54321\n" +
                "9.255.255.255\n" +
                "9.255.255.255:54321";
        final int responseCode = callFlatfileEndpoint(flatfile);
        assertEquals(200, responseCode);
        final Optional<String> lastValueReceived = this.flatFileEventConsumer.getLastValueReceived();
        assertTrue(lastValueReceived.isPresent());
        assertEquals(flatfile, lastValueReceived.get());
    }

    private static int callFlatfileEndpoint(final String flatfile) throws Exception {
        for (int i = 0; i < 10; i++) {
            try {
                final URL statusURL = new URL("http://localhost:9402/cluster/status");
                InputStream con = (InputStream) statusURL.getContent();
                con.close();
            } catch (Exception e) {
                e.printStackTrace();
                Thread.sleep(1000);
            }
        }

        final URL url = new URL("http://localhost:9402/clustering/flatfile");
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
            String text = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            System.out.println("Flatfile endpoint returned: '" + text + "'");
            return con.getResponseCode();
        } finally {
            con.disconnect();
        }
    }
}
