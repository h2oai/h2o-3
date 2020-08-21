package water;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.fvec.Frame;

import static org.junit.Assert.*;

public class JobTest extends TestUtil {

  @Rule
  public ExpectedException ee = ExpectedException.none();

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void setWork() {
    final Job<Frame> j = new Job<>(Key.make(), Frame.class.getName(), "Test Job");

    H2O.H2OCountedCompleter worker = new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        j.setWork(42L);
        j.update(21L);
        assertEquals(0.5, j.progress(), 0);
        tryComplete();
      }
    };
    
    j.start(worker, Job.WORK_UNKNOWN).get();

    assertEquals(42L, j.getWork());
  }

  @Test
  public void setWorkIllegal() {
    final Job<Frame> j = new Job<>(Key.make(), Frame.class.getName(), "Test Job");

    H2O.H2OCountedCompleter worker = new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        j.setWork(13);
        tryComplete();
      }
    };

    ee.expect(IllegalStateException.class);
    ee.expectMessage("Cannot set work amount if it was already previously specified");
    
    j.start(worker, 12).get();
  }

  @Test
  public void jobStatus() {
    final Job<Frame> j = new Job<>(Key.make(), Frame.class.getName(), "Test Job");
    
    assertEquals(Job.JobStatus.PENDING, j.getStatus());
    j.start(new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        assertEquals(Job.JobStatus.RUNNING, j.getStatus());
        tryComplete();
      }
    }, 1).get();
    assertEquals(Job.JobStatus.SUCCEEDED, j.getStatus());
  }

  @Test
  public void jobStatusFailed() {
    final Job<Frame> j = new Job<>(Key.make(), Frame.class.getName(), "Test Job");

    ee.expectMessage("Test Job Failure");
    
    try {
      j.start(new H2O.H2OCountedCompleter() {
        @Override
        public void compute2() {
          throw new IllegalStateException("Test Job Failure");
        }
      }, 1).get();
    } finally {
      assertEquals(Job.JobStatus.FAILED, j.getStatus());
    }
  }

  @Test
  public void jobStatusStopped() {
    final Job<Frame> j = new Job<>(Key.make(), Frame.class.getName(), "Test Job");

    ee.expect(Job.JobCancelledException.class);

    try {
      j.start(new H2O.H2OCountedCompleter() {
        @Override
        public void compute2() {
          j.stop();
          assertTrue(j.stop_requested());
          throw new Job.JobCancelledException();
        }
      }, 1).get();
    } finally {
      assertEquals(Job.JobStatus.STOPPED, j.getStatus());
    }
  }
  
}
