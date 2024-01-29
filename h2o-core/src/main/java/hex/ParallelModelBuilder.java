package hex;

import jsr166y.ForkJoinTask;
import org.apache.log4j.Logger;
import water.Iced;
import water.Key;
import water.util.IcedAtomicInt;

import java.util.*;

/**
 * Dispatcher for parallel model building. Starts building models every time the run method is invoked.
 * After each model is finished building, the `modelFinished` method is invoked, which in turn invokes modelFeeder callback.
 * ModelFeeder receives the model built and can deal with it in any way - e.g. put it into a Grid, or discard it if the resulting model
 * is a failure. It also has the power to invoke the training of any number of new models. Or stop the parallel model builder,
 * released the barrier inside.
 */
public class ParallelModelBuilder extends ForkJoinTask<ParallelModelBuilder> {
  
  private static final Logger LOG = Logger.getLogger(ParallelModelBuilder.class);

  public static abstract class ParallelModelBuilderCallback<D extends ParallelModelBuilderCallback> extends Iced<D> {

    public abstract void onBuildSuccess(final Model model, final ParallelModelBuilder parallelModelBuilder);

    public abstract void onBuildFailure(final ModelBuildFailure modelBuildFailure, final ParallelModelBuilder parallelModelBuilder);

  }

  private final transient ParallelModelBuilderCallback _callback;
  private final transient IcedAtomicInt _modelInProgressCounter = new IcedAtomicInt();
  private final transient EachBuilderCallbacks _modelBuildersCallbacks;

  public ParallelModelBuilder(final ParallelModelBuilderCallback callback) {
    Objects.requireNonNull(callback);
    _callback = callback;
    _modelBuildersCallbacks = new EachBuilderCallbacks();
  }

  /**
   * Runs given collection of {@link ModelBuilder} in parallel. After each model is finished building,
   * one of the callbacks (on model failure / on model completion) is called.
   *
   * @param modelBuilders An {@link Collection} of {@link ModelBuilder} to execute in parallel.
   */
  public void run(final Collection<ModelBuilder> modelBuilders) {
    if (LOG.isTraceEnabled()) LOG.trace("run with " + modelBuilders.size() + " models");
    for (final ModelBuilder modelBuilder : modelBuilders) {
      _modelInProgressCounter.incrementAndGet();
      modelBuilder.setCallbacks(_modelBuildersCallbacks);
      modelBuilder.trainModel();
    }
  }


  private class EachBuilderCallbacks extends ModelBuilderCallbacks<EachBuilderCallbacks> {

    @Override
    public void onModelSuccess(Key<Model> modelKey) {
      Model model = modelKey.get();
      if (model._parms._is_cv_model) return; // not interested in CV models here
      try {
        _callback.onBuildSuccess(model, ParallelModelBuilder.this);
      } finally {
        attemptComplete();
      }
    }

    @Override
    public void onModelFailure(Key<Model> modelKey, Throwable cause, Model.Parameters parameters) {
      if (checkExceptionHandled(cause)) return;
      if (parameters._is_cv_model) return; // not interested in CV models here
      try {
        final ModelBuildFailure modelBuildFailure = new ModelBuildFailure(cause, parameters);
        _callback.onBuildFailure(modelBuildFailure, ParallelModelBuilder.this);
      } finally {
        attemptComplete();
      }
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
  
  private void attemptComplete() {
    int modelsInProgress = _modelInProgressCounter.decrementAndGet();
    if (LOG.isTraceEnabled()) LOG.trace("Completed a model, left in progress: " + modelsInProgress);
    if (modelsInProgress == 0) {
      complete(this);
    }
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
