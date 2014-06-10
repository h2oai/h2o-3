package water.api;

import water.H2O;
import water.TimeLine;
import water.schemas.TimelineV2;
import water.util.TimelineSnapshot;

import java.util.ArrayList;

/**
 * Created by tomasnykodym on 6/5/14.
 */
public class TimelineHandler extends Handler<TimelineHandler,TimelineV2> {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  transient TimelineSnapshot.Event [] _events;
  public TimelineSnapshot snapshot;

  @Override protected TimelineV2 schema(int version) {  return new TimelineV2(); }
  @Override  public void compute2() { snapshot = new TimelineSnapshot(/*TimeLine.getCLOUD()*/ H2O.CLOUD,TimeLine.system_snapshot());}
}
