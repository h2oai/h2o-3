package water.k8s.lookup;

import java.util.Set;

/**
 * A constraint during Pod lookup in Kubernetes cluster. Each implementation represents a single rule to constraint
 * the lookup with.
 */
public interface LookupConstraint {

    /**
     * @param lookedUpNodes A set of unique string representations of the nodes discovered
     * @return True if after the recent node discovery, the lookup should be ended.
     */
    boolean isLookupEnded(final Set<String> lookedUpNodes);

}
