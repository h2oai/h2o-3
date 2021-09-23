package water.server;

import water.H2O;
import water.init.AbstractEmbeddedH2OConfig;
import water.webserver.iface.RequestAuthExtension;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Only the H2O leader node should be queried by the end user at any time (reproducibility). While running eg. on Kubernetes,
 * it is easy for to misconfigure services and let the traffic flow to non-leader nodes. If an H2O cluster is used
 * in such a way, the results provided to the user might be irreproducible/incorrect and the user has no way to easily find out.
 * <p>
 * To solve this issue, this filter blocks all incoming requests on non-leader nodes when H2O is configured to have
 * non-leader node access disabled.
 * Some APIs intended for internal use (mainly used by other products such as Sparkling Water) might still remain active.
 * All user-facing APIs, mostly the ones used by Python/R/Flow clients are disabled.
 */
public class LeaderNodeRequestFilter implements RequestAuthExtension {

    private final Set<String> allowedContextPaths;

    public LeaderNodeRequestFilter() {
        // Allowed paths are copied into a HashSet to guarantee constant search time and lowest possible memory
        // overhead, as the LinkedKeySet inside the LinkedHashMap returned by getAlwaysEnabledServlets() method
        // maintains order, which is not required for the needs of LeaderNodeRequestFilter.
        allowedContextPaths = new HashSet<>(ServletService.INSTANCE.getAlwaysEnabledServlets()
                .keySet());

    }

    @Override
    public boolean handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (H2O.SELF == null) {
            // If H2O clustering process has not yet finished, disable API on all nodes to prevent premature cluster locking.
            // Send HTTP 403 - Forbidden and indicate the clustering process has not finished yet.
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "H2O Node didn't start yet. H2O API is inaccessible at the moment.");
        } else if (H2O.SELF.isLeaderNode()) {
            // If clustering is finished and this node is the leader node, than the request landed correctly.
            // Mark as not handled by this filter and do nothing - the request will be handled by the rest of the servlet
            // chain.
            return false;
        } else if (allowedContextPaths.contains(target)) {
            // If this is not the leader node, yet it is the part of API that should be enabled on every node, indicate this request
            // not been handled by this filter and do nothing.
            return false;
        }

        // If API is disabled on this node and the context path of the request is not listed in allowedPaths,
        // Then send HTTP 403 - Forbidden and indicate the request has been handled by this filter.
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Deployment configuration error - request reached a non-leader H2O node.");
        return true;
    }

    @Override
    public boolean isEnabled() {
        AbstractEmbeddedH2OConfig config = H2O.getEmbeddedH2OConfig();
        return config != null && config.disableNonLeaderNodeAccess();
    }

}
