package water.k8s.api;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import water.k8s.H2OCluster;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class KubernetesRestApiTest {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testKubernetesCustomPortBinding() throws IOException {
        environmentVariables.set(KubernetesRestApi.KUBERNETES_REST_API_PORT_KEY, "8081");
        try (final KubernetesRestApi kubernetesRestApi = new KubernetesRestApi()) {
            kubernetesRestApi.start();
            int responseCode = callIsLeaderNode(8081);
            assertFalse(H2OCluster.isClustered());
            assertEquals(200, responseCode);
        }
    }

    @Test
    public void testKubernetesDefaultPortBinding() throws IOException {
        try (final KubernetesRestApi kubernetesRestApi = new KubernetesRestApi()) {
            kubernetesRestApi.start();
            int responseCode = callIsLeaderNode(8080);
            assertFalse(H2OCluster.isClustered());
            assertEquals(200, responseCode);
        }
    }

    private static int callIsLeaderNode(final int nodeIpPort) throws IOException {
        final URL url = new URL("http://localhost:" + nodeIpPort + "/kubernetes/isLeaderNode");
        final HttpURLConnection con = (HttpURLConnection) url.openConnection();
        try {
            con.setRequestMethod("GET");
            return con.getResponseCode();
        } finally {
            con.disconnect();
        }
    }

}
