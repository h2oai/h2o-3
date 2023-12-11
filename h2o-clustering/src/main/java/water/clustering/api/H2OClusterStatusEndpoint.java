package water.clustering.api;

import water.H2O;
import water.H2ONode;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Set;
import static water.clustering.api.HttpResponses.*;

public class H2OClusterStatusEndpoint implements HttpHandler {

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (!GET_METHOD.equals(httpExchange.getRequestMethod())) {
            newResponseCodeOnlyResponse(httpExchange, HttpURLConnection.HTTP_BAD_METHOD);
        }
        // H2O cluster grows in time, even when a flat file is used. The H2O.CLOUD property might be updated with new nodes during
        // the clustering process and doesn't necessarily have to contain all the nodes since the very beginning of the clustering process.
        // From this endpoint's point of view, H2O is clustered if and only if the H2O cloud members contain all nodes defined in the
        // flat file.

        if (!H2O.isFlatfileEnabled()) {
            newResponseCodeOnlyResponse(httpExchange, HttpURLConnection.HTTP_NO_CONTENT);
            return;
        }
        final Set<H2ONode> flatFile = H2O.getFlatfile();
        final H2ONode[] cloudMembers = H2O.CLOUD.members();
        final boolean clustered = flatFile != null && cloudMembers != null && cloudMembers.length == flatFile.size()
                && flatFile.containsAll(Arrays.asList(cloudMembers));

        if (!clustered) {
            // If there is no cluster, there is no content to report.
            newResponseCodeOnlyResponse(httpExchange, HttpURLConnection.HTTP_NO_CONTENT);
        } else {
            newFixedLengthResponse(httpExchange, HttpURLConnection.HTTP_OK, MIME_TYPE_JSON, nodesListJson());
        }
    }

    /**
     * Construct a JSON representation of healthy and unhealthy H2O nodes.
     * No external libraries are used, as those would make this independent embedded config heavier.
     * No transitive dependencies from h2o-core module are used, as that would create an indirect dependency
     * and prevent future upgrades on API level.
     * <p>
     * Example output:
     * {
     * "healthy_nodes": ["192.168.0.149:54321"],
     * "unhealthy_nodes": []
     * }
     *
     * @return A String with JSON representation of healthy and unhealthy H2O Nodes. Never null.
     */
    private String nodesListJson() {
        final H2ONode[] cloudMembers = H2O.CLOUD.members();

        final StringBuilder healthyNodesStringArray = new StringBuilder();
        final StringBuilder unhealthyNodesStringArray = new StringBuilder();
        int healthyNodeCount = 0;
        int unhealthyNodeCount = 0;
        for (final H2ONode node : cloudMembers) {
            if (node.isHealthy()) {
                healthyNodesStringArray.append('"');
                healthyNodesStringArray.append(node.getIpPortString());
                healthyNodesStringArray.append("\",");
                healthyNodeCount++;
            } else {
                unhealthyNodesStringArray.append('"');
                unhealthyNodesStringArray.append(node.getIpPortString());
                unhealthyNodesStringArray.append('"');
                unhealthyNodesStringArray.append("\",");
                unhealthyNodeCount++;
            }
        }
        if (healthyNodeCount > 0) {
            healthyNodesStringArray.deleteCharAt(healthyNodesStringArray.length() - 1);
        }

        if (unhealthyNodeCount > 0) {
            unhealthyNodesStringArray.deleteCharAt(unhealthyNodesStringArray.length() - 1);
        }

        return String.format("{\n" +
                        "\"leader_node\": \"%s\",\n" +
                        "\"healthy_nodes\": [%s],\n" +
                        "\"unhealthy_nodes\": [%s]\n" +
                        "}", H2O.CLOUD.leader().getIpPortString(),
                healthyNodesStringArray.toString(),
                unhealthyNodesStringArray.toString());
    }
}
