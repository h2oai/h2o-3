package water.k8s.probe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import water.k8s.H2OCluster;

import java.io.IOException;

import static java.net.HttpURLConnection.*;

public class KubernetesLeaderNodeProbeHandler implements HttpHandler {

    public static final String MIME_TYPE_TEXT_PLAIN = "text/plain";
    public static final String GET_METHOD = "GET";

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (!GET_METHOD.equals(httpExchange.getRequestMethod())) {
            httpResponseWithoutBody(httpExchange, HTTP_BAD_METHOD);
        }
        // All nodes report ready state until the clustering process is finished. Since then, only the leader node is ready.
        final H2OCluster.H2ONodeInfo self = H2OCluster.getCurrentNodeInfo();
        if (self == null || self.isLeader() || !H2OCluster.isClustered()) {
            httpResponseWithoutBody(httpExchange, HTTP_OK);
        } else {
            httpResponseWithoutBody(httpExchange, HTTP_NOT_FOUND);
        }
    }

    private static void httpResponseWithoutBody(HttpExchange httpExchange, int httpResponseCode) throws IOException {
        httpExchange.getResponseHeaders().set("Content-Type", MIME_TYPE_TEXT_PLAIN);
        httpExchange.sendResponseHeaders(httpResponseCode, -1);
        httpExchange.close();
    }

}
