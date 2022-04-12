package hex.encoding;

import hex.DataTransformSupport;
import water.H2O;
import water.Iced;
import water.Job;
import water.Key;
import water.fvec.Frame;

abstract class BaseCategoricalEncoder extends Iced implements CategoricalEncoder {

  @Override
  public Frame encode(Frame fr, String[] skippedCols, Stage stage, DataTransformSupport params) {
    String[] skipped = skippedCols == null ? new String[0] : skippedCols;
    return exec(fr, skipped).get();
  }

  Job<Frame> exec(Frame fr, String[] skippedCols) {
    if (fr == null)
      throw new IllegalArgumentException("Frame doesn't exist.");
    Key<Frame> destKey = Key.makeSystem(Key.make().toString());
    Job<Frame> job = new Job<>(destKey, Frame.class.getName(), getClass().getSimpleName());
    int workAmount = fr.lastVec().nChunks();
    H2O.H2OCountedCompleter completer = newDriver(fr, destKey, skippedCols);
    return job.start(completer, workAmount);
  }

  abstract H2O.H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skippedCols);
}
