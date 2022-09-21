package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.pipeline.DataTransformer.FrameType;
import hex.pipeline.PipelineContext.CompositeFrameTracker;
import hex.pipeline.PipelineContext.ConsistentKeyTracker;
import hex.pipeline.PipelineModel.PipelineOutput;
import hex.pipeline.PipelineModel.PipelineParameters;
import water.Key;
import water.Keyed;
import water.Scope;
import water.fvec.Frame;


public class Pipeline extends ModelBuilder<PipelineModel, PipelineParameters, PipelineOutput> {
  
  public Pipeline(PipelineParameters parms) {
    super(parms);
    init(false);
  }

  public Pipeline(PipelineParameters parms, Key<PipelineModel> key) {
    super(parms, key);
  }

  public Pipeline(boolean startup_once) {
    super(new PipelineParameters(), startup_once, null);  // no schema directory to completely disable schema lookup for now.
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
    if (_parms._estimator == null && nFoldCV()) {
      error("_estimator", "Pipeline can use cross validation only if provided with an estimator.");
    }
    if (_parms._transformers == null) _parms._transformers = new DataTransformer[0];
  }

  @Override
  protected PipelineDriver trainModelImpl() {
    return new PipelineDriver();
  }

  @Override
  public ModelCategory[] can_build() {
    ModelBuilder finalBuilder = getFinalBuilder();
    return finalBuilder == null ? new ModelCategory[] {ModelCategory.Unknown} : finalBuilder.can_build();
//    return finalBuilder == null ? ModelCategory.values() : finalBuilder.can_build();
  }

  @Override
  public boolean isSupervised() {
    ModelBuilder finalBuilder = getFinalBuilder();
    return finalBuilder != null && finalBuilder.isSupervised();
  }
  
  private ModelBuilder getFinalBuilder() {
    return _parms._estimator == null ? null : ModelBuilder.make(_parms._estimator.algoName(), null, null);
  }
  
  
  public class PipelineDriver extends Driver {
    @Override
    public void computeImpl() {
      init(true);
      PipelineOutput output = new PipelineOutput(Pipeline.this);
      PipelineModel model = new PipelineModel(dest(), _parms, output);
      output._transformers = _parms._transformers.clone();
      model.delete_and_lock(_job);
      try {
        PipelineContext context = newContext();
        TransformerChain chain = newChain(context);
        setTrain(context.getTrain());
        setValid(context.getValid());
        if (_parms._estimator == null) return;
        output._estimator = chain.transform(
                new Frame[]{ train(), valid() },
                new FrameType[]{FrameType.Training, FrameType.Validation},
                context,
                (frames, ctxt) -> {
                  // propagate data params only
                  _parms._estimator._train = frames[0] == null ? null : frames[0].getKey();
                  _parms._estimator._valid = frames[1] == null ? null : frames[1].getKey();
                  _parms._estimator._response_column = _parms._response_column;
//                  _parms._estimator._fold_column = _parms._fold_column;
                  _parms._estimator._weights_column = _parms._weights_column;
                  _parms._estimator._offset_column = _parms._offset_column;
                  Keyed res = ModelBuilder.make(_parms._estimator).trainModel().get();
                  return res == null ? null : res.getKey();
                }
        );
      } finally {
        model.update(_job);
        model.unlock(_job);
      }
    }
    
  }

  @Override
  public void computeCrossValidation() {
    assert _parms._estimator != null; // no CV if pipeline used as a pure transformer (see params validation)
    PipelineModel model = null;
    try {
      Scope.enter();
      init(false);
      PipelineContext context = newContext();
      TransformerChain chain = newChain(context);
  //    super.computeCrossValidation();
      setTrain(context.getTrain());
      setValid(context.getValid());
      PipelineOutput output = new PipelineOutput(Pipeline.this);
      model = new PipelineModel(dest(), _parms, output);
      output._transformers = _parms._transformers.clone();
      model.delete_and_lock(_job);
      initWorkspace(true);
      output._estimator = chain.transform(
              new Frame[] { train(), valid() }, 
              new FrameType[]{FrameType.Training, FrameType.Validation}, 
              context,
              (frames, ctxt) -> {
                _parms._estimator._train = frames[0] == null ? null : frames[0].getKey();
                _parms._estimator._valid = frames[1] == null ? null : frames[1].getKey();
                _parms._estimator._response_column = _parms._response_column;
                _parms._estimator._nfolds= _parms._nfolds;
                _parms._estimator._fold_column = _parms._fold_column;
                _parms._estimator._weights_column = _parms._weights_column;
                _parms._estimator._offset_column = _parms._offset_column;
                ModelBuilder mb = ModelBuilder.make(_parms._estimator);
                mb._job = _job;
//                mb.init(true);
                mb.trainModelNested(new DefaultTrainingFramesProvider() {
                  @Override
                  public Frame getTrain() {
                    return frames[0];
                  }

                  @Override
                  public Frame getValid() {
                    return frames[1];
                  }

                  @Override
                  public Frame[] getCVFrames(int cvIdx) {
                    PipelineContext cvContext = newCVContext(context, cvIdx, _cvBase);
                    //TODO: need to disable parallelization for CV: chain.transform is not thread safe (index incr)
                    return chain.doTransform(
                            new Frame[] {cvContext.getTrain(), cvContext.getValid()},
                            new FrameType[] { FrameType.Training, FrameType.Validation },
                            cvContext
                    );
                  }
                });
                return mb.dest();
              }
      );
    } finally {
      if (model != null) {
        model.update(_job);
        model.unlock(_job);
      }
      cleanUp();
      Scope.exit();
    }
  }
  
  private PipelineContext newCVContext(PipelineContext context, int cvIdx, Frame cvBase) {
      PipelineParameters params = (PipelineParameters) context._params.clone();
      params._is_cv_model = true;
      params._cv_fold = cvIdx;
      PipelineContext cvContext = new PipelineContext(params, context._tracker);
      Frame baseFrame = new Frame(Key.make(cvBase.getKey()+"_"+(cvIdx+1)), cvBase.names(), cvBase.vecs());
      cvContext.setTrain(baseFrame);
      cvContext.setValid(baseFrame);
      return cvContext;
  }
  
  private PipelineContext newContext() {
    return new PipelineContext(_parms, new CompositeFrameTracker(
            new ConsistentKeyTracker(),
            new PipelineContext.FrameTracker() {
              @Override
              public void apply(Frame transformed, Frame frame, FrameType type, PipelineContext context, DataTransformer transformer) {
                if (transformed == null) return;
                boolean useScope = !context._params._is_cv_model;
                trackFrame(transformed, useScope);
              }
            }
    ));
  }
  
  private TransformerChain newChain(PipelineContext context) {
    TransformerChain chain = new TransformerChain(_parms._transformers);
    chain.prepare(context);
    return chain;
  }

}
