package hex;

import hex.genmodel.GenModel;
import water.MRTask;
import water.Scope;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;
import water.util.TwoDimTable;

import java.util.Arrays;

public class ModelMetricsMultinomial extends ModelMetricsSupervised {
  public final float[] _hit_ratios;         // Hit ratios
  public final ConfusionMatrix _cm;
  public final double _logloss;
  public double _mean_per_class_error;
  public PairwiseAUC[] _aucs;
  public double _multinomial_auc;
  public double _multinomial_pr_auc;

  public ModelMetricsMultinomial(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma, ConfusionMatrix cm, float[] hr, double logloss, PairwiseAUC[] aucs, CustomMetric customMetric) {
    super(model, frame, nobs, mse, domain, sigma, customMetric);
    _cm = cm;
    _hit_ratios = hr;
    _logloss = logloss;
    _mean_per_class_error = cm==null || cm.tooLarge() ? Double.NaN : cm.mean_per_class_error();
    _aucs = aucs;
    if(_aucs != null) {
      for (PairwiseAUC auc : aucs) {
          _multinomial_auc += auc.getPairwiseAuc();
          _multinomial_pr_auc += auc.getPairwisePrAuc();
      }
      _multinomial_auc /= aucs.length;
      _multinomial_pr_auc /= aucs.length;
    } else {
      _multinomial_auc = -1;
      _multinomial_pr_auc = -1;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" logloss: " + (float)_logloss + "\n");
    sb.append(" mean_per_class_error: " + (float)_mean_per_class_error + "\n");
    sb.append(" hit ratios: " + Arrays.toString(_hit_ratios) + "\n");
    sb.append(" average multinomial_auc: "+ (float) _multinomial_auc + "\n");
    sb.append(" average multinomial_auc_pr: "+ (float) _multinomial_pr_auc + "\n");
    for(int i=0; i<_aucs.length; i++){
      sb.append(" multinomial_auc class "+_aucs[i].getPairwiseAucString()+ "\n");
    }
    for(int i=0; i<_aucs.length; i++){
      sb.append(" multinomial_pr_auc class "+_aucs[i].getPairwisePrAucString()+ "\n");
    }
    
    if (cm() != null) {
      if (cm().nclasses() <= 20)
        sb.append(" CM: " + cm().toASCII());
      else
        sb.append(" CM: too large to print.\n");
    }
    return sb.toString();
  }

  public double logloss() { return _logloss; }
  public double mean_per_class_error() { return _mean_per_class_error; }
  @Override public ConfusionMatrix cm() { return _cm; }
  @Override public float[] hr() { return _hit_ratios; }
  public double multinomial_auc(){ return _multinomial_auc; }
  public double multinomial_auc_pr() { return _multinomial_pr_auc;}
  
  public double multinomial_auc(String firstDomain, String secondDomain) {
    for(PairwiseAUC auc: _aucs){
      if(auc.hasDomains(firstDomain, secondDomain)){
        return auc.getPairwiseAuc();
      }
    }
    return Double.NaN;
  }
  
  public double multinomial_auc(int firstIndex, int secondIndex){
    for(PairwiseAUC auc: _aucs){
      if(auc.hasIndices(firstIndex, secondIndex)){
        return auc.getPairwiseAuc();
      }
    }
    return Double.NaN;
  }

  public double multinomial_auc_pr(String firstDomain, String secondDomain) {
    for(PairwiseAUC auc: _aucs){
      if(auc.hasDomains(firstDomain, secondDomain)){
        return auc.getPairwisePrAuc();
      }
    }
    return Double.NaN;
  }

  public double multinomial_auc_pr(int firstIndex, int secondIndex){
    for(PairwiseAUC auc: _aucs){
      if(auc.hasIndices(firstIndex, secondIndex)){
        return auc.getPairwisePrAuc();
      }
    }
    return Double.NaN;
  }

  public static ModelMetricsMultinomial getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsMultinomial))
      throw new H2OIllegalArgumentException("Expected to find a Multinomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsMultinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsMultinomial) mm;
  }

  public static void updateHits(double w, int iact, double[] ds, double[] hits) {
    updateHits(w, iact,ds,hits,null);
  }

  public static void updateHits(double w, int iact, double[] ds, double[] hits, double[] priorClassDistribution) {
    if (iact == ds[0]) { hits[0]++; return; }
    double before = ArrayUtils.sum(hits);
    // Use getPrediction logic to see which top K labels we would have predicted
    // Pick largest prob, assign label, then set prob to 0, find next-best label, etc.
    double[] ds_copy = Arrays.copyOf(ds, ds.length); //don't modify original ds!
    ds_copy[1+(int)ds[0]] = 0;
    for (int k=1; k<hits.length; ++k) {
      final int pred_labels = GenModel.getPrediction(ds_copy, priorClassDistribution, ds, 0.5 /*ignored*/); //use tie-breaking of getPrediction
      ds_copy[1+pred_labels] = 0; //next iteration, we'll find the next-best label
      if (pred_labels==iact) {
        hits[k]+=w;
        break;
      }
    }
    // must find at least one hit if K == n_classes
    if (hits.length == ds.length-1) {
      double after = ArrayUtils.sum(hits);
      if (after == before) hits[hits.length-1]+=w; //assume worst case
    }
  }

  public static TwoDimTable getHitRatioTable(float[] hits) {
    String tableHeader = "Top-" + hits.length + " Hit Ratios";
    String[] rowHeaders = new String[hits.length];
    for (int k=0; k<hits.length; ++k)
      rowHeaders[k] = Integer.toString(k+1);
    String[] colHeaders = new String[]{"Hit Ratio"};
    String[] colTypes = new String[]{"float"};
    String[] colFormats = new String[]{"%f"};
    String colHeaderForRowHeaders = "K";
    TwoDimTable table = new TwoDimTable(tableHeader, null/*tableDescription*/, rowHeaders, colHeaders, colTypes, colFormats, colHeaderForRowHeaders);
    for (int k=0; k<hits.length; ++k)
      table.set(k, 0, hits[k]);
    return table;
  }

  /**
   * Build a Multinomial ModelMetrics object from per-class probabilities (in Frame preds - no labels!), from actual labels, and a given domain for all possible labels (maybe more than what's in labels)
   * @param perClassProbs Frame containing predicted per-class probabilities (and no predicted labels)
   * @param actualLabels A Vec containing the actual labels (can be for fewer labels than what's in domain, since the predictions can be for a small subset of the data)
   * @return ModelMetrics object
   */
  static public ModelMetricsMultinomial make(Frame perClassProbs, Vec actualLabels) {
    String[] names = perClassProbs.names();
    String[] label = actualLabels.domain();
    String[] union = ArrayUtils.union(names, label, true);
    if (union.length == names.length + label.length)
      throw new IllegalArgumentException("Column names of per-class-probabilities and categorical domain of actual labels have no common values!");
    return make(perClassProbs, actualLabels, perClassProbs.names());
  }

  static public ModelMetricsMultinomial make(Frame perClassProbs, Vec actualLabels, String[] domain) {
    return make(perClassProbs, actualLabels, null, domain);
  }

  /**
   * Build a Multinomial ModelMetrics object from per-class probabilities (in Frame preds - no labels!), from actual labels, and a given domain for all possible labels (maybe more than what's in labels)
   * @param perClassProbs Frame containing predicted per-class probabilities (and no predicted labels)
   * @param actualLabels A Vec containing the actual labels (can be for fewer labels than what's in domain, since the predictions can be for a small subset of the data)
   * @param weights A Vec containing the observation weights.
   * @param domain Ordered list of factor levels for which the probabilities are given (perClassProbs[i] are the per-observation probabilities for belonging to class domain[i])
   * @return ModelMetrics object
   */
  static public ModelMetricsMultinomial make(Frame perClassProbs, Vec actualLabels, Vec weights, String[] domain) {
    Scope.enter();
    Vec labels = actualLabels.toCategoricalVec();
    if (labels == null || perClassProbs == null)
      throw new IllegalArgumentException("Missing actualLabels or predictedProbs for multinomial metrics!");
    if (labels.length() != perClassProbs.numRows())
      throw new IllegalArgumentException("Both arguments must have the same length for multinomial metrics (" + labels.length() + "!=" + perClassProbs.numRows() + ")!");
    for (Vec p : perClassProbs.vecs()) {
      if (!p.isNumeric())
        throw new IllegalArgumentException("Predicted probabilities must be numeric per-class probabilities for multinomial metrics.");
      if (p.min() < 0 || p.max() > 1)
        throw new IllegalArgumentException("Predicted probabilities must be between 0 and 1 for multinomial metrics.");
    }
    int nclasses = perClassProbs.numCols();
    if (domain.length!=nclasses)
      throw new IllegalArgumentException("Given domain has " + domain.length + " classes, but predictions have " + nclasses + " columns (per-class probabilities) for multinomial metrics.");
    labels = labels.adaptTo(domain);

    Frame fr = new Frame(perClassProbs);
    fr.add("labels", labels);
    if (weights != null) {
      fr.add("weights", weights);
    }
    MetricBuilderMultinomial mb = new MultinomialMetrics((labels.domain())).doAll(fr)._mb;
    labels.remove();
    ModelMetricsMultinomial mm = (ModelMetricsMultinomial)mb.makeModelMetrics(null, fr, null, null);
    mm._description = "Computed on user-given predictions and labels.";
    Scope.exit();
    return mm;
  }

  // helper to build a ModelMetricsMultinomial for a N-class problem from a Frame that contains N per-class probability columns, 
  // and the actual label as the (N+1)-th column with optional weights column at the end of the Frame
  private static class MultinomialMetrics extends MRTask<MultinomialMetrics> {
    private final String[] _domain;
    private MetricBuilderMultinomial _mb;

    MultinomialMetrics(String[] domain) { 
      _domain = domain;
    }

    @Override public void map(Chunk[] chks) {
      _mb = new MetricBuilderMultinomial(_domain.length, _domain);
      Chunk actuals = chks[_domain.length];
      Chunk weights = chks.length == _domain.length + 2 ? chks[_domain.length + 1] : null;
      double[] ds = new double[_domain.length + 1];
      float[] acts = new float[1];

      for (int i = 0; i < chks[0]._len; i++) {
        for (int c = 0; c < ds.length - 1; c++)
          ds[c + 1] = chks[c].atd(i); //per-class probs - user-given
        ds[0] = GenModel.getPrediction(ds, null, ds, 0.5 /*ignored*/);
        acts[0] = actuals.at8(i);
        double w = weights != null ? weights.atd(i) : 1;
        _mb.perRow(ds, acts, w, 0, null);
      }
    }
    @Override public void reduce(MultinomialMetrics mrt) { _mb.reduce(mrt._mb); }
  }

  public static class MetricBuilderMultinomial<T extends MetricBuilderMultinomial<T>> extends MetricBuilderSupervised<T> {
    double[/*nclasses*/][/*nclasses*/] _cm;
    double[/*K*/] _hits;            // the number of hits for hitratio, length: K
    int _K;               // TODO: Let user set K
    double _logloss;
    AUC2.AUCBuilder[][] _aucsMatrix;

    public MetricBuilderMultinomial( int nclasses, String[] domain ) {
      super(nclasses,domain);
      _cm = domain.length > ConfusionMatrix.maxClasses() ? null : new double[domain.length][domain.length];
      _K = Math.min(10,_nclasses);
      _hits = new double[_K];
      _aucsMatrix = new AUC2.AUCBuilder[domain.length][domain.length];
      for(int i=0; i < _aucsMatrix.length; i++){
        for (int j=0; j < _aucsMatrix[0].length; j++)
          if(i!=j) {
            _aucsMatrix[i][j] = new AUC2.AUCBuilder(AUC2.NBINS);
          }
      }
    }

    public transient double [] _priorDistribution;
    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution;
    @Override public double[] perRow(double ds[], float[] yact, Model m) { return perRow(ds, yact, 1, 0, m); }
    @Override public double[] perRow(double ds[], float[] yact, double w, double o, Model m) {
      if (_cm == null) return ds;
      if( Float .isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if(ArrayUtils.hasNaNs(ds)) return ds;
      if(w == 0 || Double.isNaN(w)) return ds;
      final int iact = (int)yact[0];
      _count++;
      _wcount += w;
      _wY += w*iact;
      _wYY += w*iact*iact;

      // Compute error
      double err = iact+1 < ds.length ? 1-ds[iact+1] : 1;  // Error: distance from predicting ycls as 1.0
      _sumsqe += w*err*err;        // Squared error
      assert !Double.isNaN(_sumsqe);

      assert iact < _cm.length : "iact = " + iact + "; _cm.length = " + _cm.length;
      assert (int)ds[0] < _cm.length :  "ds[0] = " + ds[0] + "; _cm.length = " + _cm.length;
      // Plain Olde Confusion Matrix
      _cm[iact][(int)ds[0]]++; // actual v. predicted

      // Compute hit ratio
      if( _K > 0 && iact < ds.length-1)
        updateHits(w,iact,ds,_hits,m != null?m._output._priorClassDist:_priorDistribution);

      // Compute log loss
      _logloss += w*MathUtils.logloss(err);
      
      // compute multinomial AUC
      calculateAucPerRow(ds, iact, w);
      return ds;                // Flow coding
    }
    
    private void calculateAucPerRow(double ds[], int iact, double w){
      for(int i=0; i<_nclasses; i++){
        if(i != iact) {
          if (iact >= _nclasses) {
            iact = _nclasses - 1;
          }
          _aucsMatrix[iact][i].perRow(ds[i + 1], 0, w);
          _aucsMatrix[i][iact].perRow(ds[iact + 1], 1, w);
        }
      }
    }

    @Override public void reduce( T mb ) {
      if (_cm == null) return;
      super.reduce(mb);
      assert mb._K == _K;
      ArrayUtils.add(_cm, mb._cm);
      _hits = ArrayUtils.add(_hits, mb._hits);
      _logloss += mb._logloss;
      for(int i=0; i<_aucsMatrix.length; i++){
        for (int j=0;j<_aucsMatrix[0].length; i++) {
          if(i!=j) {
            _aucsMatrix[i][j].reduce(mb._aucsMatrix[i][j]);
          }
        }
      }
    }

    @Override public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      double mse = Double.NaN;
      double logloss = Double.NaN;
      float[] hr = new float[_K];
      ConfusionMatrix cm = new ConfusionMatrix(_cm, _domain);
      double sigma = weightedSigma();
      PairwiseAUC[] aucsPairs = new PairwiseAUC[((_domain.length * _domain.length)-_domain.length)/2];
      int aucsIndex = 0;
      if (_wcount > 0) {
        if (_hits != null) {
          for (int i = 0; i < hr.length; i++) hr[i] = (float) (_hits[i] / _wcount);
          for (int i = 1; i < hr.length; i++) hr[i] += hr[i - 1];
        }
        mse = _sumsqe / _wcount;
        logloss = _logloss / _wcount;
        for (int i=0; i<_aucsMatrix.length-1; i++){
          for (int j=1; j<_aucsMatrix[0].length; j++){
            if(i != j) {
              aucsPairs[aucsIndex++] = new PairwiseAUC(new AUC2(_aucsMatrix[i][j]), new AUC2(_aucsMatrix[j][i]), i, j, _domain[i], _domain[j]);
            }
          }
        }
      } else {
        for (int i=0; i<_aucsMatrix.length-1; i++){
          for (int j=1; j<_aucsMatrix[0].length; j++){
            if(i != j) {
              aucsPairs[aucsIndex++] = new PairwiseAUC(new AUC2(), new AUC2(), i, j, _domain[i], _domain[j]);
            }
          }
        }
      }
      
      ModelMetricsMultinomial mm = new ModelMetricsMultinomial(m, f, _count, mse, _domain, sigma, cm,
                                                               hr, logloss, aucsPairs, _customMetric);
      if (m!=null) m.addModelMetrics(mm);
      return mm;
    }
  }
}
