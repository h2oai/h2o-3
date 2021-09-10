package water.k8s.probe;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import water.H2O;
import water.H2ONode;
import water.k8s.H2OCluster;

import java.util.Map;


public class KubernetesLeaderNodeProbeHandler extends RouterNanoHTTPD.DefaultHandler {

    @Override
    public String getText() {
        throw new IllegalStateException(String.format("Method getText should not be called on '%s'",
                getClass().getName()));
    }

    @Override
    public String getMimeType() {
        return "text/plain";
    }

    @Override
    public NanoHTTPD.Response.IStatus getStatus() {
        throw new IllegalStateException(String.format("Method getMimeType should not be called on '%s'",
                getClass().getName()));
    }

    @Override
    public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
        // All nodes report ready state until the clustering process is finished. Since then, only the leader node is ready.
        final H2ONode self = H2O.SELF;
        if (self == null || self.isLeaderNode() || !H2OCluster.isClustered()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, getMimeType(), null);
        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, getMimeType(), null);
        }
    }

}
