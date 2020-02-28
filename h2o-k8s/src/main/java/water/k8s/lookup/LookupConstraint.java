package water.k8s.lookup;

import java.util.Set;

public interface LookupConstraint {

    /**
     * @param lookedUpNodes A set of unique string representations of the nodes discovered
     * @return True if after the recent node discovery, the lookup should be over
     */
    boolean isLookupEnded(final Set<String> lookedUpNodes);

}
