package water.api;

import water.H2O;
import water.TimeLine;
import water.schemas.TimelineV2;
import water.util.TimelineSnapshot;

import java.util.ArrayList;

/**
 * Created by tomasnykodym on 6/5/14.
 */
public class Timeline extends Handler<Timeline,TimelineV2> {
  TimelineSnapshot.Event [] _events;
  @Override
  protected TimelineV2 schema(int version) {
    return new TimelineV2();
  }
  public TimelineSnapshot snapshot;
  @Override
  protected void compute2() { snapshot = new TimelineSnapshot(TimeLine.getCLOUD(),TimeLine.system_snapshot());}
}
