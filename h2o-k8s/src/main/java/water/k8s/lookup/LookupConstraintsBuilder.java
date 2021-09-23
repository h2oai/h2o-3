package water.k8s.lookup;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builder for lookup constraints. For different input/configuration, this builder outputs the exact set of instances
 * of {@link LookupConstraint} to meet user's requirements.
 */
public class LookupConstraintsBuilder {

    private static final Logger LOG = Logger.getLogger(KubernetesDnsLookup.class);

    private static final int K8S_DEFAULT_CLUSTERING_TIMEOUT_SECONDS = 180;
    private Integer timeoutSeconds;
    private Integer desiredClusterSize;

    public LookupConstraintsBuilder() {
        this.timeoutSeconds = null;
        this.desiredClusterSize = null;
    }

    /**
     * @param timeoutSeconds Timeout in seconds. Inserting a null value resets the timeout settings.
     * @return The very instance of {@link LookupConstraintsBuilder} called (builder pattern).
     */
    public LookupConstraintsBuilder withTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    /**
     * @param desiredClusterSize Desired amount of pods discovered. Inserting a null value resets the desired cluster
     *                           size.
     * @return The very instance of {@link LookupConstraintsBuilder} called (builder pattern).
     */
    public LookupConstraintsBuilder withDesiredClusterSize(final int desiredClusterSize) {
        this.desiredClusterSize = desiredClusterSize;
        return this;
    }

    /**
     * Construct a never-empty collection of {@link LookupConstraint} instances. By guaranteeing the resulting collection
     * to be never empty, it is ensured the H2O Node lookup on available pods will always end in a reasonably finite time.
     *
     * @return A {@link Collection} of {@link LookupConstraint}. The collection is never empty.
     */
    public Collection<LookupConstraint> build() {

        final List<LookupConstraint> lookupConstraintList = new ArrayList<>();

        // If there are no constraints set by the user via environment variables, use a sensible timeout.
        if (timeoutSeconds == null && desiredClusterSize == null) {
            LOG.info(String.format("No H2O Node discovery constraints set. Using default timeout of %d seconds.",
                    K8S_DEFAULT_CLUSTERING_TIMEOUT_SECONDS));
            lookupConstraintList.add(new TimeoutConstraint(K8S_DEFAULT_CLUSTERING_TIMEOUT_SECONDS));
        }

        if (timeoutSeconds != null) {
            LOG.info(String.format("Timeout for node discovery is set to %d seconds.", timeoutSeconds));
            lookupConstraintList.add(new TimeoutConstraint(timeoutSeconds));
        }
        if (desiredClusterSize != null) {
            LOG.info(String.format(String.format("Desired cluster size is set to %d nodes.", desiredClusterSize)));
            lookupConstraintList.add(new ClusterSizeConstraint(desiredClusterSize));
        }
        return lookupConstraintList;
    }
}
