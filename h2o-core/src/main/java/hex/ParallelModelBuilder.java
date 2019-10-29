package hex;

import jsr166y.ForkJoinTask;
import water.util.IcedAtomicInt;
import water.util.Log;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Dispatcher for parallel model building. Starts building models every time the run method is invoked.
 * After each model is finished building, the `modelFinished` method is invoked, which in turn invokes modelFeeder callback.
 * ModelFeeder receives the model built and can deal with it in any way - e.g. put it into a Grid, or discard it if the resulting model
 * is a failure. It also has the power to invoke the training of any number of new models. Or stop the parallel model builder,
 * released the barrier inside.
 */
public class ParallelModelBuilder extends ForkJoinTask<ParallelModelBuilder> {

  private BiConsumer<ModelBuildingResult, ParallelModelBuilder> _modelFeeder;
  public IcedAtomicInt _modelInProgressCounter = new IcedAtomicInt();
  public AtomicBoolean _completed = new AtomicBoolean(false);

  public ParallelModelBuilder(BiConsumer<ModelBuildingResult, ParallelModelBuilder> modelFeeder) {
    Objects.requireNonNull(modelFeeder);
    _modelFeeder = modelFeeder;
  }

  /**
   * Runs given collection of {@link ModelBuilder} in parallel. After each model is finished building,
   * one of the callbacks (on model failure / on model completion) is called.
   *
   * @param modelBuilders An {@link Collection} of {@link ModelBuilder} to execute in parallel.
   */
  public void run(Collection<ModelBuilder> modelBuilders) {
      for (final ModelBuilder modelBuilder : modelBuilders) {
        _modelInProgressCounter.incrementAndGet();
        final Consumer<ModelBuildingResult> consumer = this::onModelFinished;
        modelBuilder.trainModel(consumer);
      }
  }

  private void onModelFinished(final ModelBuildingResult m) {
    _modelInProgressCounter.decrementAndGet();
    _modelFeeder.accept(m, this);
  }

  /**
   * Indicate this builder there will be no more models. After this methods i called (by whoever calls it first),
   * it waits for all other models to finish execution and then returns, completing the job represented by the very instance of
   * {@link ParallelModelBuilder}. Any threads waiting for the instance of {@link ParallelModelBuilder} to finish (via join)
   * will be notified.
   */
  public void noMoreModels() {
    // Prevent completing te parallel model builder twice
    final boolean previouslyCompleted = _completed.getAndSet(true);
    if(previouslyCompleted) return;

    do {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Log.err(e);
      }
    } while (_modelInProgressCounter.get() > 0);
    complete(this);
  }


  @Override
  public ParallelModelBuilder getRawResult() {
    return this;
  }

  @Override
  protected void setRawResult(ParallelModelBuilder value) {
  }

  @Override
  protected boolean exec() {
    return false;
  }
}
