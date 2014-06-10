package water;

import jsr166y.CountedCompleter;
import org.apache.hadoop.ipc.*;
import org.junit.BeforeClass;
import org.junit.Test;
import water.api.TimelineHandler;
import water.api.TimelineHandler;
import water.schemas.TimelineV2;
import water.util.TimelineSnapshot;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by tomasnykodym on 6/9/14.
 */
public class TimelineTest extends TestUtil{
  @BeforeClass
  public static void stall() { stall_till_cloudsize(5); }
  // simple class to test the timeline
  // we want to send this task around and see that timeline shows this and in correct order
  private static class TestTask extends DTask {
    @Override
    protected void compute2() {
      // nothing to do here...
      tryComplete();
    }
  }
  private static class TestLauncher extends DTask {
    public final H2ONode tgt;
    public TestLauncher (H2ONode tgt, H2O.H2OCountedCompleter cmp) {
      super(cmp);
      this.tgt = tgt;
    }
    public TestLauncher (H2ONode tgt){
      this.tgt = tgt;
    }
    @Override
    protected void compute2() {
      RPC.call(tgt,new TestTask()).addCompleter(this).call();
    }

    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller){
      ex.printStackTrace();;
      return true;
    }

  }
  // make a test task and see it gets shown in the timeline
  @Test
  public void basicTest(){
    final int n = H2O.CLOUD.size();
    H2O.H2OCountedCompleter test = new H2O.H2OCountedCompleter() {
      @Override
      protected void compute2() {
        if (H2O.CLOUD.size() > 1) {
          for (H2ONode from : H2O.CLOUD.members()) {
            for (H2ONode to : H2O.CLOUD.members()) {
              if (from == to) continue;
              addToPendingCount(1);
              if(from != H2O.SELF) {
                RPC.call(from, new TestLauncher(to)).addCompleter(this).call();
              } else {
                new TestLauncher(to,this).fork();
              }
            }
          }
        } // otherwise nothing to test, no one to send msgs to...
        tryComplete();
      }
    };
    H2O.submitTask(test).join();
    TimelineHandler handler = new TimelineHandler();
    handler.compute2();
    Set<String> msgs = new HashSet<String>();
    for( TimelineV2.Event e : new TimelineV2().fillFrom(handler).events ) {
      if(e.bytes().contains("TestTask") && e instanceof TimelineV2.NetworkEvent) {
        TimelineV2.NetworkEvent ne = (TimelineV2.NetworkEvent)e;
        msgs.add((ne.isSend?"SEND":"RECV")  + " " + ne.from + " -> " + ne.to);
      }
    }
    // crude test for now, just look we got send and recv message for all test dtasks we made
    // we should also test the order and acks/ackacks!
    assertEquals("some msgs are missing from the timeline: " + msgs.toString(),msgs.size(),2*n*(n-1));
  }
}
