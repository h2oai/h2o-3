package hex.encoding;

import water.H2O;
import water.Iced;
import water.Job;
import water.Key;
import water.fvec.Frame;

abstract class BaseCategoricalEncoder extends Iced implements CategoricalEncoder {

  @Override
  public Frame encode(Frame fr, String[] skipCols) {
    return exec(fr, skipCols).get();
  }

  Job<Frame> exec(Frame fr, String[] skipCols) {
    if (fr == null)
      throw new IllegalArgumentException("Frame doesn't exist.");
    Key<Frame> destKey = Key.makeSystem(Key.make().toString());
    Job<Frame> job = new Job<>(destKey, Frame.class.getName(), getClass().getSimpleName());
    int workAmount = fr.lastVec().nChunks();
    H2O.H2OCountedCompleter completer = newDriver(fr, destKey, skipCols);
    return job.start(completer, workAmount);
  }

  abstract H2O.H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols);
}
