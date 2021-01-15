package water.clustering.api;

import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.Assert.assertNotNull;

public class FlatFileEventConsumer implements Consumer<String> {
    private Optional<String> lastValueReceived = Optional.empty();

    public void accept(final String ips) {
        assertNotNull(ips);
        lastValueReceived = Optional.of(ips);
    }

    public Optional<String> getLastValueReceived() {
        return lastValueReceived;
    }
}
