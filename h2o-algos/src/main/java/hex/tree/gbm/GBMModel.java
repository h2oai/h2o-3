package hex.tree.gbm;

import hex.Distribution;
import hex.KeyValue;
import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.*;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.SBPrintStream;

import java.util.Arrays;

public class GBMModel extends SharedTreeModel<GBMModel, GBMModel.GBMParameters, GBMModel.GBMOutput> implements Model.StagedPredictions {

  public static class GBMParameters extends SharedTreeModel.SharedTreeParameters {
    public double _learn_rate;
    public double _learn_rate_annealing;
    public double _col_sample_rate;
    public double _max_abs_leafnode_pred;
    public double _pred_noise_bandwidth;
    public KeyValue[] _monotone_constraints;

    public GBMParameters() {
      super();
      _learn_rate = 0.1;
      _learn_rate_annealing = 1.0;
      _col_sample_rate = 1.0;
      _sample_rate = 1.0;
      _ntrees = 50;
      _max_depth = 5;
      _max_abs_leafnode_pred = Double.MAX_VALUE;
      _pred_noise_bandwidth =0;
    }

    public String algoName() { return "GBM"; }
    public String fullName() { return "Gradient Boosting Machine"; }
    public String javaName() { return GBMModel.class.getName(); }

    Constraints constraints(Frame f) {
      if (_monotone_constraints == null || _monotone_constraints.length == 0) {
        return emptyConstraints(f);
      }
      int[] cs = new int[f.numCols()];
      for (KeyValue spec : _monotone_constraints) {
        if (spec.getValue() == 0)
          continue;
        int col = f.find(spec.getKey());
        if (col < 0) {
          throw new IllegalStateException("Invalid constraint specification, column '" + spec.getKey() + "' doesn't exist.");
        }
        cs[col] = spec.getValue() < 0 ? -1 : 1;
      }
      boolean useBounds = _distribution == DistributionFamily.gaussian ||
              _distribution == DistributionFamily.bernoulli ||
              _distribution == DistributionFamily.quasibinomial ||
              _distribution == DistributionFamily.multinomial;
      return new Constraints(cs, _distribution, useBounds);
    }

    // allows to override the behavior in tests (eg. create empty constraints and test execution as if constraints were used)
    Constraints emptyConstraints(Frame f) {
      return null;
    }
    
  }

  public static class GBMOutput extends SharedTreeModel.SharedTreeOutput {
    boolean _quasibinomial;
    int _nclasses;
    public int nclasses() {
      return _nclasses;
    }
    public GBMOutput(GBM b) {
      super(b);
      _quasibinomial = b._parms._distribution == DistributionFamily.quasibinomial;
      _nclasses = b.nclasses();
    }
    @Override
    public String[] classNames() {
      String [] res = super.classNames();
      if(res == null && _quasibinomial)
        return new String[]{"0", "1"};
      return res;
    }
  }


  public GBMModel(Key<GBMModel> selfKey, GBMParameters parms, GBMOutput output) {
    super(selfKey,parms,output);
  }

  @Override
  public Frame scoreStagedPredictions(Frame frame, Key<Frame> destination_key) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);

    final String[] names = makeAllTreeColumnNames();
    final int outputcols = names.length;

    return new StagedPredictionsTask(this)
            .doAll(outputcols, Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, names, null);
  }

  private static class StagedPredictionsTask extends MRTask<StagedPredictionsTask> {
    private final Key<GBMModel> _modelKey;

    private transient GBMModel _model;

    private StagedPredictionsTask(GBMModel model) {
      _modelKey = model._key;
    }

    @Override
    protected void setupLocal() {
      _model = _modelKey.get();
      assert _model != null;
    }

    @Override
    public void map(Chunk chks[], NewChunk[] nc) {
      double[] input = new double[chks.length];
      int contribOffset = _model._output.nclasses() == 1 ? 0 : 1;

      for (int row = 0; row < chks[0]._len; row++) {
        for (int i = 0; i < chks.length; i++)
          input[i] = chks[i].atd(row);

        double[] contribs = new double[contribOffset + _model._output.nclasses()];
        double[] preds = new double[contribs.length];

        int col = 0;
        for (int tidx = 0; tidx < _model._output._treeKeys.length; tidx++) {
          Key[] keys = _model._output._treeKeys[tidx];
          for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null)
              contribs[contribOffset + i] += DKV.get(keys[i]).<CompressedTree>get().score(input, _model._output._domains);
            preds[contribOffset + i] = contribs[contribOffset + i];
          }
          _model.score0Probabilities(preds, 0);
          _model.score0PostProcessSupervised(preds, input);
          for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null)
              nc[col++].addNum(preds[contribOffset + i]);
          }
        }
        assert (col == nc.length);
      }
    }
  }

  @Override
  protected final double[] score0Incremental(Score.ScoreIncInfo sii, Chunk[] chks, double offset, int row_in_chunk, double[] tmp, double[] preds) {
    assert _output.nfeatures() == tmp.length;
    for (int i = 0; i < tmp.length; i++)
      tmp[i] = chks[i].atd(row_in_chunk);

    if (sii._startTree == 0)
      Arrays.fill(preds,0);
    else
      for (int i = 0; i < sii._workspaceColCnt; i++)
        preds[sii._predsAryOffset + i] = chks[sii._workspaceColIdx + i].atd(row_in_chunk);

    score0(tmp, preds, offset, sii._startTree, _output._treeKeys.length);

    for (int i = 0; i < sii._workspaceColCnt; i++)
      chks[sii._workspaceColIdx + i].set(row_in_chunk, preds[sii._predsAryOffset + i]);

    score0Probabilities(preds, offset);
    score0PostProcessSupervised(preds, tmp);
    return preds;
  }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/], double offset, int ntrees) {
    super.score0(data, preds, offset, ntrees);    // These are f_k(x) in Algorithm 10.4
    return score0Probabilities(preds, offset);
  }

  private double[] score0Probabilities(double preds[/*nclasses+1*/], double offset) {
    if (_parms._distribution == DistributionFamily.bernoulli
        || _parms._distribution == DistributionFamily.quasibinomial
        || _parms._distribution == DistributionFamily.modified_huber) {
      double f = preds[1] + _output._init_f + offset; //Note: class 1 probability stored in preds[1] (since we have only one tree)
      preds[2] = new Distribution(_parms).linkInv(f);
      preds[1] = 1.0 - preds[2];
    } else if (_parms._distribution == DistributionFamily.multinomial) { // Kept the initial prediction for binomial
      if (_output.nclasses() == 2) { //1-tree optimization for binomial
        preds[1] += _output._init_f + offset; //offset is not yet allowed, but added here to be future-proof
        preds[2] = -preds[1];
      }
      hex.genmodel.GenModel.GBM_rescale(preds);
    } else { //Regression
      double f = preds[0] + _output._init_f + offset;
      preds[0] = new Distribution(_parms).linkInv(f);
    }
    return preds;
  }

  // Note: POJO scoring code doesn't support per-row offsets (the scoring API would need to be changed to pass in offsets)
  @Override protected void toJavaUnifyPreds(SBPrintStream body) {
    // Preds are filled in from the trees, but need to be adjusted according to
    // the loss function.
    if( _parms._distribution == DistributionFamily.bernoulli
        || _parms._distribution == DistributionFamily.quasibinomial
        || _parms._distribution == DistributionFamily.modified_huber
        ) {
      body.ip("preds[2] = preds[1] + ").p(_output._init_f).p(";").nl();
      body.ip("preds[2] = " + new Distribution(_parms).linkInvString("preds[2]") + ";").nl();
      body.ip("preds[1] = 1.0-preds[2];").nl();
      if (_parms._balance_classes)
        body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
      body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + defaultThreshold() + ");").nl();
      return;
    }
    if( _output.nclasses() == 1 ) { // Regression
      body.ip("preds[0] += ").p(_output._init_f).p(";").nl();
      body.ip("preds[0] = " + new Distribution(_parms).linkInvString("preds[0]") + ";").nl();
      return;
    }
    if( _output.nclasses()==2 ) { // Kept the initial prediction for binomial
      body.ip("preds[1] += ").p(_output._init_f).p(";").nl();
      body.ip("preds[2] = - preds[1];").nl();
    }
    body.ip("hex.genmodel.GenModel.GBM_rescale(preds);").nl();
    if (_parms._balance_classes)
      body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
    body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + defaultThreshold() + ");").nl();
  }


  @Override
  public GbmMojoWriter getMojo() {
    return new GbmMojoWriter(this);
  }

}
