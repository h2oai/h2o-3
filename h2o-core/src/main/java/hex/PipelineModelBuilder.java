package hex;

import water.*;
import water.fvec.Frame;
import water.util.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *  tbd
 */
public class PipelineModelBuilder extends ModelBuilder<PipelineModel, PipelineModel.PipelineModelParameters, PipelineModel.PipelineModelOutput>{

  private ModelBuilder _scoringModelBuilder;
  private final List<Key<Model>> _preprocessingModels = new ArrayList<>();

  public PipelineModelBuilder(PipelineModel.PipelineModelParameters parameters, ModelBuilder scoringModelBuilder) {
    super(parameters, scoringModelBuilder.dest());
    _scoringModelBuilder = scoringModelBuilder;
    super.init(false);
  }

  private class ModelPipelineDriver extends Driver {
    @Override
    public void computeImpl() {
      PipelineModel pipelineModel = null;
      Model retrievedScoringModel = null;
      try {
        // Making PipelineModel to be a proxy for the underlying scoringModel.
        // Training frame and response column are needed to define distribution family and corresponding model metrics.
        _parms.setTrain(_scoringModelBuilder._parms.train()._key);

        _parms._response_column = _scoringModelBuilder._parms._response_column;
        init(true);

        Model.Parameters scoringModelParams = _scoringModelBuilder._parms;

        // Applying preprocessing to train and valid frames
        ArrayList<String> featuresToIgnore = new ArrayList<>();
        Frame preprocessedTrainFrame = scoringModelParams.train();
        Frame preprocessedValidFrame = scoringModelParams.valid();
        for(Key<Model> preprocessingModelKey :_preprocessingModels) {
          Model preprocessingModel = DKV.getGet(preprocessingModelKey);
          String[] featuresBefore = preprocessedTrainFrame._names.clone();
          preprocessedTrainFrame = preprocessingModel.score(preprocessedTrainFrame);
          if(preprocessedValidFrame != null) preprocessedValidFrame = preprocessingModel.score(preprocessedValidFrame);
          String[] featuresAfter = preprocessedTrainFrame._names;

          String[] diff = ArrayUtils.difference(featuresAfter, featuresBefore);
          List<String> substitutedFeatures =
                  Arrays.stream(diff)
                  .map(featureName -> featureName.substring(0, featureName.indexOf("_"))) // TODO there should be a convention or a mechanism how to findout which colums were added and which to ignore
                  .collect(Collectors.toList());

          Log.info("[PipelineModel] Preprocessing model " + preprocessingModel._parms.algoName()
                  + " produced new features: " + StringUtils.join(",", diff) );
          featuresToIgnore.addAll(substitutedFeatures); // TODO we better introduce outputcolumn for all models. jira
        };
        scoringModelParams.setTrain(preprocessedTrainFrame._key);
        if(preprocessedValidFrame != null) scoringModelParams.setValid(preprocessedValidFrame._key);
        scoringModelParams._ignored_columns = featuresToIgnore.toArray(new String[0]);

        _scoringModelBuilder.init(false);
        Log.debug("Training scoring model for PipelineModel: " );
        Job scoringModelJob = _scoringModelBuilder.trainModelOnH2ONode();
        scoringModelJob.get();
        retrievedScoringModel = DKV.getGet(_scoringModelBuilder.dest());

        // Preparing PipelineModel
        PipelineModel.PipelineModelOutput emptyOutput = new PipelineModel.PipelineModelOutput(PipelineModelBuilder.this, null);
        pipelineModel = new PipelineModel(_result, _parms, emptyOutput, getPreprocessingModels()); // TODO output should have also preprocessing models
        pipelineModel.write_lock(_job._key); // we don't want to use `delete_lock` here as `pipelineModel` uses dest() and `_scoringModelBuilder` was already trained with same destination key

        pipelineModel._output = new PipelineModel.PipelineModelOutput(PipelineModelBuilder.this, retrievedScoringModel);

        _job.update(1);
        pipelineModel.update(_job);
      } finally {
        if (pipelineModel != null) {
          pipelineModel.unlock(_job);
        }
      }
    }
  }

  public void addPreprocessorModel(Key<Model> modelKey) {
    _preprocessingModels.add(modelKey);
  }

  public List<Key<Model>> getPreprocessingModels() {
    return _preprocessingModels;
  }

  @Override
  protected Driver trainModelImpl() {
    return new ModelPipelineDriver();
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ // TODO check which categories we support
            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial,
    };
  }

  @Override
  public boolean isSupervised() {
    return true;
  }
}
