package water.k8s.lookup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import water.k8s.api.KubernetesRestApi;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KubernetesFlatfileLookupTest {

    private KubernetesRestApi kubernetesRestApi;

    @Before
    public void setUp() throws Exception {
        kubernetesRestApi = new KubernetesRestApi();
        kubernetesRestApi.start();

    }

    @After
    public void tearDown() {
        kubernetesRestApi.close();
    }

    @Test
    public void testFlatfileParsing() throws Exception {
        final String flatfile = "1200:0000:AB00:1234:0000:2552:7777:1313\n" +
                "21DA:D3:0:2F3B:2AA:FF:FE28:9C5A\n" +
                "9.255.255.255";
        final int responseCode = callFlatfileEndpoint(flatfile);
        assertEquals(200, responseCode);
        final Field podIpsField = KubernetesFlatfileLookup.class.getDeclaredField("podIps");
        try {
            podIpsField.setAccessible(true);
            final Optional<Set<String>> podIPs = (Optional<Set<String>>) podIpsField.get(new KubernetesFlatfileLookup());
            assertTrue(podIPs.isPresent());
            assertEquals(3, podIPs.get().size());
        } finally {
            podIpsField.setAccessible(false);
        }
    }

    private static int callFlatfileEndpoint(final String flatfile) throws IOException {
        final URL url = new URL("http://localhost:8080/kubernetes/flatfile");
        final HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Content-Length", String.valueOf(flatfile.length()));
        con.setRequestProperty("Content-Type", "text/plain");
        con.setDoOutput(true);
        con.setRequestMethod("POST");
        try(OutputStream outputStream = con.getOutputStream()) {
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
