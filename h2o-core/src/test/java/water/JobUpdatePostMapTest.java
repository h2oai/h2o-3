package water;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class JobUpdatePostMapTest {

  @Test
  public void call_key() {
    final Job<Frame> j = new Job<>(null, Frame.class.getName(), null);
    j.start(new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        assertEquals(0.0, j.progress(), 0);
        JobUpdatePostMap action = JobUpdatePostMap.forJob(j);
        action.call(Key.make());
        assertEquals(0.1, j.progress(), 1e-6);
        tryComplete();
      }
    }, 10L).get();
  }

  @Test
  public void call_chunks() {
    final Job<Frame> j = new Job<>(null, Frame.class.getName(), null);
    j.start(new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        assertEquals(0.0, j.progress(), 0);
        int len = 21;
        JobUpdatePostMap action = JobUpdatePostMap.forJob(j);
        Chunk[] cs = new Chunk[]{new NewChunk(new double[len])};
        action.call(cs);
        assertEquals(0.5, j.progress(), 1e-6);
        tryComplete();
      }
    }, 42L).get();
  }

  @Test
  public void forJob() {
    Job<Frame> j = new Job<>(null, Frame.class.getName(), null);
    assertNull(JobUpdatePostMap.forJob(null));
    assertNotNull(JobUpdatePostMap.forJob(j));
  }

}
