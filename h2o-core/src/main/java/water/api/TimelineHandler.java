package water.api;

import water.H2O;
import water.Iced;
import water.TimeLine;
import water.init.TimelineSnapshot;

/** UDP Timeline
 * Created by tomasnykodym on 6/5/14.
 */
public class TimelineHandler extends Handler {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public static final class Timeline extends Iced {
    TimelineSnapshot snapshot;
  }

  // TODO: should return a base class for TimelineVx
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public TimelineV2 fetch(int version, TimelineV2 s) {
    Timeline t = s.createAndFillImpl();
    t.snapshot = new TimelineSnapshot(H2O.CLOUD,TimeLine.system_snapshot());
    return s.fillFromImpl(t);
  }
}
