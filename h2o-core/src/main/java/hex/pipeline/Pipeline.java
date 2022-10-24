package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelBuilderCallbacks;
import hex.ModelCategory;
import hex.pipeline.DataTransformer.FrameType;
import hex.pipeline.PipelineContext.CompositeFrameTracker;
import hex.pipeline.PipelineContext.ConsistentKeyTracker;
import hex.pipeline.PipelineContext.ScopeTracker;
import hex.pipeline.PipelineModel.PipelineOutput;
import hex.pipeline.PipelineModel.PipelineParameters;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import static hex.pipeline.PipelineHelper.reassign;


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
    if (_parms._transformers == null) _parms._transformers = new DataTransformer[0];
    if (_parms._estimatorResult == null) _parms._estimatorResult = Key.make(_result+"_estimator");
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
      init(true);
      PipelineOutput output = new PipelineOutput(Pipeline.this);
      output._transformers = _parms._transformers.clone();
      PipelineModel model = new PipelineModel(dest(), _parms, output);
      model.delete_and_lock(_job);
      
      try {
        PipelineContext context = newContext();
        TransformerChain chain = newChain(context);
        setTrain(context.getTrain());
        setValid(context.getValid());
        Scope.track(train(), valid()); //chain preparation may have provided extended/modified train/valid frames.
        if (_parms._estimatorParams == null) return;
        try (Scope.Safe inner = Scope.safe(train(), valid())) {
          output._model = chain.transform(
                  new Frame[]{train(), valid()},
                  new FrameType[]{FrameType.Training, FrameType.Validation},
                  context,
                  (frames, ctxt) -> {
                    ModelBuilder mb = makeEstimatorBuilder(_parms._estimatorResult, _parms._estimatorParams, frames[0], frames[1]);
                    Keyed res = mb.trainModelNested(null);
                    return res == null ? null : res.getKey();
                  }
          );
        }
      } finally {
        model._output.sync();
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
      init(true); //also protects train+valid frames
      PipelineOutput output = new PipelineOutput(Pipeline.this);
      output._transformers = _parms._transformers.clone();
      model = new PipelineModel(dest(), _parms, output);
      model.delete_and_lock(_job);
      
      PipelineContext context = newContext();
      TransformerChain chain = newChain(context);
      setTrain(context.getTrain());
      setValid(context.getValid());
      Scope.track(train(), valid()); //chain preparation may have provided extended/modified train/valid frames.
//      initWorkspace(true);
      try (Scope.Safe mainModelScope = Scope.safe(train(), valid())) {
        output._model = chain.transform(
                new Frame[]{train(), valid()},
                new FrameType[]{FrameType.Training, FrameType.Validation},
                context,
                (frames, ctxt) -> {
                  ModelBuilder mb = makeEstimatorBuilder(_parms._estimatorResult, _parms._estimatorParams, frames[0], frames[1]);
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
//                                  System.out.println("before cv model:\n"+ScopeInspect.dataKeysToString());
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
//                        System.out.println("after cv compute:\n"+ScopeInspect.dataKeysToString());
                      }
                    }
                  });
                  mb.trainModelNested(null);
                  return mb.dest();
                }
        );
      }
    } finally {
      if (model != null) {
        model._output.sync();
        model.update(_job);
        model.unlock(_job);
      }
//      System.out.println("before cleanup:\n"+ScopeInspect.dataKeysToString());
      cleanUp();
//      System.out.println("before exit:\n"+ScopeInspect.dataKeysToString());
      Scope.exit();
//      System.out.println("after exit:\n"+ScopeInspect.dataKeysToString());
    }
  }
  
  private PipelineContext newCVContext(PipelineContext context, Model.Parameters cvParams) {
    PipelineParameters pparams = (PipelineParameters) context._params.clone();
    pparams._is_cv_model = cvParams._is_cv_model;
    pparams._cv_fold = cvParams._cv_fold;
    PipelineContext cvContext = new PipelineContext(pparams, context._tracker);
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
            new PipelineContext.FrameTracker() {
              @Override
              public void apply(Frame transformed, Frame frame, FrameType type, PipelineContext context, DataTransformer transformer) {
                if (stop_requested()) throw new Job.JobCancelledException(_job);
              }
            },
            new ConsistentKeyTracker(),
            new ScopeTracker()
//            new PipelineContext.FrameTracker() {
//              @Override
//              public void apply(Frame transformed, Frame frame, FrameType type, PipelineContext context, DataTransformer transformer) {
//                if (transformed == null || frame == transformed) return;
//                track(transformed, context._params._is_cv_model);
//              }
//            }
    ));
  }
  
  private TransformerChain newChain(PipelineContext context) {
    TransformerChain chain = new TransformerChain(_parms._transformers);
    chain.prepare(context);
    return chain;
  }
  
  private ModelBuilder makeEstimatorBuilder(Key<Model> eKey, Model.Parameters eParams, Frame train, Frame valid) {
    eParams._train = train == null ? null : train.getKey();
    eParams._valid = valid == null ? null : valid.getKey();
    eParams._response_column = _parms._response_column;
    eParams._weights_column = _parms._weights_column;
    eParams._offset_column = _parms._offset_column;
    eParams._ignored_columns = _parms._ignored_columns;
    eParams._fold_column = _parms._fold_column;
    eParams._fold_assignment = _parms._fold_assignment;
    eParams._nfolds= _parms._nfolds;
    eParams._max_runtime_secs = _parms._max_runtime_secs > 0 ? remainingTimeSecs() : _parms._max_runtime_secs;
    
    ModelBuilder mb = ModelBuilder.make(eParams, eKey);
    mb._job = _job;
    return mb;
  }
  
}
