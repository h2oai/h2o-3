package water.k8s.lookup;

import water.k8s.lookup.LookupConstraint;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface KubernetesLookup {

    /**
     * Looks up H2O pods in K8S cluster.
     *
     * @param lookupConstraints Constraints to obey during lookup
     * @return A {@link Set} of adresses of looked up nodes represented as String. If there are difficulties
     * during node lookup, Optional.empty() is returned.
     */
    Optional<Set<String>> lookupNodes(final Collection<LookupConstraint> lookupConstraints);
}
