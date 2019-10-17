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
 *
 * 
 */
public class ParallelModelBuilder extends ForkJoinTask<ParallelModelBuilder> {

  private BiConsumer<Model, ParallelModelBuilder> _modelFeeder;
  public IcedAtomicInt _modelInProgressCounter = new IcedAtomicInt();
  public AtomicBoolean _completed = new AtomicBoolean(false);

  public ParallelModelBuilder(BiConsumer<Model, ParallelModelBuilder> modelFeeder) {
    Objects.requireNonNull(modelFeeder);
    _modelFeeder = modelFeeder;
  }

  public void run(ModelBuilder[] modelBuilders) {
      for (final ModelBuilder modelBuilder : modelBuilders) {
        _modelInProgressCounter.incrementAndGet();
        final Consumer<Model> consumer = this::modelFinished;
        modelBuilder.trainModel(consumer);
      }

  }

  private void modelFinished(final Model m) {
    _modelInProgressCounter.decrementAndGet();
    _modelFeeder.accept(m, this);
  }

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
    return null;
  }

  @Override
  protected void setRawResult(ParallelModelBuilder value) {

  }

  @Override
  protected boolean exec() {
    return true;
  }
}
