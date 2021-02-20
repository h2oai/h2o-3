package water.clustering.api;

import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class H2OClusterStatusEndpointTest {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private AssistedClusteringRestApi assistedClusteringRestApi;
    private FlatFileEventConsumer flatFileEventConsumer;

    @Before
    public void setUp() throws Exception {
        environmentVariables.set("H2O_ASSISTED_CLUSTERING_REST", "true");
        environmentVariables.set("H2O_ASSISTED_CLUSTERING_API_PORT", "9403");

        this.flatFileEventConsumer = new FlatFileEventConsumer();
        assistedClusteringRestApi = new AssistedClusteringRestApi(this.flatFileEventConsumer);
        assistedClusteringRestApi.start();
    }

    @After
    public void tearDown() {
        assistedClusteringRestApi.close();
    }

    @Test
    public void testClusterStatusEndpoint() throws Exception {
        final Response response = callClusterStatusEndpoint();
        assertEquals(204, response.responseCode);
    }


    private static class Response {
        final int responseCode;
        final String responseBody;

        private Response(final int responseCode, final String responseBody) {
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }
    }

    private static Response callClusterStatusEndpoint() throws IOException {
        final URL url = new URL("http://localhost:9403/cluster/status");
        final HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Content-Type", "text/plain");
        con.setDoOutput(true);
        con.setRequestMethod("GET");
        con.connect();
        try (final InputStream inputStream = con.getInputStream()) {
            String s = IOUtils.toString(inputStream);
            return new Response(con.getResponseCode(), s);
        } finally {
            con.disconnect();
        }
    }
}
