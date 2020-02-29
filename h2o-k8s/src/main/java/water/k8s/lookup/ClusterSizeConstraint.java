package water.k8s.lookup;

import java.util.Set;

/**
 * Constraint triggered when a pre-defined amount of pods is discovered.
 */
public class ClusterSizeConstraint implements LookupConstraint {

    private final int desiredClusterSize;

    public ClusterSizeConstraint(final int desiredClusterSize) {
        this.desiredClusterSize = desiredClusterSize;
    }

    @Override
    public boolean isLookupEnded(final Set<String> discoveredNodes) {
        return discoveredNodes.size() == desiredClusterSize;
    }
}
