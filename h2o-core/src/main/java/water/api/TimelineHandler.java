package water.api;

import water.H2O;
import water.Iced;
import water.api.TimelineHandler.Timeline;
import water.TimeLine;
import water.init.TimelineSnapshot;

/** UDP Timeline
 * Created by tomasnykodym on 6/5/14.
 */
public class TimelineHandler extends Handler<Timeline,TimelineV2> {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public static final class Timeline extends Iced {
    TimelineSnapshot snapshot;
  }

  @Override protected TimelineV2 schema(int version) {  return new TimelineV2(); }
  @Override public void compute2() { throw H2O.unimpl(); }

  // TODO: should return a base class for TimelineVx
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public TimelineV2 fetch(int version, Timeline t) {
    t.snapshot = new TimelineSnapshot(H2O.CLOUD,TimeLine.system_snapshot());
    return schema(version).fillFromImpl(t);
  }
}
