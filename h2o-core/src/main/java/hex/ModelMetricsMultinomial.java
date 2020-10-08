package hex;

import hex.genmodel.GenModel;
import water.MRTask;
import water.Scope;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.util.TwoDimTable;

import java.util.Arrays;

public class ModelMetricsMultinomial extends ModelMetricsSupervised {
  public final float[] _hit_ratios;         // Hit ratios
  public final ConfusionMatrix _cm;
  public final double _logloss;
  public double _mean_per_class_error;
  public final PairwiseAUC[] _ovo_aucs;
  public final AUC2[] _ovr_aucs;
  public final MultinomialAucType _default_auc_type;
  
  public ModelMetricsMultinomial(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma, ConfusionMatrix cm, float[] hr, double logloss, AUC2[] ovrAucs, PairwiseAUC[] ovoAucs, CustomMetric customMetric) {
    super(model, frame, nobs, mse, domain, sigma, customMetric);
    _cm = cm;
    _hit_ratios = hr;
    _logloss = logloss;
    _mean_per_class_error = cm==null || cm.tooLarge() ? Double.NaN : cm.mean_per_class_error();
    _ovr_aucs = ovrAucs;
    _ovo_aucs = ovoAucs;
    _default_auc_type = model._parms._multinomial_auc_type;
    
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" logloss: " + (float)_logloss + "\n");
    sb.append(" mean_per_class_error: " + (float)_mean_per_class_error + "\n");
    sb.append(" hit ratios: " + Arrays.toString(_hit_ratios) + "\n");   
    for(int i = 0; i< _ovr_aucs.length; i++) {
      sb.append(" "+_domain[i]+" vs. Rest AUC "+_ovr_aucs[i]._auc);
    }
    for(int i = 0; i< _ovr_aucs.length; i++) {
      sb.append(" "+_domain[i]+" vs. Rest PR AUC "+_ovr_aucs[i]._pr_auc);
    }
    for(int i = 0; i< _ovo_aucs.length; i++){
      sb.append(" One vs. One "+ _ovo_aucs[i].getAucString()+ "\n");
    }
    for(int i = 0; i< _ovo_aucs.length; i++){
      sb.append(" One vs. One "+ _ovo_aucs[i].getPrAucString()+ "\n");
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
  
  public double auc() {
    switch (_default_auc_type) {
      case MACRO_OVR:
        return getOvrMacroAuc(false);
      case MACRO_OVO:
        return getOvoMacroAuc(false);
      case WEIGHTED_OVO:
        return getOvoWeightedAuc(false);
      default:
        return getOvrWeightedAuc(false);
    }
  }

  public double pr_auc() {
    switch (_default_auc_type) {
      case MACRO_OVR:
        return getOvrMacroAuc(true);
      case MACRO_OVO:
        return getOvoMacroAuc(true);
      case WEIGHTED_OVO:
        return getOvoWeightedAuc(true);
      default:
        return getOvrWeightedAuc(true);
    }
  }
  
  public double getOvrMacroAuc(boolean isPr){
    double macroAuc = 0;
    for(AUC2 ovrAuc : _ovr_aucs){
      macroAuc += isPr ? ovrAuc._pr_auc : ovrAuc._auc;
    }
    return macroAuc/_ovr_aucs.length;
  }

  public double getOvrWeightedAuc(boolean isPr){
    double weightedAuc = 0;
    double sumWeights = 0;
    for(AUC2 ovrAuc : _ovr_aucs){
      int maxIndex = ovrAuc._max_idx;
      double tp = 0;
      if(maxIndex != -1){
        tp = ovrAuc.tp(maxIndex);
      }
      sumWeights += tp;
      weightedAuc += isPr ? ovrAuc._pr_auc * tp : ovrAuc._auc * tp;
    }
    return weightedAuc/sumWeights;
  }
  
  public double getOvoMacroAuc(boolean isPr){
    double macroAuc = 0;
    for(PairwiseAUC ovoAuc : _ovo_aucs){
      macroAuc += isPr ? ovoAuc.getPrAuc() : ovoAuc.getAuc();
    }
    return macroAuc/_ovo_aucs.length;
  }

  public double getOvoWeightedAuc(boolean isPr){
    double weightedAuc = 0;
    double sumWeights = 0;
    for(PairwiseAUC ovoAuc : _ovo_aucs){
      weightedAuc += isPr ? ovoAuc.getWeightedPrAuc() : ovoAuc.getWeightedAuc();
      sumWeights += ovoAuc.getSumTp();
    }
    return weightedAuc/sumWeights;
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
  
  

  public static TwoDimTable getAucsTable(AUC2[] ovrAucs, PairwiseAUC[] ovoAucs, String[] domains, boolean isPr) {
    String metric = isPr ? "PR AUC" : "AUC";
    String tableHeader = "Multinomial "+metric+" values";
    int rows = ovrAucs.length + ovoAucs.length + 4 /*2 + 2 weighted aucs*/;
    String[] rowHeaders = new String[rows];
    for(int i = 0; i < ovrAucs.length; i++)
      rowHeaders[i] = domains[i]+" vs Rest";
    rowHeaders[ovrAucs.length] = "Macro OVR";
    rowHeaders[ovrAucs.length + 1] = "Weighted OVR";
    for(int i = 0; i < ovoAucs.length; i++)
      rowHeaders[ovrAucs.length+2+i] = ovoAucs[i].getPairwiseDomainsString();
    rowHeaders[rows - 2] = "Macro OVR";
    rowHeaders[rows - 1] = "Weighted OVR";
    String[] colHeaders = new String[]{"First class domain", "Second class domain", metric};
    String[] colTypes = new String[]{"String", "String", "double"};
    String[] colFormats = new String[]{"%s", "%s", "%d"};
    String colHeaderForRowHeaders = "Type";
    TwoDimTable table = new TwoDimTable(tableHeader, null, rowHeaders, colHeaders, colTypes, colFormats, colHeaderForRowHeaders);
    double macroOvr = 0, weightedOvr = 0, macroOvo = 0, weightedOvo = 0;
    double sumWeights = 0;
    for(int i = 0; i < ovrAucs.length; i++){
      AUC2 auc = ovrAucs[i];
      double aucValue = isPr ? auc._pr_auc : auc._auc;
      table.set(i, 0, domains[i]);
      table.set(i, 2, aucValue);
      macroOvr += aucValue;
      double weight = auc._max_idx != -1 ? auc.tp(auc._max_idx) : 0;
      weightedOvr += aucValue * weight;
      sumWeights += weight;
    }
    table.set(ovrAucs.length, 2, macroOvr/ovrAucs.length);
    table.set(ovrAucs.length + 1, 2, weightedOvr/sumWeights);
    sumWeights = 0;
    for(int i = 0; i < ovoAucs.length; i++) {
      PairwiseAUC auc = ovoAucs[i];
      double aucValue = isPr ? auc.getPrAuc() : auc.getAuc();
      table.set(ovrAucs.length+2+i, 0, auc.getDomainFirst());
      table.set(ovrAucs.length+2+i, 1, auc.getDomainSecond());
      table.set(ovrAucs.length+2+i, 2, aucValue);
      double weight = auc.getSumTp();
      macroOvo += aucValue;
      weightedOvo += isPr ? auc.getWeightedPrAuc() : auc.getWeightedAuc();
      sumWeights += weight;
    }
    table.set(rows-2, 2, macroOvo/ovoAucs.length);
    table.set(rows-1, 2, weightedOvo/sumWeights);
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
    AUC2.AUCBuilder[][] _ovoAucs;
    AUC2.AUCBuilder[] _ovrAucs;

    public MetricBuilderMultinomial( int nclasses, String[] domain ) {
      super(nclasses,domain);
      int domainLength = domain.length;
      _cm = domain.length > ConfusionMatrix.maxClasses() ? null : new double[domainLength][domainLength];
      _K = Math.min(10,_nclasses);
      _hits = new double[_K];
      // matrix for pairwise AUCs
      _ovoAucs = new AUC2.AUCBuilder[domainLength][domainLength];
      _ovrAucs = new AUC2.AUCBuilder[domainLength];
      for(int i = 0; i < domainLength; i++){
        _ovrAucs[i] = new AUC2.AUCBuilder(AUC2.NBINS);
        for(int j = 0; j < domainLength; j++)
          // diagonal is not used
          if(i != j) {
            _ovoAucs[i][j] = new AUC2.AUCBuilder(AUC2.NBINS);
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
      
      // compute multinomial pairwise AUCs
      calculateAucsPerRow(ds, iact, w);
      return ds;                // Flow coding
    }
    
    private void calculateAucsPerRow(double ds[], int iact, double w){
      if (iact >= _domain.length) {
        iact = _domain.length - 1;
      }
      for(int i = 0; i < _domain.length; i++){
        // diagonal is empty
        if(i != iact) {
          _ovoAucs[iact][i].perRow(ds[i + 1], 0, w);
          _ovoAucs[i][iact].perRow(ds[iact + 1], 1, w);
          _ovrAucs[i].perRow(ds[i + 1], 0, w);
        } else {
          _ovrAucs[iact].perRow(ds[iact + 1], 1, w);
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
      for(int i = 0; i < _ovoAucs.length; i++){
        for (int j = 0; j < _ovoAucs[0].length; j++) {
          if(i != j) {
            _ovoAucs[i][j].reduce(mb._ovoAucs[i][j]);
          }
        }
      }
    }

    @Override public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      double mse = Double.NaN;
      double logloss = Double.NaN;
      float[] hr = new float[_K];
      ConfusionMatrix cm = new ConfusionMatrix(_cm, _domain);
      int domainLength = _domain.length;
      double sigma = weightedSigma();
      PairwiseAUC[] ovoAucs = new PairwiseAUC[(domainLength * domainLength - domainLength)/2];
      AUC2[] ovrAucs = new AUC2[domainLength];
      int aucsIndex = 0;
      if (_wcount > 0) {
        if (_hits != null) {
          for (int i = 0; i < hr.length; i++) hr[i] = (float) (_hits[i] / _wcount);
          for (int i = 1; i < hr.length; i++) hr[i] += hr[i - 1];
        }
        mse = _sumsqe / _wcount;
        logloss = _logloss / _wcount;
        
        for (int i = 0; i < domainLength-1; i++){
          ovrAucs[i] = _ovrAucs[i]._n > 0 ? new AUC2(_ovrAucs[i]) : new AUC2();
          for (int j = i+1; j < domainLength; j++){
            AUC2 first = _ovoAucs[i][j]._n > 0 ? new AUC2(_ovoAucs[i][j]) : new AUC2();
            AUC2 second = _ovoAucs[j][i]._n > 0 ? new AUC2(_ovoAucs[j][i]) : new AUC2();
            ovoAucs[aucsIndex++] = new PairwiseAUC(first, second, _domain[i], _domain[j]);
          }
        }
        ovrAucs[domainLength-1] = _ovrAucs[domainLength-1]._n > 0 ? new AUC2(_ovrAucs[domainLength-1]) : new AUC2();
      } else {
        for (int i = 0; i < _ovoAucs.length-1; i++){
          ovrAucs[i] =  new AUC2();
          for (int j = i+1; j< _ovoAucs[0].length; j++){
            if(i < j) {
              ovoAucs[aucsIndex++] = new PairwiseAUC(new AUC2(), new AUC2(), _domain[i], _domain[j]);
            }
          }
        }
        ovrAucs[domainLength-1] = new AUC2();
      }
      
      ModelMetricsMultinomial mm = new ModelMetricsMultinomial(m, f, _count, mse, _domain, sigma, cm,
                                                               hr, logloss, ovrAucs, ovoAucs, _customMetric);
      if (m!=null) m.addModelMetrics(mm);
      return mm;
    }
  }
}
