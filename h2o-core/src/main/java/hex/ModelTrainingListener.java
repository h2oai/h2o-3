package hex;

import java.util.concurrent.LinkedBlockingQueue;

public class ModelTrainingListener {

    public enum Event {ONE_DONE, ALL_DONE}

    private final LinkedBlockingQueue<Event> _events;

    public ModelTrainingListener(LinkedBlockingQueue<Event> events) {
        _events = events;
    }

    public void onIterationComplete() {
        _events.add(Event.ONE_DONE);
    }

    public void onAllIterationsComplete() {
        _events.add(Event.ALL_DONE);
    }

}
