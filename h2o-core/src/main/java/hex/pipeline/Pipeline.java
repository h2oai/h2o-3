package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelBuilderListener;
import hex.ModelCategory;
import hex.pipeline.DataTransformer.FrameType;
import hex.pipeline.PipelineModel.PipelineOutput;
import hex.pipeline.PipelineModel.PipelineParameters;
import water.Key;
import water.fvec.Frame;

import java.util.concurrent.atomic.AtomicReference;


public class Pipeline extends ModelBuilder<PipelineModel, PipelineParameters, PipelineOutput> {

  public Pipeline(PipelineParameters parms) {
    super(parms);
  }

  public Pipeline(PipelineParameters parms, Key<PipelineModel> key) {
    super(parms, key);
  }

  public Pipeline(PipelineParameters parms, boolean startup_once) {
    super(parms, startup_once);
  }

  @Override
  protected PipelineDriver trainModelImpl() {
    return new PipelineDriver();
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[0];  //depends on final estimator
  }

  @Override
  public boolean isSupervised() {
    return false; //depends on final estimator
  }
  
  public class PipelineDriver extends Driver {
    @Override
    public void computeImpl() {
//      init(true);
      PipelineOutput output = new PipelineOutput();
      PipelineModel model = new PipelineModel(dest(), _parms, output);
      output._transformers = _parms._transformers;
      model.delete_and_lock(_job);
      try {
        PipelineContext context = new PipelineContext(_parms);
        TransformerChain chain = new TransformerChain(_parms._transformers);
        chain.prepare(context);
        if (_parms._estimator == null) return;
        output._estimator = chain.transform(
                new Frame[]{_parms.train(), _parms.valid()},
                new FrameType[]{FrameType.Training, FrameType.Validation},
                context,
                (frames, ctxt) -> {
                  _parms._estimator._train = frames[0] == null ? null : frames[0].getKey();
                  _parms._estimator._valid = frames[1] == null ? null : frames[1].getKey();
                  AtomicReference<Key<Model>> result = new AtomicReference<>();
                  AtomicReference<Throwable> failure = new AtomicReference<>();
                  ModelBuilder.make(_parms._estimator).trainModel(new ModelBuilderListener() {
                    @Override
                    public void onModelSuccess(Model model) {
                      result.set(model.getKey());
                    }

                    @Override
                    public void onModelFailure(Throwable cause, Model.Parameters parameters) {
                      failure.set(cause);
                    }
                  });
                  if (failure.get() != null) {
                    throw new RuntimeException(failure.get());
                  }
                  return result.get();
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
    new TransformerChain(_parms._transformers).prepare(new PipelineContext(_parms));
    super.computeCrossValidation();
  }

}
