package water.k8s.lookup;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class LookupConstraintsBuilderTest {

    private LookupConstraintsBuilder lookupConstraintsBuilder;
    private Set<String> lookedUpNodes;

    @Before
    public void beforeTest() {
        lookupConstraintsBuilder = new LookupConstraintsBuilder();
        lookedUpNodes = new HashSet<>();
    }

    @Test
    public void testTimeoutOnly() {
        final Collection<LookupConstraint> lookupStrategies = lookupConstraintsBuilder.withTimeoutSeconds(0)
                .build();

        assertEquals(1, lookupStrategies.size());
        assertTrue(lookupStrategies.stream().allMatch(lookupStrategy -> lookupStrategy instanceof TimeoutConstraint));
        assertTrue(lookupStrategies.stream().allMatch(strategy -> strategy.isLookupEnded(lookedUpNodes)));
    }

    @Test
    public void testTimeoutOnlyRunning() {
        final Collection<LookupConstraint> lookupStrategies = lookupConstraintsBuilder.withTimeoutSeconds(Integer.MAX_VALUE)
                .build();

        assertEquals(1, lookupStrategies.size());
        assertTrue(lookupStrategies.stream().allMatch(lookupStrategy -> lookupStrategy instanceof TimeoutConstraint));
        assertFalse(lookupStrategies.stream().allMatch(strategy -> strategy.isLookupEnded(lookedUpNodes)));
    }

    @Test
    public void testClusterSize() {
        final Collection<LookupConstraint> lookupStrategies = this.lookupConstraintsBuilder.withDesiredClusterSize(2)
                .build();
        assertEquals(1, lookupStrategies.size());
        assertTrue(lookupStrategies.stream().allMatch(lookupStrategy -> lookupStrategy instanceof ClusterSizeConstraint));
        lookedUpNodes.add("ABCD");
        assertFalse(lookupStrategies.stream().allMatch(strategy -> strategy.isLookupEnded(lookedUpNodes)));

        lookedUpNodes.add("EFGE");
        assertTrue(lookupStrategies.stream().allMatch(strategy -> strategy.isLookupEnded(lookedUpNodes)));
    }

    @Test
    public void testTimeoutAndClusterSize() {
        final Collection<LookupConstraint> lookupStrategies = this.lookupConstraintsBuilder.withDesiredClusterSize(1)
                .withTimeoutSeconds(1)
                .build();
        assertEquals(2, lookupStrategies.size());
        assertEquals(1, lookupStrategies.stream().filter(lookupStrategy -> lookupStrategy instanceof TimeoutConstraint).count());
        assertEquals(1, lookupStrategies.stream().filter(lookupStrategy -> lookupStrategy instanceof ClusterSizeConstraint).count());
    }

    @Test
    public void testNoConstraints() {
        final Collection<LookupConstraint> lookupStrategies = lookupConstraintsBuilder.build();

        assertEquals(1, lookupStrategies.size());
        assertTrue(lookupStrategies.stream().allMatch(lookupStrategy -> lookupStrategy instanceof TimeoutConstraint));
        assertFalse(lookupStrategies.stream().allMatch(strategy -> strategy.isLookupEnded(lookedUpNodes)));
    }

}
