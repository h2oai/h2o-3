package water.k8s.lookup;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Constraints triggered once the lookup takes a certain amount of time.
 */
public class TimeoutConstraint implements LookupConstraint {

    private final int timeoutSeconds;
    private final Instant beginning;

    public TimeoutConstraint(final int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        beginning = Instant.now();
    }

    @Override
    public boolean isLookupEnded(final Set<String> discoveredNodes) {
        return Duration.between(beginning, Instant.now()).getSeconds() >= timeoutSeconds;
    }
}
