package water;

import jsr166y.CountedCompleter;
import org.junit.Ignore;
import org.junit.Test;
import water.api.TimelineHandler;
import water.api.schemas3.TimelineV3;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;

public class TimelineTest extends TestUtil{
  public TimelineTest() { super(5); }

  // Simple class to test the timeline.  We want to send this task around and
  // see that timeline shows this and in correct order.  An instance is sent
  // from all nodes to all nodes (full cross-bar).
  private static class TestTask extends DTask {
    // nothing to do here...
    @Override
    public void compute2() { tryComplete(); }
  }

  // RPC call of above simple class from here to 'tgt'
  private static class TestLauncher extends DTask {
    final H2ONode _tgt;
    TestLauncher (H2ONode tgt, H2O.H2OCountedCompleter cmp) {  super(cmp); _tgt = tgt; }
    TestLauncher (H2ONode tgt){ _tgt = tgt; }
    @Override
    public void compute2() {
      new RPC(_tgt,new TestTask()).addCompleter(this).call();
    }

    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller){
      ex.printStackTrace();
      return true;
    }

  }
  // fixme: ignored for now, faling on java 8 (timeline is missing some of the sent messages)
  // make a test task and see it gets shown in the timeline
  @Test @Ignore
  public void basicTest(){
    final int n = H2O.CLOUD.size();
    // Make a CountedCompleter, so we can have lots of pending tasks for which
    // this is the completer.  When they all complete, this one completes as well.
    H2O.H2OCountedCompleter test = new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        if( H2O.CLOUD.size() > 1) {
          for( H2ONode from : H2O.CLOUD.members() ) {
            for( H2ONode to : H2O.CLOUD.members() ) {
              if( from == to ) continue;
              addToPendingCount(1);
              if( from != H2O.SELF ) {
                new RPC(from, new TestLauncher(to)).addCompleter(this).call();
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
    TimelineV3 t = handler.fetch(2, new TimelineV3());
    Set<String> msgs = new HashSet<>();
    for( TimelineV3.EventV3 e : t.events) {
      if(e.bytes().contains("TestTask") && e instanceof TimelineV3.NetworkEvent) {
        TimelineV3.NetworkEvent ne = (TimelineV3.NetworkEvent)e;
        msgs.add((ne.is_send ?"SEND":"RECV")  + " " + ne.from + " -> " + ne.to);
      }
    }
    // crude test for now, just look we got send and recv message for all test dtasks we made
    // we should also test the order and acks/ackacks!
    assertEquals("some msgs are missing from the timeline: epxected " + (2*n*(n-1)) + ", got " + msgs.size()  + ", msgs = " + msgs.toString(),msgs.size(),2*n*(n-1));
  }
}
