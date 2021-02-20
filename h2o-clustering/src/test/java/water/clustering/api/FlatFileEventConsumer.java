package water.clustering.api;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FlatFileEventConsumer implements Consumer<String> {
    private final BlockingQueue<String> lastValueReceived = new LinkedBlockingQueue<>(1);

    public void accept(final String ips) {
        lastValueReceived.add(ips);
    }

    public Optional<String> getLastValueReceived() {
        try {
            String s = lastValueReceived.poll(30, TimeUnit.SECONDS);
            return Optional.ofNullable(s);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
