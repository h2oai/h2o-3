package hex;

import java.util.concurrent.BlockingQueue;

public class ModelTrainingEventsPublisher {

    public enum Event {ONE_DONE, ALL_DONE}

    private final BlockingQueue<Event> _events;

    public ModelTrainingEventsPublisher(BlockingQueue<Event> events) {
        _events = events;
    }

    public void onIterationComplete() {
        _events.add(Event.ONE_DONE);
    }

    public void onAllIterationsComplete() {
        _events.add(Event.ALL_DONE);
    }

}
