package hex.ensemble;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.genmodel.utils.LinkFunctionType;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.Log;
import water.util.MRUtils;
import water.util.TwoDimTable;
import water.util.fp.Function2;

import java.util.*;
import java.util.stream.Stream;

import static hex.Model.Contributions.ContributionsOutputFormat.Original;
import static hex.Model.Parameters.FoldAssignmentScheme.AUTO;
import static hex.Model.Parameters.FoldAssignmentScheme.Random;

/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsembleModel 
        extends Model<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> 
        implements Model.Contributions{

  // common parameters for the base models (keeping them public for backwards compatibility, although it's nonsense)
  public ModelCategory modelCategory;
  public long trainingFrameRows = -1;

  public String responseColumn = null;

  class GDeepSHAP extends MRTask<GDeepSHAP> {
    final String[] _columns;
    final int[][] _baseIdx;
    final int[] _metaIdx;
    final int[] _levelOneIdx;
    final int _biasTermIdx;
    final int _biasTermSrc;
    final Integer[] _baseModelIdx;
    final int[] _biasTermIndices;
    final int[] _rowIndices;
    final int[] _rowBgIndices;
    
    final StackedEnsembleParameters.MetalearnerTransform _metaLearnerTransform;
    
    GDeepSHAP(String[] columns, String[] baseModels, String[] bigFrameColumnsArr, Integer[] baseModelIdx, StackedEnsembleParameters.MetalearnerTransform metaLearnerTransform) {
      _columns = columns;
      _baseIdx = new int[columns.length][baseModels.length];
      _metaIdx = new int[baseModels.length];
      _levelOneIdx = new int[baseModels.length];
      _biasTermIdx = columns.length;
      List<String> bigFrameColumns = Arrays.asList(bigFrameColumnsArr);
      _biasTermSrc = bigFrameColumns.indexOf("metalearner_BiasTerm");
      _baseModelIdx = baseModelIdx;
      _metaLearnerTransform = metaLearnerTransform;
      _biasTermIndices = new int[baseModels.length];
      _rowIndices = new int[baseModels.length + 1];
      _rowBgIndices = new int[baseModels.length + 1];

      for (int i = 0; i < columns.length; i++) {
        for (int j = 0; j < baseModels.length; j++) {
          _baseIdx[i][j] = bigFrameColumns.indexOf(baseModels[j] + "_" + columns[i]);
        }
      }
      for (int i = 0; i < baseModels.length; i++) {
        _metaIdx[i] = bigFrameColumns.indexOf("metalearner_" + baseModels[i]);
        _levelOneIdx[i] = bigFrameColumns.indexOf(baseModels[i]);
        _biasTermIndices[i] = bigFrameColumns.indexOf(baseModels[i]+"_RowIdx");
        _rowIndices[i] = bigFrameColumns.indexOf(baseModels[i]+"_RowIdx");
        _rowBgIndices[i] = bigFrameColumns.indexOf(baseModels[i]+"_BackgroundRowIdx");
      }
      _rowIndices[baseModels.length] = bigFrameColumns.indexOf("metalearner_RowIdx");
      _rowBgIndices[baseModels.length] = bigFrameColumns.indexOf("metalearner_BackgroundRowIdx");
    }


    private double baseModelContribution(Chunk[] chunks, int rowIdx, int baseModelIdx, int featureIdx) {
      return chunks[_baseIdx[featureIdx][baseModelIdx]].atd(rowIdx);
    }

    private double metalearnerContribution(Chunk[] chunks, int rowIdx, int baseModelIdx) {
      return chunks[_metaIdx[baseModelIdx]].atd(rowIdx);
    }


    private double baseModelBiasTerm(Chunk[] chunks, int rowIdx, int baseModelIdx) {
      return chunks[_biasTermIndices[baseModelIdx]].atd(rowIdx);
    }
    
    
    private double div(double a, double b) {
      return Math.abs(b) < 1e-6 ? 0 : a/b;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      // Multiplier is array of basemodel's contribution to metalearners prediction divided by the difference between 
      // prediction on the explained point and the background point. Simplified GDeepSHAP rescale rule for 2-layers:
      //
      //                                                                / phi_{i+1}_1_1 .. phi_{i+1}_1_m  \     / f_i_1(x) - f_i_1(b) \
      // phi_i (psi_{i+1} (/) (f_i(x)-f_i(b)) = (phi_i_1, ..., phi_i_n) |      ...           ...          | (/) |         ...         | =
      //                                                                \ phi_{i+1}_n_1 ..  phi_{i+1}_n_m /     \ f_i_n(x) - f_i_n(b) /
      //
      // = (sum_{k <= n} phi_i_k * phi_{i+1}_k_1 /  (f_i_k(x) - f_i_k(b)), ..., sum_{k <= n} phi_i_k * phi_{i+1}_k_m / (f_i_k(x) - f_i_k(b)))
      //
      // in this notation multiplier[k] = phi_i_k / (f_i_k(x) - f_i_k(b))

      double[] multiplier = MemoryManager.malloc8d(_metaIdx.length);
      double result = 0;
      for (int row = 0; row < cs[0]._len; row++) {
        // check if row == row and bg row  == bg row
        long rowIdx = cs[_rowIndices[0]].at8(row);
        long rowBgIdx = cs[_rowBgIndices[0]].at8(row);
        for (int i = 0; i < _rowIndices.length; i++) {
          assert rowIdx == cs[_rowIndices[i]].at8(row);
          assert rowBgIdx == cs[_rowBgIndices[i]].at8(row);
        }
        Arrays.fill(multiplier, 0);
        for (int bm = 0; bm < _baseModelIdx.length-1; bm++) {
          for (int col = 0; col < _columns.length; col++) {
            multiplier[bm] += baseModelContribution(cs, row, bm, col);
          }
          multiplier[bm] = div(metalearnerContribution(cs, row, bm), multiplier[bm]);
        }
        
        // Should we deal here with metalearner transform? No, since the transformation is one dimensional and
        // transforms all the inputs independently the generalized rescale rule cancels it out.
        //
        //    Let B stand for basemodel, L for logit metalearner transform, M for metalearner.
        //    Contributions of the transform sum up to f_L(x) - f_L(b) and since the transform is one dimensional
        //    we know that phi_L = f_L(x) - f_L(b).
        //    Let denote the final contributions as phi, (/) as Hadamard division and (*) Hadamard product.
        //
        //    Without the metalearner transform the generalized rescale rule gives us:
        //    phi = phi_B (phi_M (/) (f_B(x)-f_B(b)))
        //
        //    With metalearner transform we have:
        //    phi = (phi_B(phi_L(phi_M (/) (f_L(x)-f_L(b))) (/) (f_B(x)-f_B(b)))) = 
        //        = (phi_B((f_L(x) - f_L(b))(phi_M (*) (1 (/) (f_L(x)-f_L(b)))) (/) (f_B(x)-f_B(b)))) =
        //        # Hadamard product is commutative and associative 
        //        = (phi_B( (f_L(x) - f_L(b)) (/) (f_L(x)-f_L(b)) (*) phi_M)) (/) (f_B(x)-f_B(b)))) =
        //        = (phi_B( 1 (*) phi_M) (/) (f_B(x)-f_B(b)))) =
        //        = phi_B (phi_M (/) (f_B(x)-f_B(b)))


        for (int col = 0; col < ncs.length-3; col++) {
          result = 0;
          for (int bm = 0; bm < multiplier.length; bm++) {
            result += multiplier[bm]*baseModelContribution(cs, row, bm, col);
          }
          ncs[col].addNum(result);
        }
        ncs[ncs.length-3].addNum(cs[_biasTermSrc].atd(row));
        ncs[ncs.length-2].addNum(cs[_rowIndices[0]].at8(row));
        ncs[ncs.length-1].addNum(cs[_rowBgIndices[0]].at8(row));
      }
    }
  }

  int numOfUsefulBaseModels(){
    int result = 0;
    for (Key<Model> bm : _parms._base_models)
      if (isUsefulBaseModel(bm))
        result++;
    return result;
  }
  
  
  private Frame baseLineContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options, Frame backgroundFrame) {
    List<String> baseModels = new ArrayList<>();
    List<Integer> baseModelsIdx = new ArrayList<>();
    String[] columns = null;
    baseModelsIdx.add(0);
    try (Scope.Safe s = Scope.safe(frame, backgroundFrame)) {
      Frame fr = new Frame();
      for (Key<Model> bm : _parms._base_models) {
        if (isUsefulBaseModel(bm)) {
          baseModels.add(bm.toString());
          Frame contributions = ((Model.Contributions) bm.get()).scoreContributions(
                  frame,
                  Key.make(destination_key.toString()+"_"+bm),
                  j,
                  new ContributionsOptions()
                          .setOutputFormat(options._outputFormat)
                          .setOutputSpace(true)
                          .setOutputPerReference(true),
                  backgroundFrame);
          Scope.track(contributions);
          if (null == columns)
            columns = contributions._names;

          if (!Arrays.equals(columns, contributions._names)) {
            if (columns.length == contributions._names.length) {
              HashSet<String> colSet = new HashSet<>();
              List<String> colList = Arrays.asList(columns);
              List<String> contrList = Arrays.asList(contributions._names);
              colSet.addAll(colList);
              if (colSet.containsAll(contrList)) {
                int[] perm = new int[columns.length];
                for (int i = 0; i < columns.length; i++) {
                  perm[i] = contrList.indexOf(columns[i]);
                }
                contributions.reOrder(perm);
              }
            }
            if (!Arrays.equals(columns, contributions._names)) {
              if (Original.equals(options._outputFormat)) {
                throw new IllegalArgumentException("Base model contributions have different columns likely due to models using different categorical encoding. Please use output_format=\"compact\".");
              }
              throw new RuntimeException("Base model contributions have different columns. This is not expected. Please fill in a bug report.");
            }
          }
          contributions.setNames(
                  Arrays.stream(contributions._names)
                          .map(name -> bm+"_"+name)
                          .toArray(String[]::new)
          );
          fr.add(contributions);
          baseModelsIdx.add(fr.numCols());
        }
      }
      
      if (baseModels.isEmpty())
        throw new RuntimeException("Stacked Ensemble \"" + this._key + "\" doesn't use any base models. Stopping contribution calculation as no feature contributes.");

      assert columns[columns.length - 3].equals("BiasTerm") && columns[columns.length - 2].equals("RowIdx") && columns[columns.length - 1].equals("BackgroundRowIdx");
      String[] colsWithRows = columns;
      columns = Arrays.copyOfRange(columns, 0, columns.length - 3);

      Frame adaptFr = adaptFrameForScore(frame, false);
      Frame levelOneFrame = makeLevelOnePredictFrame(frame, adaptFr, j);

      Frame adaptFrBg = adaptFrameForScore(backgroundFrame, false);
      Frame levelOneFrameBg = makeLevelOnePredictFrame(backgroundFrame, adaptFrBg, j);

      Frame metalearnerContrib = ((Model.Contributions) _output._metalearner).scoreContributions(levelOneFrame,
              Key.make(destination_key + "_" + _output._metalearner._key), j,
              new ContributionsOptions()
                      .setOutputFormat(options._outputFormat)
                      .setOutputSpace(options._outputSpace)
                      .setOutputPerReference(true),
              levelOneFrameBg);
      Scope.track(metalearnerContrib);

      metalearnerContrib.setNames(Arrays.stream(metalearnerContrib._names)
              .map(name -> "metalearner_" + name)
              .toArray(String[]::new));

      fr.add(metalearnerContrib);
      DKV.remove(metalearnerContrib.getKey());
      
      return Scope.untrack(new GDeepSHAP(columns, baseModels.toArray(new String[0]),
              fr._names, baseModelsIdx.toArray(new Integer[0]), _parms._metalearner_transform)
              .withPostMapAction(JobUpdatePostMap.forJob(j))
              .doAll(colsWithRows.length, Vec.T_NUM, fr)
              .outputFrame(destination_key, colsWithRows, null));
    }
  }

  @Override
  public long scoreContributionsWorkEstimate(Frame frame, Frame backgroundFrame, boolean outputPerReference) {
    long workAmount = Math.max(frame.numRows(), backgroundFrame.numRows()); // Maps over the bigger frame while the smaller is sent across the cluster
    workAmount *= numOfUsefulBaseModels() + 1; // by each BaseModel and the metalearner
    workAmount += frame.numRows() * backgroundFrame.numRows(); // G-DeepSHAP work
    if (!outputPerReference)
      workAmount += frame.numRows() * backgroundFrame.numRows(); // Aggregating over the baselines
    return workAmount;
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options, Frame backgroundFrame) {
    if (null == backgroundFrame)
      throw H2O.unimpl("StackedEnsemble supports contribution calculation only with a background frame.");
    Log.info("Starting contributions calculation for " + this._key + "...");
    try (Scope.Safe safe = Scope.safe(frame, backgroundFrame)) {
      Frame contributions;
      if (options._outputPerReference) {
        contributions = baseLineContributions(frame, destination_key, j, options, backgroundFrame);
      } else {
        Function2<Frame, Boolean, Frame> fun = (subFrame, resultIsFinalFrame) -> {
          String[] columns = null;
          String[] colsWithBiasTerm = null;
          Frame indivContribs = baseLineContributions(subFrame, Key.make(destination_key+"_individual_contribs_for_subframe_"+subFrame._key), j, options, backgroundFrame);
          columns = Arrays.copyOf(indivContribs.names(), indivContribs.names().length-3);
          colsWithBiasTerm = Arrays.copyOf(indivContribs.names(), indivContribs.names().length-2);
          assert colsWithBiasTerm[colsWithBiasTerm.length-1].equals("BiasTerm");

          try {
            return new ContributionsMeanAggregator(j, (int) subFrame.numRows(), columns.length+1 /* (bias term) */, (int) backgroundFrame.numRows())
                    .withPostMapAction(JobUpdatePostMap.forJob(j))
                    .doAll(columns.length+1, Vec.T_NUM, indivContribs)
                    .outputFrame(resultIsFinalFrame
                                    ? destination_key // no subframes -> one result with the destination key
                                    : Key.make(destination_key+"_for_subframe_"+subFrame._key),
                            colsWithBiasTerm, null);
          } finally {
            indivContribs.delete(true);
          }
        };
        if (backgroundFrame.anyVec().nChunks() > H2O.CLOUD._memary.length || // could be map-reduced over the bg frame 
                !ContributionsWithBackgroundFrameTask.enoughMinMemory(numOfUsefulBaseModels() *
                        ContributionsWithBackgroundFrameTask.estimatePerNodeMinimalMemory(frame.numCols(), frame, backgroundFrame))) // or we have no other choice due to memory
          contributions = SplitToChunksApplyCombine.splitApplyCombine(frame, (fr -> fun.apply(fr, false)), destination_key);
        else {
          contributions = fun.apply(frame, true);
          DKV.put(contributions);
        }
      }
      return Scope.untrack(contributions);
    } finally {
      Log.info("Finished contributions calculation for " + this._key + "...");
    }
  }

  public enum StackingStrategy {
    cross_validation,
    blending
  }

  // TODO: add a separate holdout dataset for the ensemble
  // TODO: add a separate overall cross-validation for the ensemble, including _fold_column and FoldAssignmentScheme / _fold_assignment

  public StackedEnsembleModel(Key selfKey, StackedEnsembleParameters parms, StackedEnsembleOutput output) {
    super(selfKey, parms, output);
  }

  @Override
  public void initActualParamValues() {
    super.initActualParamValues();
    if (_parms._metalearner_fold_assignment == AUTO) {
      _parms._metalearner_fold_assignment = Random;
    }
  }

  @Override
  public boolean haveMojo() {
    return super.haveMojo() 
            && Stream.of(_parms._base_models)
                     .filter(this::isUsefulBaseModel)
                     .map(DKV::<Model>getGet)
                     .allMatch(Model::haveMojo);
  }

  public static class StackedEnsembleParameters extends Model.Parameters {
    public String algoName() { return "StackedEnsemble"; }
    public String fullName() { return "Stacked Ensemble"; }
    public String javaName() { return StackedEnsembleModel.class.getName(); }
    @Override public long progressUnits() { return 1; }  // TODO

    // base_models is a list of base model keys to ensemble (must have been cross-validated)
    public Key<Model> _base_models[] = new Key[0];
    // Should we keep the level-one frame of cv preds + response col?
    public boolean _keep_levelone_frame = false;
    // internal flag if we want to avoid having the base model predictions rescored multiple times, esp. for blending.
    public boolean _keep_base_model_predictions = false;

    // Metalearner params
    //for stacking using cross-validation
    public int _metalearner_nfolds;
    public Parameters.FoldAssignmentScheme _metalearner_fold_assignment;
    public String _metalearner_fold_column;
    //the training frame used for blending (from which predictions columns are computed)
    public Key<Frame> _blending;

    public enum MetalearnerTransform {
      NONE,
      Logit;

      private LinkFunction logitLink = LinkFunctionFactory.getLinkFunction(LinkFunctionType.logit);
       
      public Frame transform(StackedEnsembleModel model, Frame frame, Key<Frame> destKey) {
        if (this == Logit) {
          return new MRTask() {
            @Override
            public void map(Chunk[] cs, NewChunk[] ncs) {
              for (int c = 0; c < cs.length; c++) {
                for (int i = 0; i < cs[c]._len; i++) {
                  final double p = Math.min(1 - 1e-9, Math.max(cs[c].atd(i), 1e-9)); // 0 and 1 don't work well with logit
                  ncs[c].addNum(logitLink.link(p));
                }
              }
            }
          }.doAll(frame.numCols(), Vec.T_NUM, frame)
                  .outputFrame(destKey, frame._names, null);
        } else {
          throw H2O.unimpl("Transformation "+this.name()+" is not supported.");
        }
      }
    }

    public MetalearnerTransform _metalearner_transform = MetalearnerTransform.NONE;

    public Metalearner.Algorithm _metalearner_algorithm = Metalearner.Algorithm.AUTO;
    public String _metalearner_params = new String(); //used for clients code-gen only.
    public Model.Parameters _metalearner_parameters;
    public long _score_training_samples = 10_000;

    /**
     * initialize {@link #_metalearner_parameters} with default parameters for the current {@link #_metalearner_algorithm}.
     */
    public void initMetalearnerParams() {
      initMetalearnerParams(_metalearner_algorithm);
    }

    /**
     * initialize {@link #_metalearner_parameters} with default parameters for the given algorithm
     * @param algo the metalearner algorithm we want to use and for which parameters are initialized.
     */
    public void initMetalearnerParams(Metalearner.Algorithm algo) {
      _metalearner_algorithm = algo;
      _metalearner_parameters = Metalearners.createParameters(algo.name());
    }
    
    public final Frame blending() { return _blending == null ? null : _blending.get(); }

    @Override
    public String[] getNonPredictors() {
      HashSet<String> nonPredictors = new HashSet<>();
      nonPredictors.addAll(Arrays.asList(super.getNonPredictors()));
      if (null != _metalearner_fold_column)
        nonPredictors.add(_metalearner_fold_column);
      return nonPredictors.toArray(new String[0]);
    }

    @Override
    public DistributionFamily getDistributionFamily() {
      if (_metalearner_parameters != null)
        return _metalearner_parameters.getDistributionFamily();
      return super.getDistributionFamily();
    }

    @Override
    public void setDistributionFamily(DistributionFamily distributionFamily) {
      assert _metalearner_parameters != null;
      _metalearner_parameters.setDistributionFamily(distributionFamily);
    }
  }

  public static class StackedEnsembleOutput extends Model.Output {
    public StackedEnsembleOutput() { super(); }
    public StackedEnsembleOutput(StackedEnsemble b) { super(b); }

    public StackedEnsembleOutput(Job job) { _job = job; }
    // The metalearner model (e.g., a GLM that has a coefficient for each of the base_learners).
    public Model _metalearner;
    public Frame _levelone_frame_id; //set only if StackedEnsembleParameters#_keep_levelone_frame=true
    public StackingStrategy _stacking_strategy;
    
    //Set of base model predictions that have been cached in DKV to avoid scoring the same model multiple times,
    // it is then the responsibility of the client code to delete those frames from DKV.
    //This especially useful when building SE models incrementally (e.g. in AutoML).
    //The Set is instantiated and filled only if StackedEnsembleParameters#_keep_base_model_predictions=true.
    public Key<Frame>[] _base_model_predictions_keys;

    @Override
    public int nfeatures() {
      return super.nfeatures() - (_metalearner._parms._fold_column == null ? 0 : 1);
    }
  }

  /**
   * For StackedEnsemble we call score on all the base_models and then combine the results
   * with the metalearner to create the final predictions frame.
   *
   * @see Model#predictScoreImpl(Frame, Frame, String, Job, boolean, CFuncRef)
   * @param adaptFrm Already adapted frame
   * @param computeMetrics
   * @return A Frame containing the prediction column, and class distribution
   */
  @Override
  protected PredictScoreResult predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    try (Scope.Safe safe = Scope.safe(fr, adaptFrm)) {
      Frame levelOneFrame = makeLevelOnePredictFrame(fr, adaptFrm, j);
      // TODO: what if we're running multiple in parallel and have a name collision?
      Log.info("Finished creating \"level one\" frame for scoring: "+levelOneFrame.toString());

      // Score the dataset, building the class distribution & predictions

      Model metalearner = this._output._metalearner;
      Frame predictFr = metalearner.score(
              levelOneFrame,
              destination_key,
              j,
              computeMetrics,
              CFuncRef.from(_parms._custom_metric_func)
      );
      ModelMetrics mmStackedEnsemble = null;
      if (computeMetrics) {
        // #score has just stored a ModelMetrics object for the (metalearner, preds_levelone) Model/Frame pair.
        // We need to be able to look it up by the (this, fr) pair.
        // The ModelMetrics object for the metalearner will be removed when the metalearner is removed.
        Key<ModelMetrics>[] mms = metalearner._output.getModelMetrics();
        ModelMetrics lastComputedMetric = mms[mms.length-1].get();
        mmStackedEnsemble = lastComputedMetric.deepCloneWithDifferentModelAndFrame(this, fr);
        this.addModelMetrics(mmStackedEnsemble);
        //now that we have the metric set on the SE model, removing the one we just computed on metalearner (otherwise it leaks in client mode)
        for (Key<ModelMetrics> mm : metalearner._output.clearModelMetrics(true)) {
          DKV.remove(mm);
        }
      }
      Scope.untrack(predictFr); // needed in the result
      return new StackedEnsemblePredictScoreResult(predictFr, mmStackedEnsemble);
    }
  }

  private Frame makeLevelOnePredictFrame(Frame fr, Frame adaptFrm, Job j) {
    final StackedEnsembleParameters.MetalearnerTransform transform;
    if (_parms._metalearner_transform != null && _parms._metalearner_transform != StackedEnsembleParameters.MetalearnerTransform.NONE) {
      if (!(_output.isBinomialClassifier() || _output.isMultinomialClassifier()))
        throw new H2OIllegalArgumentException("Metalearner transform is supported only for classification!");
      transform = _parms._metalearner_transform;
    } else {
      transform = null;
    }
    final String seKey = this._key.toString();
    final String frId = ""+(fr._key == null ? fr.checksum() : fr._key);
    final Key<Frame> levelOneFrameKey = Key.make("preds_levelone_"+seKey+"_"+frId);
    Frame levelOneFrame = transform == null 
            ? new Frame(levelOneFrameKey)  // no transform -> this will be the final frame
            : new Frame();                 // transform -> this is only an intermediate result

    Model[] usefulBaseModels = Stream.of(_parms._base_models)
            .filter(this::isUsefulBaseModel)
            .map(Key::get)
            .toArray(Model[]::new);

    if (usefulBaseModels.length > 0) {
      Frame[] baseModelPredictions = new Frame[usefulBaseModels.length];

      // Run scoring of base models in parallel
      H2O.submitTask(new LocalMR(new MrFun() {
        @Override
        protected void map(int id) {
          baseModelPredictions[id] = usefulBaseModels[id].score(
                  fr,
                  "preds_base_"+seKey+"_"+usefulBaseModels[id]._key+"_"+frId,
                  j,
                  false
          );
        }
      }, usefulBaseModels.length)).join();

      for (int i = 0; i < usefulBaseModels.length; i++) {
        StackedEnsemble.addModelPredictionsToLevelOneFrame(usefulBaseModels[i], baseModelPredictions[i], levelOneFrame);
        DKV.remove(baseModelPredictions[i]._key); //Cleanup
        Frame.deleteTempFrameAndItsNonSharedVecs(baseModelPredictions[i], levelOneFrame);
      }
    }

    if (transform != null) {
      Frame oldLOF = levelOneFrame;
      levelOneFrame = transform.transform(this, levelOneFrame, levelOneFrameKey);
      oldLOF.remove();
    }
    // Add response column, weights columns to level one frame
    StackedEnsemble.addNonPredictorsToLevelOneFrame(_parms, adaptFrm, levelOneFrame, false);
    Scope.track(levelOneFrame); // level-one frame is always temporary and must be used in a scoped context.
    return levelOneFrame;
  }

  private class StackedEnsemblePredictScoreResult extends PredictScoreResult {
    private final ModelMetrics _modelMetrics;

    public StackedEnsemblePredictScoreResult(Frame preds, ModelMetrics modelMetrics) {
      super(null, preds, preds);
      _modelMetrics = modelMetrics;
    }

    @Override
    public ModelMetrics makeModelMetrics(Frame fr, Frame adaptFrm) {
      return _modelMetrics;
    }

    @Override
    public ModelMetrics.MetricBuilder<?> getMetricBuilder() {
      throw new UnsupportedOperationException("Stacked Ensemble model doesn't implement MetricBuilder infrastructure code, " +
              "retrieve your metrics by calling getOrMakeMetrics method.");
    }
  }
  
  /**
   * Is the baseModel's prediction used in the metalearner?
   *
   * @param baseModelKey
   */
  boolean isUsefulBaseModel(Key<Model> baseModelKey) {
    Model metalearner = _output._metalearner;
    assert metalearner != null : "can't use isUsefulBaseModel during training";
    if (modelCategory == ModelCategory.Multinomial) {
      // Multinomial models output multiple columns and a base model
      // might be useful just for one category...
      for (String feature : metalearner._output._names) {
        if (feature.startsWith(baseModelKey.toString().concat("/"))){
          if (metalearner.isFeatureUsedInPredict(feature)) {
            return true;
          }
        }
      }
      return false;
    } else {
      return metalearner.isFeatureUsedInPredict(baseModelKey.toString());
    }
  }

  /**
   * Should never be called: the code paths that normally go here should call predictScoreImpl().
   * @see Model#score0(double[], double[])
   */
  @Override
  protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    throw new UnsupportedOperationException("StackedEnsembleModel.score0() should never be called: the code paths that normally go here should call predictScoreImpl().");
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw new UnsupportedOperationException("StackedEnsembleModel.makeMetricBuilder should never be called!");
  }

  private ModelMetrics doScoreTrainingMetrics(Frame frame, Job job) {
    Frame scoredFrame = (_parms._score_training_samples > 0 && _parms._score_training_samples < frame.numRows())
            ? MRUtils.sampleFrame(frame, _parms._score_training_samples, _parms._seed)
            : frame;
    try {
      Frame adaptedFrame = new Frame(scoredFrame);
      PredictScoreResult result = predictScoreImpl(scoredFrame, adaptedFrame, null, job, true, CFuncRef.from(_parms._custom_metric_func));
      result.getPredictions().delete();
      return result.makeModelMetrics(scoredFrame, adaptedFrame);
    } finally {
      if (scoredFrame != frame) scoredFrame.delete();
    }
  }

  void doScoreOrCopyMetrics(Job job) {
    // To get ensemble training metrics, the training frame needs to be re-scored since
    // training metrics from metalearner are not equal to ensemble training metrics.
    // The training metrics for the metalearner do not reflect true ensemble training metrics because
    // the metalearner was trained on cv preds, not training preds.  So, rather than clone the metalearner
    // training metrics, we have to re-score the training frame on all the base models, then send these
    // biased preds through to the metalearner, and then compute the metrics there.
    //
    // Job set to null since `stop_requested()` due to timeout would invalidate the whole SE at this point
    // which would be unfortunate since this is the last step of SE training and it also should be relatively fast.
    this._output._training_metrics = doScoreTrainingMetrics(this._parms.train(), null);

    // Validation metrics can be copied from metalearner (may be null).
    // Validation frame was already piped through so there's no need to re-do that to get the same results.
    this._output._validation_metrics = this._output._metalearner._output._validation_metrics;
    
    // Cross-validation metrics can be copied from metalearner (may be null).
    // For cross-validation metrics, we use metalearner cross-validation metrics as a proxy for the ensemble
    // cross-validation metrics -- the true k-fold cv metrics for the ensemble would require training k sets of
    // cross-validated base models (rather than a single set of cross-validated base models), which is extremely
    // computationally expensive and awkward from the standpoint of the existing Stacked Ensemble API.
    // More info: https://github.com/h2oai/h2o-3/issues/10864
    // Need to do DeepClone because otherwise framekey is incorrect (metalearner train is levelone not train)
    if (null != this._output._metalearner._output._cross_validation_metrics) {
      this._output._cross_validation_metrics = this._output._metalearner._output._cross_validation_metrics
              .deepCloneWithDifferentModelAndFrame(this, this._output._metalearner._parms.train());
      this._output._cross_validation_metrics_summary = (TwoDimTable) this._output._metalearner._output._cross_validation_metrics_summary.clone();
    }
  }



  
  public void deleteBaseModelPredictions() {
    if (_output._base_model_predictions_keys != null) {
      for (Key<Frame> key : _output._base_model_predictions_keys) {
        if (_output._levelone_frame_id != null && key.get() != null)
          Frame.deleteTempFrameAndItsNonSharedVecs(key.get(), _output._levelone_frame_id);
        else
          Keyed.remove(key);
      }
      _output._base_model_predictions_keys = null;
    }
  }

  @Override protected Futures remove_impl(Futures fs, boolean cascade) {
    deleteBaseModelPredictions(); 
    if (_output._metalearner != null)
      _output._metalearner.remove(fs);
    if (_output._levelone_frame_id != null)
      _output._levelone_frame_id.remove(fs);

    return super.remove_impl(fs, cascade);
  }

  /** Write out models (base + metalearner) */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    //Metalearner
    ab.putKey(_output._metalearner._key);
    //Base Models
    for (Key<Model> ks : _parms._base_models)
        ab.putKey(ks);
    return super.writeAll_impl(ab);
  }

  /** Read in models (base + metalearner) */
  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    //Metalearner
    ab.getKey(_output._metalearner._key,fs);
    //Base Models
    for (Key<Model> ks : _parms._base_models)
      ab.getKey(ks,fs);
    return super.readAll_impl(ab,fs);
  }

  @Override
  public StackedEnsembleMojoWriter getMojo() {
    return new StackedEnsembleMojoWriter(this);
  }

  @Override
  public void deleteCrossValidationModels() {
    if (_output._metalearner != null)
      _output._metalearner.deleteCrossValidationModels();
  }

  @Override
  public void deleteCrossValidationPreds() {
    if (_output._metalearner != null)
      _output._metalearner.deleteCrossValidationPreds();
  }

  @Override
  public void deleteCrossValidationFoldAssignment() {
    if (_output._metalearner != null)
      _output._metalearner.deleteCrossValidationFoldAssignment();
  }
}
