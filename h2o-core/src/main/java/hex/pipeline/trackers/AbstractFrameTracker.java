package hex.pipeline.trackers;

import hex.pipeline.FrameTracker;
import water.Iced;

/**
 * This abstract class just makes it easier to provide good serialization support for its subclasses.
 * The no-arg public constructor is implemented as a reminder that subclasses need to override it.
 */
public abstract class AbstractFrameTracker<T extends AbstractFrameTracker> extends Iced<T> implements FrameTracker {

  public AbstractFrameTracker() {
    super();
  }
}
