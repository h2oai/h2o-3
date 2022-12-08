package hex.ensemble;

import hex.Distribution;
import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel;
import hex.grid.Grid;
import hex.tree.drf.DRFModel;
import jsr166y.CountedCompleter;
import water.DKV;
import water.Job;
import water.Key;
import water.Scope;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.ReflectionUtils;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

import static hex.Model.Parameters.FoldAssignmentScheme.AUTO;
import static hex.Model.Parameters.FoldAssignmentScheme.Random;
import static hex.genmodel.utils.DistributionFamily.*;
import static hex.util.DistributionUtils.familyToDistribution;


/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsemble extends ModelBuilder<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> {
  StackedEnsembleDriver _driver;
  // The in-progress model being built
  protected StackedEnsembleModel _model;

  public StackedEnsemble(StackedEnsembleModel.StackedEnsembleParameters parms) {
    super(parms);
    init(false);
  }

  public StackedEnsemble(boolean startup_once) {
    super(new StackedEnsembleModel.StackedEnsembleParameters(), startup_once);
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial
    };
  }

  @Override
  public BuilderVisibility builderVisibility() {
    return BuilderVisibility.Stable;
  }

  @Override
  public boolean isSupervised() {
    return true;
  }

  @Override
  protected void ignoreBadColumns(int npredictors, boolean expensive){
    HashSet usedColumns = new HashSet();

    for(Key k: _parms._base_models) {
      Model model = (Model) DKV.getGet(k);
      usedColumns.add(model._parms._response_column);
      usedColumns.addAll(Arrays.asList(model._parms.getNonPredictors()));
      if (model._output._origNames != null)
        usedColumns.addAll(Arrays.asList(model._output._origNames));
      else
        usedColumns.addAll(Arrays.asList(model._output._names));
    }

    usedColumns.addAll(Arrays.asList(_parms.getNonPredictors()));

    // FilterCols(n=0) because there is no guarantee that non-predictors are
    // at the end of the frame, e.g., `metalearner_fold` column can be anywhere,
    // and `usedColumns` contain all used columns even the non-predictor ones
    new FilterCols(0) {
      @Override protected boolean filter(Vec v, String name) {
        return !usedColumns.contains(name);
      }
    }.doIt(_train,"Dropping unused columns: ",expensive);
  }

  @Override
  protected StackedEnsembleDriver trainModelImpl() {
    return _driver = _parms._blending == null ? new StackedEnsembleCVStackingDriver() : new StackedEnsembleBlendingDriver();
  }

  @Override
  public boolean haveMojo() {
    return true;
  }

  @Override
  public int nclasses() {
    if (_parms._metalearner_parameters != null) {
      DistributionFamily distribution = _parms._metalearner_parameters.getDistributionFamily();
      if (Arrays.asList(multinomial, ordinal, AUTO).contains(distribution))
        return _nclass;
      if (Arrays.asList(bernoulli, quasibinomial, fractionalbinomial).contains(distribution))
        return 2;
      return 1;
    }
    return super.nclasses();
  }

  @Override
  public void init(boolean expensive) {
    expandBaseModels();
    super.init(expensive);

    if (_parms._distribution != DistributionFamily.AUTO) {
      throw new H2OIllegalArgumentException("Setting \"distribution\" to StackedEnsemble is unsupported. Please set it in \"metalearner_parameters\".");
    }

    checkColumnPresent("fold", _parms._metalearner_fold_column, train(), valid(), _parms.blending());
    checkColumnPresent("weights", _parms._weights_column, train(), valid(), _parms.blending());
    checkColumnPresent("offset", _parms._offset_column, train(), valid(), _parms.blending());
    validateBaseModels();
  }

  /**
   * Expand base models - if a grid is provided instead of a model it gets expanded in to individual models.
   */
  private void expandBaseModels() {
    // H2O Flow initializes SE with no base_models
    if (_parms._base_models == null) return;

    List<Key> baseModels = new ArrayList<Key>();
    for (Key baseModelKey : _parms._base_models) {
      Object retrievedObject = DKV.getGet(baseModelKey);
      if (retrievedObject instanceof Model) {
        baseModels.add(baseModelKey);
      } else if (retrievedObject instanceof Grid) {
        Grid grid = (Grid) retrievedObject;
        Collections.addAll(baseModels, grid.getModelKeys());
      } else if (retrievedObject == null) {
        throw new IllegalArgumentException(String.format("Specified id \"%s\" does not exist.", baseModelKey));
      } else {
        throw new IllegalArgumentException(String.format("Unsupported type \"%s\" as a base model.", retrievedObject.getClass().toString()));
      }
    }
    _parms._base_models = baseModels.toArray(new Key[0]);
  }

  /**
   * Validates base models.
   */
  private void validateBaseModels() {
    // H2O Flow initializes SE with no base_models
    if (_parms._base_models == null) return;

    boolean warnSameWeightsColumns = true;
    String referenceWeightsColumn = null;
    for (int i = 0; i < _parms._base_models.length; i++) {
      Model baseModel = DKV.getGet(_parms._base_models[i]);

      if (i == 0) {
        if ((_parms._offset_column == null))
          _parms._offset_column = baseModel._parms._offset_column;
        referenceWeightsColumn = baseModel._parms._weights_column;
        warnSameWeightsColumns = referenceWeightsColumn != null; // We don't want to warn if no weights are set
      }

      if (!Objects.equals(referenceWeightsColumn, baseModel._parms._weights_column)) {
        warnSameWeightsColumns = false;
      }

      if (!Objects.equals(_parms._offset_column, baseModel._parms._offset_column))
        throw new IllegalArgumentException("All base models must have the same offset_column!");
    }

    if (_parms._weights_column == null && warnSameWeightsColumns && _parms._base_models.length > 0) {
      warn("_weights_column", "All base models use weights_column=\"" + referenceWeightsColumn +
              "\" but Stacked Ensemble does not. If you want to use the same " +
              "weights_column for the meta learner, please specify it as an argument " +
              "in the h2o.stackedEnsemble call.");
    }
  }


  /**
   * Checks for presence of a column in given {@link Frame}s. Null column means no checks are done.
   *
   * @param columnName     Name of the column, such as fold, weight, etc.
   * @param columnId       Actual column name in the frame. Null means no column has been specified.
   * @param frames         A list of frames to check the presence of fold column in
   */
  private static void checkColumnPresent(final String columnName, final String columnId, final Frame... frames) {
    if (columnId == null) return; // Unspecified column implies no checks are needs on provided frames

    for (Frame frame : frames) {
      if (frame == null) continue; // No frame provided, no checks required
      if (frame.vec(columnId) == null) {
        throw new IllegalArgumentException(String.format("Specified %s column '%s' not found in one of the supplied data frames. Available column names are: %s",
                columnName, columnId, Arrays.toString(frame.names())));
      }
    }
  }

  static void addModelPredictionsToLevelOneFrame(Model aModel, Frame aModelsPredictions, Frame levelOneFrame) {
    if (aModel._output.isBinomialClassifier()) {
      // GLM uses a different column name than the other algos
      Vec preds = aModelsPredictions.vec(2); // Predictions column names have been changed...
      levelOneFrame.add(aModel._key.toString(), preds);
    } else if (aModel._output.isMultinomialClassifier()) { //Multinomial
      //Need to remove 'predict' column from multinomial since it contains outcome
      Frame probabilities = aModelsPredictions.subframe(ArrayUtils.remove(aModelsPredictions.names(), "predict"));
      probabilities.setNames(
              Stream.of(probabilities.names())
                      .map((name) -> aModel._key.toString().concat("/").concat(name))
                      .toArray(String[]::new)
      );
      levelOneFrame.add(probabilities);
    } else if (aModel._output.isAutoencoder()) {
      throw new H2OIllegalArgumentException("Don't yet know how to stack autoencoders: " + aModel._key);
    } else if (!aModel._output.isSupervised()) {
      throw new H2OIllegalArgumentException("Don't yet know how to stack unsupervised models: " + aModel._key);
    } else {
      Vec preds = aModelsPredictions.vec("predict");
      levelOneFrame.add(aModel._key.toString(), preds);
    }
  }

  /**
   * Add non predictor columns to levelOneFrame, i.e., all but those generated by base models. For example:
   * response_column, metalearner_fold_column, weights_column
   *
   * @param parms           StackedEnsembleParameters
   * @param fr
   * @param levelOneFrame
   * @param training        Used to determine which columns are necessary to add
   */
  static void addNonPredictorsToLevelOneFrame(final StackedEnsembleModel.StackedEnsembleParameters parms, Frame fr, Frame levelOneFrame, boolean training) {
    if (training) {
      if (parms._metalearner_fold_column != null)
        levelOneFrame.add(parms._metalearner_fold_column, fr.vec(parms._metalearner_fold_column));
    }

    if (parms._weights_column != null)
      levelOneFrame.add(parms._weights_column, fr.vec(parms._weights_column));

    if (parms._offset_column != null)
      levelOneFrame.add(parms._offset_column, fr.vec(parms._offset_column));

    levelOneFrame.add(parms._response_column, fr.vec(parms._response_column));
  }


  /**
   * Inherit distribution and its parameters
   * @param baseModelParms
   */
  private void inheritDistributionAndParms(StackedEnsembleModel seModel, Model.Parameters baseModelParms) {
    if (baseModelParms instanceof GLMModel.GLMParameters) {
      try {
        _parms._metalearner_parameters.setDistributionFamily(familyToDistribution(((GLMModel.GLMParameters) baseModelParms)._family));
      } catch (IllegalArgumentException e) {
        warn("distribution", "Stacked Ensemble is not able to inherit distribution from GLM's family " + ((GLMModel.GLMParameters) baseModelParms)._family + ".");
      }
    } else if (baseModelParms instanceof DRFModel.DRFParameters) {
      inferBasicDistribution(seModel);
    } else {
      _parms._metalearner_parameters.setDistributionFamily(baseModelParms._distribution);
    }
    // deal with parameterized distributions
    switch (baseModelParms._distribution) {
      case custom:
        _parms._metalearner_parameters._custom_distribution_func = baseModelParms._custom_distribution_func;
        break;
      case huber:
        _parms._metalearner_parameters._huber_alpha = baseModelParms._huber_alpha;
        break;
      case tweedie:
        _parms._metalearner_parameters._tweedie_power = baseModelParms._tweedie_power;
        break;
      case quantile:
        _parms._metalearner_parameters._quantile_alpha = baseModelParms._quantile_alpha;
        break;
    }
  }


  void inferBasicDistribution(StackedEnsembleModel seModel) {
    if (seModel._output.isBinomialClassifier()) {
      _parms._metalearner_parameters.setDistributionFamily(DistributionFamily.bernoulli);
    } else if (seModel._output.isClassifier()) {
      _parms._metalearner_parameters.setDistributionFamily(DistributionFamily.multinomial);
    } else {
      _parms._metalearner_parameters.setDistributionFamily(DistributionFamily.gaussian);
    }
  }


  /**
   * Inherit family and its parameters
   * @param baseModelParms
   */
  private void inheritFamilyAndParms(StackedEnsembleModel seModel, Model.Parameters baseModelParms) {
    GLMModel.GLMParameters metaParams = (GLMModel.GLMParameters) _parms._metalearner_parameters;
    if (baseModelParms instanceof GLMModel.GLMParameters) {
      GLMModel.GLMParameters glmParams = (GLMModel.GLMParameters) baseModelParms;
      metaParams._family = glmParams._family;
      metaParams._link = glmParams._link;
    } else if (baseModelParms instanceof DRFModel.DRFParameters) {
      inferBasicDistribution(seModel);
    } else {
      try {
        metaParams.setDistributionFamily(baseModelParms._distribution);
      } catch (H2OIllegalArgumentException e) {
        warn("distribution", "Stacked Ensemble is not able to inherit family from a distribution " + baseModelParms._distribution + ".");
        inferBasicDistribution(seModel);
      }
    }
    // deal with parameterized distributions
    if (metaParams._family == GLMModel.GLMParameters.Family.tweedie) {
      _parms._metalearner_parameters._tweedie_power = baseModelParms._tweedie_power;
    }
  }

  /**
   * Infers distribution/family from a model
   * @param aModel
   * @return True if the distribution or family was inferred from a model
   */
  boolean inferDistributionOrFamily(StackedEnsembleModel seModel, Model aModel) {
    if (Metalearners.getActualMetalearnerAlgo(_parms._metalearner_algorithm) == Metalearner.Algorithm.glm) { //use family
      if (((GLMModel.GLMParameters)_parms._metalearner_parameters)._family != GLMModel.GLMParameters.Family.AUTO) {
        return false; // User specified family - no need to infer one; Link will be also used properly if it is specified
      }
      inheritFamilyAndParms(seModel,aModel._parms);
    } else { // use distribution
      if (_parms._metalearner_parameters._distribution != DistributionFamily.AUTO) {
        return false; // User specified distribution; no need to infer one
      }
      inheritDistributionAndParms(seModel, aModel._parms);
    }
    return true;
  }

  private DistributionFamily distributionFamily(Model aModel) {
    // TODO: hack alert: In DRF, _parms._distribution is always set to multinomial.  Yay.
    if (aModel instanceof DRFModel)
      if (aModel._output.isBinomialClassifier())
        return DistributionFamily.bernoulli;
      else if (aModel._output.isClassifier())
        return DistributionFamily.multinomial;
      else
        return DistributionFamily.gaussian;

    if (aModel instanceof StackedEnsembleModel) {
      StackedEnsembleModel seModel = (StackedEnsembleModel) aModel;
      if (Metalearners.getActualMetalearnerAlgo(seModel._parms._metalearner_algorithm) == Metalearner.Algorithm.glm) {
        return familyToDistribution(((GLMModel.GLMParameters) seModel._parms._metalearner_parameters)._family);
      }
      if (seModel._parms._metalearner_parameters._distribution != DistributionFamily.AUTO) {
        return seModel._parms._metalearner_parameters._distribution;
      }
    }

    try {
      Field familyField = ReflectionUtils.findNamedField(aModel._parms, "_family");
      Field distributionField = (familyField != null ? null : ReflectionUtils.findNamedField(aModel, "_dist"));
      if (null != familyField) {
        // GLM only, for now
        GLMModel.GLMParameters.Family thisFamily = (GLMModel.GLMParameters.Family) familyField.get(aModel._parms);
        return familyToDistribution(thisFamily);
      }

      if (null != distributionField) {
        Distribution distribution = ((Distribution)distributionField.get(aModel));
        DistributionFamily distributionFamily;
        if (null != distribution)
          distributionFamily = distribution._family;
        else
          distributionFamily = aModel._parms._distribution;

        // NOTE: If the algo does smart guessing of the distribution family we need to duplicate the logic here.
        if (distributionFamily == DistributionFamily.AUTO) {
          if (aModel._output.isBinomialClassifier())
            distributionFamily = DistributionFamily.bernoulli;
          else if (aModel._output.isClassifier())
            distributionFamily = DistributionFamily.multinomial;
          else
            distributionFamily = DistributionFamily.gaussian;
        } // DistributionFamily.AUTO

        return distributionFamily;
      }

      throw new H2OIllegalArgumentException("Don't know how to stack models that have neither a distribution hyperparameter nor a family hyperparameter.");
    }
    catch (Exception e) {
      throw new H2OIllegalArgumentException(e.toString(), e.toString());
    }
  }

  void checkAndInheritModelProperties(StackedEnsembleModel seModel) {
    if (null == _parms._base_models || 0 == _parms._base_models.length)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; found 0.");

    if (null != _parms._metalearner_fold_column && 0 != _parms._metalearner_nfolds)
      throw new H2OIllegalArgumentException("Cannot specify fold_column and nfolds at the same time.");

    Model aModel = null;
    boolean retrievedFirstModelParams = false;
    boolean inferredDistributionFromFirstModel = false;
    GLMModel firstGLM = null;
    boolean blending_mode = _parms._blending != null;
    boolean cv_required_on_base_model = !blending_mode;
    boolean require_consistent_training_frames = !blending_mode && !_parms._is_cv_model;

    //following variables are collected from the 1st base model (should be identical across base models), i.e. when beenHere=false
    int basemodel_nfolds = -1;
    Model.Parameters.FoldAssignmentScheme basemodel_fold_assignment = null;
    String basemodel_fold_column = null;
    long seed = -1;
    //end 1st model collected fields

    // Make sure we can set metalearner's family and link if needed
    if (_parms._metalearner_parameters == null) {
      _parms.initMetalearnerParams();
    }

    for (Key<Model> k : _parms._base_models) {
      aModel = DKV.getGet(k);
      if (null == aModel) {
        warn("base_models", "Failed to find base model; skipping: "+k);
        continue;
      }
      Log.debug("Checking properties for model "+k);
      if (!aModel.isSupervised()) {
        throw new H2OIllegalArgumentException("Base model is not supervised: "+aModel._key.toString());
      }

      if (retrievedFirstModelParams) {
        // check that the base models are all consistent with first based model

        if (seModel.modelCategory != aModel._output.getModelCategory())
          throw new H2OIllegalArgumentException("Base models are inconsistent: "
                  +"there is a mix of different categories of models among "+Arrays.toString(_parms._base_models));

        if (! seModel.responseColumn.equals(aModel._parms._response_column))
          throw new H2OIllegalArgumentException("Base models are inconsistent: they use different response columns."
                  +" Found: " + seModel.responseColumn + " (StackedEnsemble) and "+aModel._parms._response_column+" (model "+k+").");

        if (require_consistent_training_frames) {
          if (seModel.trainingFrameRows < 0) seModel.trainingFrameRows = _parms.train().numRows();
          long numOfRowsUsedToTrain = aModel._parms.train() == null ?
                  aModel._output._cross_validation_holdout_predictions_frame_id.get().numRows() :
                  aModel._parms.train().numRows();
          if (seModel.trainingFrameRows != numOfRowsUsedToTrain)
            throw new H2OIllegalArgumentException("Base models are inconsistent: they use different size (number of rows) training frames."
                    +" Found: "+seModel.trainingFrameRows+" (StackedEnsemble) and "+numOfRowsUsedToTrain+" (model "+k+").");
        }

        if (cv_required_on_base_model) {

          if (aModel._parms._fold_assignment != basemodel_fold_assignment
                  && !(aModel._parms._fold_assignment == AUTO && basemodel_fold_assignment == Random)
          ) {
            warn("base_models", "Base models are inconsistent: they use different fold_assignments. This can lead to data leakage.");
          }

          if (aModel._parms._fold_column == null) {
            // If we don't have a fold_column require:
            // nfolds > 1
            // nfolds consistent across base models
            if (aModel._parms._nfolds < 2)
              throw new H2OIllegalArgumentException("Base model does not use cross-validation: "+aModel._parms._nfolds);
            if (basemodel_nfolds != aModel._parms._nfolds)
              warn("base_models", "Base models are inconsistent: they use different values for nfolds. This can lead to data leakage.");

            if (basemodel_fold_assignment == Random && aModel._parms._seed != seed)
              warn("base_models", "Base models are inconsistent: they use random-seeded k-fold cross-validation but have different seeds. This can lead to data leakage.");

          } else {
            if (!aModel._parms._fold_column.equals(basemodel_fold_column))
              warn("base_models", "Base models are inconsistent: they use different fold_columns. This can lead to data leakage.");
          }
          if (! aModel._parms._keep_cross_validation_predictions)
            throw new H2OIllegalArgumentException("Base model does not keep cross-validation predictions: "+aModel._parms._nfolds);
        }

        if (inferredDistributionFromFirstModel) {
          // Check inferred params and if they differ fallback to basic distribution of model category
          if (!(aModel instanceof DRFModel) && distributionFamily(aModel) == distributionFamily(seModel)) {
            boolean sameParams = true;
            switch (_parms._metalearner_parameters._distribution) {
              case custom:
                sameParams = _parms._metalearner_parameters._custom_distribution_func
                        .equals(aModel._parms._custom_distribution_func);
                break;
              case huber:
                sameParams = _parms._metalearner_parameters._huber_alpha == aModel._parms._huber_alpha;
                break;
              case tweedie:
                sameParams = _parms._metalearner_parameters._tweedie_power == aModel._parms._tweedie_power;
                break;
              case quantile:
                sameParams = _parms._metalearner_parameters._quantile_alpha == aModel._parms._quantile_alpha;
                break;
            }

            if ((aModel instanceof GLMModel) && (Metalearners.getActualMetalearnerAlgo(_parms._metalearner_algorithm) == Metalearner.Algorithm.glm)) {
              if (firstGLM == null) {
                firstGLM = (GLMModel) aModel;
                inheritFamilyAndParms(seModel, firstGLM._parms);
              } else {
                sameParams = ((GLMModel.GLMParameters) _parms._metalearner_parameters)._link.equals(((GLMModel) aModel)._parms._link);
              }
            }

            if (!sameParams) {
              warn("distribution", "Base models are inconsistent; they use same distribution but different parameters of " +
                      "the distribution. Reverting to default distribution.");
              inferBasicDistribution(seModel);
              inferredDistributionFromFirstModel = false;
            }
          } else {
            if (distributionFamily(aModel) != distributionFamily(seModel)) {
              // Distribution of base models differ
              warn("distribution","Base models are inconsistent; they use different distributions: "
                      + distributionFamily(seModel) + " and: " + distributionFamily(aModel) +
                      ". Reverting to default distribution.");
            } // else the first model was DRF/XRT so we don't want to warn
            inferBasicDistribution(seModel);
            inferredDistributionFromFirstModel = false;
          }
        }
      } else {
        // !retrievedFirstModelParams: this is the first base_model
        seModel.modelCategory = aModel._output.getModelCategory();
        inferredDistributionFromFirstModel = inferDistributionOrFamily(seModel, aModel);
        firstGLM = aModel instanceof GLMModel && inferredDistributionFromFirstModel ? (GLMModel) aModel : null;
        seModel.responseColumn = aModel._parms._response_column;

        if (! _parms._response_column.equals(seModel.responseColumn))  // _params._response_column can't be null, validated by ModelBuilder
          throw new H2OIllegalArgumentException("StackedModel response_column must match the response_column of each base model."
                  +" Found: "+_parms._response_column+"(StackedEnsemble) and: "+seModel.responseColumn+" (model "+k+").");

        basemodel_nfolds = aModel._parms._nfolds;
        basemodel_fold_assignment = aModel._parms._fold_assignment;
        if (basemodel_fold_assignment == AUTO) basemodel_fold_assignment = Random;
        basemodel_fold_column = aModel._parms._fold_column;
        seed = aModel._parms._seed;
        retrievedFirstModelParams = true;
      }

    } // for all base_models

    if (null == aModel)
      throw new H2OIllegalArgumentException("When creating a StackedEnsemble you must specify one or more models; "
              +_parms._base_models.length+" were specified but none of those were found: "+Arrays.toString(_parms._base_models));
  }

  private abstract class StackedEnsembleDriver extends Driver {

    /**
     * Prepare a "level one" frame for a given set of models, predictions-frames and actuals.  Used for preparing
     * training and validation frames for the metalearning step, and could also be used for bulk predictions for
     * a StackedEnsemble.
     */
    private Frame prepareLevelOneFrame(String levelOneKey, Model[] baseModels, Frame[] baseModelPredictions, Frame actuals) {
      if (null == baseModels) throw new H2OIllegalArgumentException("Base models array is null.");
      if (null == baseModelPredictions) throw new H2OIllegalArgumentException("Base model predictions array is null.");
      if (baseModels.length == 0) throw new H2OIllegalArgumentException("Base models array is empty.");
      if (baseModelPredictions.length == 0)
        throw new H2OIllegalArgumentException("Base model predictions array is empty.");
      if (baseModels.length != baseModelPredictions.length)
        throw new H2OIllegalArgumentException("Base models and prediction arrays are different lengths.");
      final StackedEnsembleModel.StackedEnsembleParameters.MetalearnerTransform transform;
      if (_parms._metalearner_transform != null && _parms._metalearner_transform != StackedEnsembleModel.StackedEnsembleParameters.MetalearnerTransform.NONE) {
        if (!(_model._output.isBinomialClassifier() || _model._output.isMultinomialClassifier()))
          throw new H2OIllegalArgumentException("Metalearner transform is supported only for classification!");
        transform = _parms._metalearner_transform;
      } else {
        transform = null;
      }

      if (null == levelOneKey) levelOneKey = "levelone_" + _model._key.toString() + "_" + _parms._metalearner_transform.toString();

      // TODO: what if we're running multiple in parallel and have a name collision?
      Frame old = DKV.getGet(levelOneKey);
      if (old != null && old instanceof Frame) {
        Frame oldFrame = (Frame) old;
        oldFrame.write_lock(_job);
        // Remove ALL the columns, so we don't delete them in remove_impl.  Their
        // lifetime is controlled by their model.
        oldFrame.removeAll();
        oldFrame.update(_job);
        oldFrame.unlock(_job);
      }

      Frame levelOneFrame = transform == null ?
              new Frame(Key.make(levelOneKey))  // no tranform -> this will be the final frame 
              :
              new Frame();                      // tranform -> this is only an intermediate result

      for (int i = 0; i < baseModels.length; i++) {
        Model baseModel = baseModels[i];
        Frame baseModelPreds = baseModelPredictions[i];

        if (null == baseModel) {
          Log.warn("Failed to find base model; skipping: " + baseModels[i]);
          continue;
        }
        if (null == baseModelPreds) {
          Log.warn("Failed to find base model " + baseModel + " predictions; skipping: " + baseModelPreds._key);
          continue;
        }
        StackedEnsemble.addModelPredictionsToLevelOneFrame(baseModel, baseModelPreds, levelOneFrame);
        Scope.untrack(baseModelPredictions);
      }

      if (transform != null) {
        levelOneFrame = _parms._metalearner_transform.transform(_model, levelOneFrame, Key.make(levelOneKey));
      }

      // Add metalearner fold column, weights column to level one frame if it exists
      addNonPredictorsToLevelOneFrame(_model._parms, actuals, levelOneFrame, true);

      Log.info("Finished creating \"level one\" frame for stacking: " + levelOneFrame.toString());
      DKV.put(levelOneFrame);
      return levelOneFrame;
    }

    /**
     * Prepare a "level one" frame for a given set of models and actuals.
     * Used for preparing validation frames for the metalearning step, and could also be used for bulk predictions for a StackedEnsemble.
     */
    private Frame prepareLevelOneFrame(String levelOneKey, Key<Model>[] baseModelKeys, Frame actuals, boolean isTraining) {
      List<Model> baseModels = new ArrayList<>();
      List<Frame> baseModelPredictions = new ArrayList<>();

      for (Key<Model> k : baseModelKeys) {
        if (_model._output._metalearner == null || _model.isUsefulBaseModel(k)) {
          Model aModel = DKV.getGet(k);
          if (null == aModel)
            throw new H2OIllegalArgumentException("Failed to find base model: " + k);

          Frame predictions = getPredictionsForBaseModel(aModel, actuals, isTraining);
          baseModels.add(aModel);
          baseModelPredictions.add(predictions);
        }
      }
      boolean keepLevelOneFrame = isTraining && _parms._keep_levelone_frame;
      Frame levelOneFrame = prepareLevelOneFrame(levelOneKey, baseModels.toArray(new Model[0]), baseModelPredictions.toArray(new Frame[0]), actuals);
      if (keepLevelOneFrame) {
        levelOneFrame = levelOneFrame.deepCopy(levelOneFrame._key.toString());
        levelOneFrame.write_lock(_job);
        levelOneFrame.update(_job);
        levelOneFrame.unlock(_job);
        Scope.untrack(levelOneFrame.keysList());
      }
      return levelOneFrame;
    }

    @Override
    public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
      if (_model != null) _model.delete();
      return super.onExceptionalCompletion(ex, caller);
    }

    protected Frame buildPredictionsForBaseModel(Model model, Frame frame) {
      Key<Frame> predsKey = buildPredsKey(model, frame);
      Frame preds = DKV.getGet(predsKey);
      if (preds == null) {
        preds =  model.score(frame, predsKey.toString(), null, false);  // no need for metrics here (leaks in client mode)
        Scope.untrack(preds.keysList());
      }
      if (_model._output._base_model_predictions_keys == null)
        _model._output._base_model_predictions_keys = new Key[0];

      if (!ArrayUtils.contains(_model._output._base_model_predictions_keys, predsKey)){
        _model._output._base_model_predictions_keys = ArrayUtils.append(_model._output._base_model_predictions_keys, predsKey);
      }
      //predictions are cleaned up by metalearner if necessary
      return preds;
    }

    TwoDimTable generateModelSummary() {
      HashMap<String, Integer> baseModelTypes = new HashMap<>();
      HashMap<String, Integer> usedBaseModelTypes = new HashMap<>();

      for (Key bmk : _model._parms._base_models) {
        Model bm = (Model) bmk.get();
        if (_model.isUsefulBaseModel(bmk))
          usedBaseModelTypes.put(bm._parms.algoName(), usedBaseModelTypes.containsKey(bm._parms.algoName()) ? usedBaseModelTypes.get(bm._parms.algoName()) + 1 : 1);
        baseModelTypes.put(bm._parms.algoName(), baseModelTypes.containsKey(bm._parms.algoName()) ? baseModelTypes.get(bm._parms.algoName()) + 1 : 1);
      }
      List<String> rowHeaders = new ArrayList<>();
      List<String> rowValues = new ArrayList<>();
      rowHeaders.add("Stacking strategy");
      rowValues.add(_model._output._stacking_strategy.toString());
      rowHeaders.add("Number of base models (used / total)");
      rowValues.add(Arrays.stream(_model._parms._base_models).filter(_model::isUsefulBaseModel).count() + "/" + _model._parms._base_models.length);
      for (Map.Entry<String, Integer> baseModelType : baseModelTypes.entrySet()) {
        rowHeaders.add("# " + baseModelType.getKey() + " base models (used / total)");
        rowValues.add(((usedBaseModelTypes.containsKey(baseModelType.getKey())) ?
                usedBaseModelTypes.get(baseModelType.getKey()) : "0") + "/" + baseModelType.getValue());
      }

      // Metalearner
      rowHeaders.add("Metalearner algorithm");
      rowValues.add(_model._output._metalearner._parms.algoName());
      rowHeaders.add("Metalearner fold assignment scheme");
      rowValues.add(_model._output._metalearner._parms._fold_assignment == null ? "AUTO" : _model._output._metalearner._parms._fold_assignment.name());
      rowHeaders.add("Metalearner nfolds");
      rowValues.add(""+_model._output._metalearner._parms._nfolds);
      rowHeaders.add("Metalearner fold_column");
      rowValues.add(_model._output._metalearner._parms._fold_column);
      rowHeaders.add("Custom metalearner hyperparameters");
      rowValues.add(_model._parms._metalearner_params.isEmpty()? "None" : _model._parms._metalearner_params);

      TwoDimTable ms = new TwoDimTable("Model Summary for Stacked Ensemble", "",
              rowHeaders.toArray(new String[]{}),
              new String[]{"Value"},
              new String[]{"string"},
              new String[]{"%s"},
              "Key"
              );
      int i = 0;
      for (String val : rowValues){
        ms.set(i++, 0, val);
      }
      return ms;
    }
    protected abstract StackedEnsembleModel.StackingStrategy strategy();

    /**
     * @RETURN THE FRAME THAT IS USED TO COMPUTE THE PREDICTIONS FOR THE LEVEL-ONE TRAINING FRAME.
     */
    protected abstract Frame getActualTrainingFrame();

    protected abstract Frame getPredictionsForBaseModel(Model model, Frame actualsFrame, boolean isTrainingFrame);

    private Key<Frame> buildPredsKey(Key model_key, long model_checksum, Key frame_key, long frame_checksum) {
      return Key.make("preds_" + model_checksum + "_on_" + frame_checksum);
    }

    protected Key<Frame> buildPredsKey(Model model, Frame frame) {
      return frame == null || model == null ? null : buildPredsKey(model._key, model.checksum(), frame._key, frame.checksum());
    }

    public void computeImpl() {
      init(true);
      if (error_count() > 0) throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(StackedEnsemble.this);

      _model = new StackedEnsembleModel(dest(), _parms, new StackedEnsembleModel.StackedEnsembleOutput(StackedEnsemble.this));
      _model._output._stacking_strategy = strategy();
      try {
        _model.delete_and_lock(_job); // and clear & write-lock it (smashing any prior)
        checkAndInheritModelProperties(_model);
        _model.update(_job);
      } finally {
        _model.unlock(_job);
      }

      String levelOneTrainKey = "levelone_training_" + _model._key.toString();
      Frame levelOneTrainingFrame = prepareLevelOneFrame(levelOneTrainKey, _model._parms._base_models, getActualTrainingFrame(), true);
      Frame levelOneValidationFrame = null;
      if (_model._parms.valid() != null) {
        String levelOneValidKey = "levelone_validation_" + _model._key.toString();
        levelOneValidationFrame = prepareLevelOneFrame(levelOneValidKey, _model._parms._base_models, _model._parms.valid(), false);
      }

      Metalearner.Algorithm metalearnerAlgoSpec = _model._parms._metalearner_algorithm;
      Metalearner.Algorithm metalearnerAlgoImpl = Metalearners.getActualMetalearnerAlgo(metalearnerAlgoSpec);

      // Compute metalearner
      if(metalearnerAlgoImpl != null) {
        Key<Model> metalearnerKey = Key.<Model>make("metalearner_" + metalearnerAlgoSpec + "_" + _model._key);

        Job metalearnerJob = new Job<>(metalearnerKey, ModelBuilder.javaName(metalearnerAlgoImpl.toString()),
                "StackingEnsemble metalearner (" + metalearnerAlgoSpec + ")");
        //Check if metalearner_params are passed in
        boolean hasMetaLearnerParams = _model._parms._metalearner_parameters != null;
        long metalearnerSeed = _model._parms._seed;

        Metalearner metalearner = Metalearners.createInstance(metalearnerAlgoSpec.name());
        metalearner.init(
                levelOneTrainingFrame,
                levelOneValidationFrame,
                _model._parms._metalearner_parameters,
                _model,
                _job,
                metalearnerKey,
                metalearnerJob,
                _parms,
                hasMetaLearnerParams,
                metalearnerSeed,
                _parms._max_runtime_secs == 0 ? 0 : Math.max(remainingTimeSecs(), 1)
        );
        metalearner.compute();
      } else {
        throw new H2OIllegalArgumentException("Invalid `metalearner_algorithm`. Passed in " + metalearnerAlgoSpec +
                " but must be one of " + Arrays.toString(Metalearner.Algorithm.values()));
      }
      if (_model.evalAutoParamsEnabled && _model._parms._metalearner_algorithm == Metalearner.Algorithm.AUTO)
        _model._parms._metalearner_algorithm = metalearnerAlgoImpl;
      _model._output._model_summary = generateModelSummary();
    } // computeImpl
  }

  private class StackedEnsembleCVStackingDriver extends StackedEnsembleDriver {

    @Override
    protected StackedEnsembleModel.StackingStrategy strategy() {
      return StackedEnsembleModel.StackingStrategy.cross_validation;
    }

    @Override
    protected Frame getActualTrainingFrame() {
      return _model._parms.train();
    }

    @Override
    protected Frame getPredictionsForBaseModel(Model model, Frame actualsFrame, boolean isTraining) {
      Frame fr;
      if (isTraining) {
        // for training, retrieve predictions from cv holdout predictions frame as all base models are required to get built with keep_cross_validation_frame=true
        if (null == model._output._cross_validation_holdout_predictions_frame_id)
          throw new H2OIllegalArgumentException("Failed to find the xval predictions frame id. . .  Looks like keep_cross_validation_predictions wasn't set when building the models.");

        fr = DKV.getGet(model._output._cross_validation_holdout_predictions_frame_id);

        if (null == fr)
          throw new H2OIllegalArgumentException("Failed to find the xval predictions frame. . .  Looks like keep_cross_validation_predictions wasn't set when building the models, or the frame was deleted.");

      } else {
        fr = buildPredictionsForBaseModel(model, actualsFrame);
      }
      return fr;
    }

  }

  private class StackedEnsembleBlendingDriver extends StackedEnsembleDriver {

    @Override
    protected StackedEnsembleModel.StackingStrategy strategy() {
      return StackedEnsembleModel.StackingStrategy.blending;
    }

    @Override
    protected Frame getActualTrainingFrame() {
      return _model._parms.blending();
    }

    @Override
    protected Frame getPredictionsForBaseModel(Model model, Frame actualsFrame, boolean isTrainingFrame) {
      // if training we can stop prematurely due to a timeout but computing validation scores should be allowed to finish
      if (stop_requested() && isTrainingFrame) {
        throw new Job.JobCancelledException();
      }
      return buildPredictionsForBaseModel(model, actualsFrame);
    }
  }

}
