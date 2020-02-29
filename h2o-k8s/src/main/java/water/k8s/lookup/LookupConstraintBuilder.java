package water.k8s.lookup;

import water.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LookupConstraintBuilder {
    private static final int K8S_DEFAULT_CLUSTERING_TIMEOUT_SECONDS = 120;
    private Integer timeoutSeconds;
    private Integer desiredClusterSize;

    public LookupConstraintBuilder() {
        this.timeoutSeconds = null;
        this.desiredClusterSize = null;
    }

    public LookupConstraintBuilder withTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public LookupConstraintBuilder withDesiredClusterSize(final int desiredClusterSize) {
        this.desiredClusterSize = desiredClusterSize;
        return this;
    }

    public Collection<LookupConstraint> build() {

        final List<LookupConstraint> lookupConstraintList = new ArrayList<>();

        if (timeoutSeconds == null && desiredClusterSize == null) {
            Log.info(String.format("No H2O Node discovery timeout set. Using default timeout of %d seconds.",
                    K8S_DEFAULT_CLUSTERING_TIMEOUT_SECONDS));
            lookupConstraintList.add(new TimeoutConstraint(K8S_DEFAULT_CLUSTERING_TIMEOUT_SECONDS));
        }

        if (timeoutSeconds != null) {
            Log.info(String.format("Timeout for node discovery is set to %d seconds.", timeoutSeconds));
            lookupConstraintList.add(new TimeoutConstraint(timeoutSeconds));
        }
        if (desiredClusterSize != null) {
            Log.info(String.format(String.format("Desired cluster size is set to %d nodes.", desiredClusterSize)));
            lookupConstraintList.add(new ClusterSizeConstraint(desiredClusterSize));
        }
        return lookupConstraintList;
    }
}
