package hex;

import water.H2O;
import water.ParallelizationTask;

public class ModelBuilderHelper {

  /**
   * A helper method facilitating parallel training of a collection of models.
   * 
   * @param mbs an array of ModelBuilders ready to be trained (ideally with parameters already validated) 
   * @param parallelization level of parallelization - we will run up to #parallelization models concurrently
   * @param <E> type of ModelBuilder
   * @return finished ModelBuilders
   */
  public static <E extends ModelBuilder<?, ?, ?>> E[] trainModelsParallel(E[] mbs, int parallelization) {
    TrainModelTask[] tasks = new TrainModelTask[mbs.length]; 
    for (int i = 0; i < mbs.length; i++) {
      tasks[i] = new TrainModelTask(mbs[i]);
    }
    H2O.submitTask(new ParallelizationTask<>(tasks, parallelization, null)).join();
    return mbs;
  }

  /**
   * Simple wrapper around ModelBuilder#trainModel; we could alternatively get the H2OCompleter used
   * by the model builder but then we would need to deal with managing Job's lifecycle.
   */
  private static class TrainModelTask extends H2O.H2OCountedCompleter<TrainModelTask> {
    private final ModelBuilder<?, ?, ?> _mb;

    TrainModelTask(ModelBuilder<?, ?, ?> mb) {
      _mb = mb;
    }

    @Override
    public void compute2() {
      _mb.trainModel().get();
      tryComplete();
    }
  }

}
