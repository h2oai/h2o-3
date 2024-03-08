package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelBuilderCallbacks;
import hex.ModelCategory;
import hex.pipeline.DataTransformer.FrameType;
import hex.pipeline.trackers.CompositeFrameTracker;
import hex.pipeline.trackers.ConsistentKeyTracker;
import hex.pipeline.trackers.ScopeTracker;
import hex.pipeline.PipelineModel.PipelineOutput;
import hex.pipeline.PipelineModel.PipelineParameters;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;

import java.util.Arrays;

import static hex.pipeline.PipelineHelper.reassign;


/**
 * The {@link ModelBuilder} for {@link PipelineModel}s.
 */
public class Pipeline extends ModelBuilder<PipelineModel, PipelineParameters, PipelineOutput> {
  
  public Pipeline(PipelineParameters parms) {
    super(parms);
    init(false);
  }

  public Pipeline(PipelineParameters parms, Key<PipelineModel> key) {
    super(parms, key);
  }

  public Pipeline(boolean startup_once) {
//    super(new PipelineParameters(), startup_once, null);  // no schema directory to completely disable schema lookup for now.
    super(new PipelineParameters(), startup_once); 
  }

  @Override
  public void init(boolean expensive) {
    if (expensive) {
      earlyValidateParams();
      if (_parms._transformers == null) _parms._transformers = new DataTransformer[0];
      _parms._transformers = Arrays.stream(_parms._transformers).filter(DataTransformer::enabled).toArray(DataTransformer[]::new);
    }
    super.init(expensive);
  }

  protected void earlyValidateParams() {
    if (_parms._categorical_encoding != Model.Parameters.CategoricalEncodingScheme.AUTO) {
      // we need to ensure that no transformation occurs before the transformers in the pipeline
      hide("_categorical_encoding",
           "Pipeline supports only AUTO categorical encoding: custom categorical encoding should be applied either as a transformer or directly to the final estimator of the pipeline.");
      _parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.AUTO;
    }
    if (_parms._estimatorParams == null && nFoldCV()) {
      error("_estimator", "Pipeline can use cross validation only if provided with an estimator.");
    }
  }

  @Override
  protected PipelineDriver trainModelImpl() {
    return new PipelineDriver();
  }

  @Override
  public ModelCategory[] can_build() {
    ModelBuilder finalBuilder = getFinalBuilder();
//    return finalBuilder == null ? new ModelCategory[] {ModelCategory.Unknown} : finalBuilder.can_build();
    return finalBuilder == null ? ModelCategory.values() : finalBuilder.can_build();
  }

  @Override
  public boolean isSupervised() {
    ModelBuilder finalBuilder = getFinalBuilder();
    return finalBuilder != null && finalBuilder.isSupervised();
  }
  
  private ModelBuilder getFinalBuilder() {
    return _parms._estimatorParams == null ? null : ModelBuilder.make(_parms._estimatorParams.algoName(), null, null);
  }
  
  
  public class PipelineDriver extends Driver {
    @Override
    public void computeImpl() {
      init(true); //also protects original train+valid frames
      PipelineOutput output = new PipelineOutput(Pipeline.this);
      PipelineModel model = new PipelineModel(dest(), _parms, output);
      model.delete_and_lock(_job);
      
      try {
        PipelineContext context = newContext();
        TransformerChain chain = newChain(context);
        setTrain(context.getTrain());
        setValid(context.getValid());
        Scope.track(train(), valid()); //chain preparation may have provided extended/modified train/valid frames, so better track the current ones.
        output._transformers = _parms._transformers.clone();
        if (_parms._estimatorParams == null) return;
        try (Scope.Safe inner = Scope.safe(train(), valid())) {
          output._estimator = chain.transform(
                  new Frame[]{train(), valid()},
                  new FrameType[]{FrameType.Training, FrameType.Validation},
                  context,
                  (frames, ctxt) -> {
                    // use params from the context as they may have been modified during chain preparation
                    ModelBuilder mb = makeEstimatorBuilder(_parms._estimatorKeyGen.make(_result), ctxt._params._estimatorParams, ctxt._params, frames[0], frames[1]);
                    Keyed res = mb.trainModelNested(null);
                    return res == null ? null : res.getKey();
                  }
          );
        }
      } finally {
        model.syncOutput();
        model.update(_job);
        model.unlock(_job);
      }
    }
  }

  @Override
  public void computeCrossValidation() {
    assert _parms._estimatorParams != null; // no CV if pipeline used as a pure transformer (see params validation)
    PipelineModel model = null;
    try {
      Scope.enter();
      init(true); //also protects original train+valid frames
      PipelineOutput output = new PipelineOutput(Pipeline.this);
      model = new PipelineModel(dest(), _parms, output);
      model.delete_and_lock(_job);
      
      PipelineContext context = newContext();
      TransformerChain chain = newChain(context);
      setTrain(context.getTrain());
      setValid(context.getValid());
      Scope.track(train(), valid()); //chain preparation may have provided extended/modified train/valid frames.
      try (Scope.Safe mainModelScope = Scope.safe(train(), valid())) {
        output._transformers = _parms._transformers.clone();
        output._estimator = chain.transform(
                new Frame[]{train(), valid()},
                new FrameType[]{FrameType.Training, FrameType.Validation},
                context,
                (frames, ctxt) -> {
                  ModelBuilder mb = makeEstimatorBuilder(_parms._estimatorKeyGen.make(_result), ctxt._params._estimatorParams, ctxt._params, frames[0], frames[1]);
                  mb.setCallbacks(new ModelBuilderCallbacks() {
                    /**
                     * Using this callback, the transformations are applied at the time the CV model training is triggered,
                     * we don't have to stack up all transformed frames in memory BEFORE starting the CV-training.
                     */
                    @Override
                    public void wrapCompute(ModelBuilder builder, Runnable compute) {
                      Model.Parameters params = builder._parms;
                      if (!params._is_cv_model || !chain.isCVSensitive()) {
                        compute.run();
                        return;
                      }
                      
                      try (Scope.Safe cvModelComputeScope = Scope.safe(train(), params.train(), params.valid())) {
                        PipelineContext cvContext = newCVContext(context, params);
                        Scope.track(cvContext.getTrain(), cvContext.getValid());
                        TransformerChain cvChain = chain.clone(); // as cv models can be trained in parallel
                        cvChain.transform(
                                new Frame[]{cvContext.getTrain(), cvContext.getValid()},
                                new FrameType[]{FrameType.Training, FrameType.Validation},
                                cvContext,
                                (cvFrames, ctxt) -> {
                                  // ensure that generated vecs, that will be used to train+score this CV model, get deleted at the end of the pipeline training
                                  track(cvFrames[0], true);
                                  track(cvFrames[1], true);
                                  reassign(cvFrames[0], params._train, _job.getKey());
                                  reassign(cvFrames[1], params._valid, _job.getKey());
                                  // re-init & re-validate the builder in case we produced a bad frame 
                                  // (although this should have been detected earlier as a similar transformation was already applied to main training frame)
                                  builder._input_parms = params.clone();
                                  builder.setTrain(null);
                                  builder.setValid(null);
                                  builder.init(false);
                                  if (builder.error_count() > 0)
                                    throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(builder);
                                  return null;
                                }
                        );
                        compute.run();
                      }
                    }
                  });
                  mb.trainModelNested(null);
                  return mb.dest();
                }
        );
      }
      model.setInputParms(_input_parms);
    } finally {
      if (model != null) {
        model.syncOutput();
        model.update(_job);
        model.unlock(_job);
      }
      cleanUp();
      Scope.exit();
    }
  }
  
  private PipelineContext newCVContext(PipelineContext context, Model.Parameters cvParams) {
    PipelineContext cvContext = new PipelineContext(context._params, context._tracker);
    PipelineParameters pparams = cvContext._params;
    pparams._is_cv_model = cvParams._is_cv_model;
    pparams._cv_fold = cvParams._cv_fold;
    Frame baseFrame = new Frame(Key.make(_result.toString()+"_cv_"+(pparams._cv_fold+1)), train().names(), train().vecs());
    if ( pparams._weights_column != null ) baseFrame.remove( pparams._weights_column );
    Frame cvTrainOld = cvParams.train();
    Frame cvValidOld = cvParams.valid();
    String cvWeights = cvParams._weights_column;
    Frame cvTrain = new Frame(baseFrame);
    cvTrain.add(cvWeights, cvTrainOld.vec(cvWeights));
    DKV.put(cvTrain);
    Frame cvValid = new Frame(baseFrame);
    cvValid.add(cvWeights, cvValidOld.vec(cvWeights));
    DKV.put(cvValid);
    cvContext.setTrain(cvTrain);
    cvContext.setValid(cvValid);
    return cvContext;
  }
  
  private PipelineContext newContext() {
    return new PipelineContext(_parms, new CompositeFrameTracker(
            new FrameTracker() { // propagates training cancellation requests as early as possible
              @Override
              public void apply(Frame transformed, Frame original, FrameType type, PipelineContext context, DataTransformer transformer) {
                if (stop_requested()) throw new Job.JobCancelledException(_job);
              }
            },
            new ConsistentKeyTracker(),
            new ScopeTracker()
    ));
  }
  
  private TransformerChain newChain(PipelineContext context) {
    TransformerChain chain = new TransformerChain(_parms._transformers);
    chain.prepare(context);
    return chain;
  }
  
  private ModelBuilder makeEstimatorBuilder(Key<Model> eKey, Model.Parameters eParams, PipelineParameters pParams, Frame train, Frame valid) {
    eParams._train = train == null ? null : train.getKey();
    eParams._valid = valid == null ? null : valid.getKey();
    eParams._response_column = pParams._response_column;
    eParams._weights_column = pParams._weights_column;
    eParams._offset_column = pParams._offset_column;
    eParams._ignored_columns = pParams._ignored_columns;
    eParams._fold_column = pParams._fold_column;
    eParams._fold_assignment = pParams._fold_assignment;
    eParams._nfolds= pParams._nfolds;
    eParams._max_runtime_secs = pParams._max_runtime_secs > 0 ? remainingTimeSecs() : pParams._max_runtime_secs;
    
    ModelBuilder mb = ModelBuilder.make(eParams, eKey);
    mb._job = _job;
    return mb;
  }
  
}
