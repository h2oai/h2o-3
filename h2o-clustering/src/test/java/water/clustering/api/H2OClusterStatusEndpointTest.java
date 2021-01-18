package water.clustering.api;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

@RunWith(Enclosed.class)
public class H2OClusterStatusEndpointTest {

    public static class NotClusteredTests {

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
        public void testClusterStatusEndpoint() throws Exception {
            final Response response = callClusterStatusEndpoint();
            assertEquals(204, response.responseCode);
        }

    }

    @RunWith(H2ORunner.class)
    @CloudSize(1)
    public static class ClusteredTests {

        @Test
        public void testClusterStatusEndpoint() throws Exception {
            final Response response = callClusterStatusEndpoint();
            assertEquals(200, response.responseCode);
            assertTrue(response.responseBody.contains("\"unhealthy_nodes\": []"));
            assertTrue(response.responseBody.matches("\\{\n\"healthy_nodes\": \\[\"\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}:\\d{1,5}\"\\],\n\"unhealthy_nodes\": \\[\\]\n\\}"));
        }

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
        final URL url = new URL("http://localhost:8080/cluster/status");
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
