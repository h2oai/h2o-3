package water.clustering.api;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.Assert.*;

public class AssistedClusteringEndpointTest {

    private AssistedClusteringRestApi assistedClusteringRestApi;
    private FlatFileEventConsumer flatFileEventConsumer;

    @Before
    public void setUp() throws Exception {
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

    private static int callFlatfileEndpoint(final String flatfile) throws IOException {
        final URL url = new URL("http://localhost:8080/clustering/flatfile");
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
