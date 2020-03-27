package hex;

import jsr166y.ForkJoinTask;
import water.Iced;
import water.util.IcedAtomicInt;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatcher for parallel model building. Starts building models every time the run method is invoked.
 * After each model is finished building, the `modelFinished` method is invoked, which in turn invokes modelFeeder callback.
 * ModelFeeder receives the model built and can deal with it in any way - e.g. put it into a Grid, or discard it if the resulting model
 * is a failure. It also has the power to invoke the training of any number of new models. Or stop the parallel model builder,
 * released the barrier inside.
 */
public class ParallelModelBuilder extends ForkJoinTask<ParallelModelBuilder> {

  public static abstract class ParallelModelBuilderCallback<D extends ParallelModelBuilderCallback> extends Iced<D> {

    public abstract void onBuildSuccess(final Model model, final ParallelModelBuilder parallelModelBuilder);

    public abstract void onBuildFailure(final ModelBuildFailure modelBuildFailure, final ParallelModelBuilder parallelModelBuilder);
  }

  private final transient ParallelModelBuilderCallback _callback;
  private final transient IcedAtomicInt _modelInProgressCounter = new IcedAtomicInt();
  private final transient AtomicBoolean _completed = new AtomicBoolean(false);
  private final transient ParallelModelBuiltListener _parallelModelBuiltListener;

  public ParallelModelBuilder(final ParallelModelBuilderCallback callback) {
    Objects.requireNonNull(callback);
    _callback = callback;
    _parallelModelBuiltListener = new ParallelModelBuiltListener();
  }

  /**
   * Runs given collection of {@link ModelBuilder} in parallel. After each model is finished building,
   * one of the callbacks (on model failure / on model completion) is called.
   *
   * @param modelBuilders An {@link Collection} of {@link ModelBuilder} to execute in parallel.
   */
  public void run(final Collection<ModelBuilder> modelBuilders) {
      for (final ModelBuilder modelBuilder : modelBuilders) {
        _modelInProgressCounter.incrementAndGet();

        // Set the callbacks
        modelBuilder.setModelBuilderListener(_parallelModelBuiltListener);
        modelBuilder.trainModel();
      }
  }


  private class ParallelModelBuiltListener extends ModelBuilderListener<ParallelModelBuiltListener> {

    @Override
    public void onModelSuccess(Model model) {
      try {
        _callback.onBuildSuccess(model, ParallelModelBuilder.this);
      } finally {
        _modelInProgressCounter.decrementAndGet();
      }
      attemptComplete();
    }

    @Override
    public void onModelFailure(Throwable cause, Model.Parameters parameters) {
      try {
        final ModelBuildFailure modelBuildFailure = new ModelBuildFailure(cause, parameters);
        _callback.onBuildFailure(modelBuildFailure, ParallelModelBuilder.this);
      } finally {
        _modelInProgressCounter.decrementAndGet();
      }
      attemptComplete();
    }
    
  }

  /**
   * Contains all the necessary information after a model builder has failed to build the model
   */
  public static class ModelBuildFailure {
    private final Throwable _throwable;
    private final Model.Parameters _parameters;

    public ModelBuildFailure(Throwable throwable, Model.Parameters parameters) {
      this._throwable = throwable;
      this._parameters = parameters;
    }

    public Throwable getThrowable() {
      return _throwable;
    }

    public Model.Parameters getParameters() {
      return _parameters;
    }
  }

  /**
   * Indicate this builder there will be no more models.
   */
  public void noMoreModels() {
    _completed.set(true);
  }
  
  private void attemptComplete(){
    if(!_completed.get() || _modelInProgressCounter.get() != 0) return;
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
