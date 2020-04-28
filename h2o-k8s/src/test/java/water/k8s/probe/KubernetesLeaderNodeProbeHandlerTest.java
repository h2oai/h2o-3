package water.k8s.probe;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import water.H2O;
import water.k8s.KubernetesEmbeddedConfigProvider;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class KubernetesLeaderNodeProbeHandlerTest {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testReadinessProbeOk() throws IOException {
        environmentVariables.set(KubernetesEmbeddedConfigProvider.K8S_DESIRED_CLUSTER_SIZE_KEY,
                String.valueOf(H2O.CLOUD.size()));
        int responseCode = callIsLeaderNode(H2O.CLOUD.leader().getIpPortString());
        assertEquals(HttpURLConnection.HTTP_OK, responseCode);
    }

    @Test
    public void testReadinessProbeLeaderNode() throws IOException {
        environmentVariables.set(KubernetesEmbeddedConfigProvider.K8S_DESIRED_CLUSTER_SIZE_KEY,
                String.valueOf(H2O.CLOUD.size() + 1));
        int responseCode = callIsLeaderNode(H2O.CLOUD.leader().getIpPortString());
        assertEquals(HttpURLConnection.HTTP_OK, responseCode);
    }

    private static int callIsLeaderNode(final String nodeIpPortString) throws IOException {
        final URL url = new URL("http://" + nodeIpPortString + "/3/kubernetes/isLeaderNode");
        final HttpURLConnection con = (HttpURLConnection) url.openConnection();
        try {
            con.setRequestMethod("GET");
            return con.getResponseCode();
        } finally {
            con.disconnect();
        }
    }

}
