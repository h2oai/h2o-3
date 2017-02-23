package water.fvec;

import water.MRTask;

/**
 * Created by vpatryshev on 2/23/17.
 */
public class FrameFilterTask extends MRTask<FrameFilterTask> {
  FrameFilter f;

  FrameFilterTask(FrameFilter f) {
    this.f = f;
  }

  @Override
  public void map(Chunk c, Chunk c2) {
    for (int i = 0; i < c.len(); ++i) {
      if (f.accept(c, i)) c2.set(i, 1);
    }
  }
}
