package hex.tree;

import java.util.Arrays;
import hex.AUC;
import hex.ConfusionMatrix2;
import hex.SupervisedModel;
import hex.VarImp;
import water.*;

public abstract class SharedTreeModel<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends SupervisedModel<M,P,O> {

  public abstract static class SharedTreeParameters extends SupervisedModel.SupervisedParameters {
    /** Maximal number of supported levels in response. */
    static final int MAX_SUPPORTED_LEVELS = 1000;

    public int _ntrees=50; // Number of trees in the final model. Grid Search, comma sep values:50,100,150,200

    public int _max_depth = 5; // Maximum tree depth. Grid Search, comma sep values:5,7

    public int _min_rows = 10; // Fewest allowed observations in a leaf (in R called 'nodesize'). Grid Search, comma sep values

    public int _nbins = 20; // Build a histogram of this many bins, then split at the best point

    public boolean _importance = false; // compute variable importance

    public long _seed;          // Seed for psuedo-random redistribution

    // TRUE: Continue extending an existing checkpointed model
    // FALSE: Overwrite any prior model
    public boolean _checkpoint;
  }

  public abstract static class SharedTreeOutput extends SupervisedModel.SupervisedOutput {

    /** Initially predicted value (for zero trees) */
    public double _initialPrediction;

    /** Number of trees actually in the model (as opposed to requested) */
    public int _ntrees;

    /** More indepth tree stats */
    final TreeStats _treeStats;

    /** Trees get big, so store each one seperately in the DKV. */
    public Key[/*_ntrees*/][/*_nclass*/] _treeKeys;

    /** r2 metric on validation set: 1-(MSE(model) / MSE(mean)) */
    public double _r2;

    /** Train and test errors per-tree (scored).  Zero index is the no-tree
     *  error, guessing only the class distribution.  Not all trees are
     *  scored, NaN represents trees not scored. */
    public double _mse_train[/*_ntrees+1*/];
    public double _mse_test [/*_ntrees+1*/];

    /** Confusion Matrix for classification models, or null otherwise */
    public ConfusionMatrix2 _cm;

    /** AUC for binomial models, or null otherwise */
    public AUC _auc;

    /** Variable Importance, if asked for */
    public VarImp _varimp;

    public SharedTreeOutput( SharedTree b, double mse_train, double mse_test ) { 
      super(b);
      _ntrees = 0;              // No trees yet
      _treeKeys = new Key[_ntrees][]; // No tree keys yet
      _treeStats = new TreeStats();
      _mse_train = new double[]{mse_train};
      _mse_test  = Double.isNaN(mse_test) ? null : new double[]{mse_test };
    }

    // Append next set of K trees
    public void addKTrees( DTree[] trees, double mse_train, double mse_test ) {
      assert nclasses()==trees.length;
      _treeStats.updateBy(trees); // Update tree shape stats
      // Compress trees and record tree-keys
      _treeKeys = Arrays.copyOf(_treeKeys ,_ntrees+1);
      Key[] keys = _treeKeys[_ntrees] = new Key[trees.length];
      Futures fs = new Futures();
      for( int i=0; i<nclasses(); i++ ) if( trees[i] != null ) {
        CompressedTree ct = trees[i].compress(_ntrees,i);
        DKV.put(keys[i]=ct._key,ct,fs);
      }
      _ntrees++;
      // 1-based for errors; _mse_train[0] is for zero trees, not 1 tree
      _mse_train= Arrays.copyOf(_mse_train,_ntrees+1);
      _mse_train[_ntrees] = mse_train;
      if( _mse_test != null ) {
        _mse_test = Arrays.copyOf(_mse_test ,_ntrees+1);
        _mse_test [_ntrees] = mse_test ;
      }
      fs.blockForPending();
    }
  }

  public SharedTreeModel(Key selfKey, P parms, O output) { super(selfKey,parms,output); }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    // Prefetch trees into the local cache if it is necessary
    // Invoke scoring
    Arrays.fill(preds,0);
    for( int tidx=0; tidx<_output._treeKeys.length; tidx++ )
      score0(data, preds, tidx);
    return preds;
  }
  // Score per line per tree
  public void score0(double data[], float preds[], int treeIdx) {
    Key[] keys = _output._treeKeys[treeIdx];
    for( int c=0; c<keys.length; c++ )
      if( keys[c] != null )
        preds[keys.length==1?0:c+1] += DKV.get(keys[c]).<CompressedTree>get().score(data);
  }

  // Numeric type used in generated code to hold predicted value between the
  // calls; i.e. the numerical precision of predictions.
  static final String PRED_TYPE = "float";

  // TODO: once SpeeDRF inherits from SharedTreeModel, remove this and use a
  // v-call for the default compare function (is "<" vs "<=").
  boolean isFromSpeeDRF() { return false; }

  @Override protected Futures remove_impl( Futures fs ) {
    for( Key ks[] : _output._treeKeys)
      for( Key k : ks )
        if( k != null ) k.remove(fs);
    return super.remove_impl(fs);
  }
}

//  // --------------------------------------------------------------------------
//  public static abstract class TreeModel extends hex.Model {
//    @API(help="Expected max trees")                public final int N;
//    @API(help="MSE rate as trees are added")       public final double [] errs;
//    @API(help="Keys of actual trees built")        public final Key [/*N*/][/*nclass*/] treeKeys; // Always filled, but 2-binary classifiers can contain null for 2nd class
//    @API(help="Maximum tree depth")                public final int max_depth;
//    @API(help="Fewest allowed observations in a leaf") public final int min_rows;
//    @API(help="Bins in the histograms")            public final int nbins;
//
//    // For classification models, we'll do a Confusion Matrix right in the
//    // model (for now - really should be separate).
//    @API(help="Testing key for cm and errs")                                          public final Key testKey;
//    // Confusion matrix per each generated tree or null
//    @API(help="Confusion Matrix computed on training dataset, cm[actual][predicted]") public final ConfusionMatrix cms[/*CM-per-tree*/];
//    @API(help="Confusion matrix domain.")                                             public final String[]        cmDomain;
//    @API(help="Variable importance for individual input variables.")                  public final VarImp          varimp; // NOTE: in future we can have an array of different variable importance measures (per method)
//    @API(help="Tree statistics")                                                      public final TreeStats       treeStats;
//    @API(help="AUC for validation dataset")                                           public final AUCData         validAUC;
//    @API(help="Whether this is transformed from speedrf")                             public       boolean         isFromSpeeDRF=false;
//
//    private final int num_folds;
//    private transient volatile CompressedTree[/*N*/][/*nclasses OR 1 for regression*/] _treeBitsCache;
//
//    public TreeModel( Key key, Key dataKey, Key testKey, String names[], String domains[][], String[] cmDomain, int ntrees, int max_depth, int min_rows, int nbins, int num_folds, float[] priorClassDist, float[] classDist) {
//      this(key, dataKey, testKey, names, domains, cmDomain, ntrees, max_depth, min_rows, nbins, num_folds,
//          priorClassDist, classDist,
//          new Key[0][], new ConfusionMatrix[0], new double[0], null, null, null);
//    }
//    private TreeModel( Key key, Key dataKey, Key testKey, String names[], String domains[][], String[] cmDomain, int ntrees, int max_depth, int min_rows, int nbins, int num_folds,
//                      float[] priorClassDist, float[] classDist,
//                      Key[][] treeKeys, ConfusionMatrix[] cms, double[] errs, TreeStats treeStats, VarImp varimp, AUCData validAUC) {
//      super(key,dataKey,names,domains,priorClassDist, classDist);
//      this.N = ntrees;
//      this.max_depth = max_depth; this.min_rows = min_rows; this.nbins = nbins;
//      this.num_folds = num_folds;
//      this.treeKeys = treeKeys;
//      this.treeStats = treeStats;
//      this.cmDomain = cmDomain!=null ? cmDomain : new String[0];;
//      this.testKey = testKey;
//      this.cms = cms;
//      this.errs = errs;
//      this.varimp = varimp;
//      this.validAUC = validAUC;
//    }
//    // Simple copy ctor, null value of parameter means copy from prior-model
//    protected TreeModel(TreeModel prior, Key[][] treeKeys, double[] errs, ConfusionMatrix[] cms, TreeStats tstats, VarImp varimp, AUCData validAUC) {
//      super(prior._key,prior._dataKey,prior._names,prior._domains, prior._priorClassDist,prior._modelClassDist,prior.training_start_time,prior.training_duration_in_ms);
//      this.N = prior.N;
//      this.testKey   = prior.testKey;
//      this.max_depth = prior.max_depth;
//      this.min_rows  = prior.min_rows;
//      this.nbins     = prior.nbins;
//      this.cmDomain  = prior.cmDomain;
//      this.num_folds = prior.num_folds;
//
//      if (treeKeys != null) this.treeKeys  = treeKeys; else this.treeKeys  = prior.treeKeys;
//      if (errs     != null) this.errs      = errs;     else this.errs      = prior.errs;
//      if (cms      != null) this.cms       = cms;      else this.cms       = prior.cms;
//      if (tstats   != null) this.treeStats = tstats;   else this.treeStats = prior.treeStats;
//      if (varimp   != null) this.varimp    = varimp;   else this.varimp    = prior.varimp;
//      if (validAUC != null) this.validAUC  = validAUC; else this.validAUC  = prior.validAUC;
//    }
//    // Additional copy ctors to update specific fields
//    public TreeModel(TreeModel prior, DTree[] tree, double err, ConfusionMatrix cm, TreeStats tstats) {
//      this(prior, append(prior.treeKeys, tree), Utils.append(prior.errs, err), Utils.append(prior.cms, cm), tstats, null, null);
//    }
//    public TreeModel(TreeModel prior, DTree[] tree, TreeStats tstats) {
//      this(prior, append(prior.treeKeys, tree), null, null, tstats, null, null);
//    }
//    public TreeModel(TreeModel prior, double err, ConfusionMatrix cm, VarImp varimp, AUCData validAUC) {
//      this(prior, null, Utils.append(prior.errs, err), Utils.append(prior.cms, cm), null, varimp, validAUC);
//    }
//
//    public enum TreeModelType {
//      UNKNOWN,
//      GBM,
//      DRF,
//    }
//
//    protected TreeModelType getTreeModelType() { return TreeModelType.UNKNOWN; }
//
//    /** Returns Producer if the model is under construction else null.
//     * <p>The implementation looks for writer lock. If it is present, then returns true.</p>
//     *
//     * <p>WARNING: the method is strictly for UI used, does not provide any atomicity!!!</p>*/
//    private final Key getProducer() {
//      return FetchProducer.fetch(_key);
//    }
//    private final boolean isProduced() {
//      return getProducer()!=null;
//    }
//
//    private static final class FetchProducer extends DTask<FetchProducer> {
//      final private Key _key;
//      private Key _producer;
//      public static Key fetch(Key key) {
//        FetchProducer fp = new FetchProducer(key);
//        if (key.home()) fp.compute2();
//        else fp = RPC.call(key.home_node(), fp).get();
//        return fp._producer;
//      }
//      private FetchProducer(Key k) { _key = k; }
//      @Override public void compute2() {
//        Lockable l = UKV.get(_key);
//        _producer = l!=null && l._lockers!=null && l._lockers.length > 0 ? l._lockers[0] : null;
//        tryComplete();
//      }
//      @Override public byte priority() { return H2O.ATOMIC_PRIORITY; }
//    }
//
//    private static final Key[][] append(Key[][] prior, DTree[] tree ) {
//      if (tree==null) return prior;
//      prior = Arrays.copyOf(prior, prior.length+1);
//      Key ts[] = prior[prior.length-1] = new Key[tree.length];
//      for( int c=0; c<tree.length; c++ )
//        if( tree[c] != null ) {
//            ts[c] = tree[c].save();
//        }
//      return prior;
//    }
//
//    /** Number of trees in current model. */
//    public int ntrees() { return treeKeys.length; }
//    // Most recent ConfusionMatrix
//    @Override public ConfusionMatrix cm() {
//      ConfusionMatrix[] cms = this.cms; // Avoid race update; read it once
//      if(cms != null && cms.length > 0){
//        int n = cms.length-1;
//        while(n > 0 && cms[n] == null)--n;
//        return cms[n] == null?null:cms[n];
//      } else return null;
//    }
//
//    @Override public VarImp varimp() { return varimp; }
//    @Override public double mse() {
//      if(errs != null && errs.length > 0){
//        int n = errs.length-1;
//        while(n > 0 && Double.isNaN(errs[n]))--n;
//        return errs[n];
//      } else return Double.NaN;
//    }
//    @Override protected float[] score0(double data[], float preds[]) {
//      // Prefetch trees into the local cache if it is necessary
//      // Invoke scoring
//      Arrays.fill(preds,0);
//      for( int tidx=0; tidx<treeKeys.length; tidx++ )
//        score0(data, preds, tidx);
//      return preds;
//    }
//
//    /** Returns i-th tree represented by an array of k-trees. */
//    public final CompressedTree[] ctree(int tidx) {
//      if (_treeBitsCache==null) {
//        synchronized(this) {
//          if (_treeBitsCache==null) _treeBitsCache = new CompressedTree[ntrees()][];
//        }
//      }
//      if (_treeBitsCache[tidx]==null) {
//        synchronized(this) {
//          if (_treeBitsCache[tidx]==null) {
//            Key[] k = treeKeys[tidx];
//            CompressedTree[] ctree = new CompressedTree[nclasses()];
//            for (int i = 0; i < nclasses(); i++) // binary classifiers can contains null for second tree
//              if (k[i]!=null) ctree[i] = UKV.get(k[i]);
//            _treeBitsCache[tidx] = ctree;
//          }
//        }
//      }
//      return _treeBitsCache[tidx];
//    }
//    // Score per line per tree
//    public void score0(double data[], float preds[], int treeIdx) {
//      CompressedTree ts[] = ctree(treeIdx);
//      DTreeUtils.scoreTree(data, preds, ts);
//    }
//
//    /** Delete model trees */
//    public void delete_trees() {
//      Futures fs = new Futures();
//      delete_trees(fs);
//      fs.blockForPending();
//    }
//    public Futures delete_trees(Futures fs) {
//      for (int tid = 0; tid < treeKeys.length; tid++) /* over all trees */
//          for (int cid = 0; cid < treeKeys[tid].length; cid++) /* over all classes */
//            // 2-binary classifiers can contain null for the second
//            if (treeKeys[tid][cid]!=null) DKV.remove(treeKeys[tid][cid], fs);
//      return fs;
//    }
//
//    // If model is deleted then all trees has to be delete as well
//    @Override public Futures delete_impl(Futures fs) {
//      delete_trees(fs);
//      super.delete_impl(fs);
//      return fs;
//    }
//
//    @Override public ModelAutobufferSerializer getModelSerializer() {
//      // Return a serializer which knows how to serialize keys
//      return new ModelAutobufferSerializer() {
//        @Override protected AutoBuffer postSave(Model m, AutoBuffer ab) {
//          int ntrees = treeKeys.length;
//          ab.put4(ntrees);
//          for (int i=0; i<ntrees; i++) {
//            CompressedTree[] ts = ctree(i);
//            ab.putA(ts);
//          }
//          return ab;
//        }
//        @Override protected AutoBuffer postLoad(Model m, AutoBuffer ab) {
//          int ntrees = ab.get4();
//          Futures fs = new Futures();
//          for (int i=0; i<ntrees; i++) {
//            CompressedTree[] ts = ab.getA(CompressedTree.class);
//            for (int j=0; j<ts.length; j++) {
//              Key k = ((TreeModel) m).treeKeys[i][j];
//              assert k == null && ts[j] == null || k != null && ts[j] != null : "Incosistency in model serialization: key is null but model is not null, OR vice versa!";
//              if (k!=null) {
//                UKV.put(k, ts[j], fs);
//              }
//            }
//          }
//          fs.blockForPending();
//          return ab;
//        }
//      };
//    }
//
//    public void generateHTML(String title, StringBuilder sb) {
//      DocGen.HTML.title(sb,title);
//      sb.append("<div class=\"alert\">").append("Actions: ");
//      if (_dataKey != null)
//        sb.append(Inspect2.link("Inspect training data ("+_dataKey.toString()+")", _dataKey)).append(", ");
//      sb.append(Predict.link(_key,"Score on dataset")).append(", ");
//      if (_dataKey != null)
//        sb.append(UIUtils.builderModelLink(this.getClass(), _dataKey, responseName(), "Compute new model")).append(", ");
//      sb.append(UIUtils.qlink(SaveModel.class, "model", _key, "Save model")).append(", ");
//      if (isProduced()) { // looks at locker field and check W-locker guy
//        sb.append("<i class=\"icon-stop\"></i>&nbsp;").append(Cancel.link(getProducer(), "Stop training this model"));
//      } else {
//        sb.append("<i class=\"icon-play\"></i>&nbsp;").append(UIUtils.builderLink(this.getClass(), _dataKey, responseName(), this._key, "Continue training this model"));
//      }
//      sb.append("</div>");
//      DocGen.HTML.paragraph(sb,"Model Key: "+_key);
//      DocGen.HTML.paragraph(sb,"Max depth: "+max_depth+", Min rows: "+min_rows+", Nbins:"+nbins+", Trees: " + ntrees());
//      generateModelDescription(sb);
//      sb.append("</pre>");
//
//      String[] domain = cmDomain; // Domain of response col
//
//      // Generate a display using the last scored Model.  Not all models are
//      // scored immediately (since scoring can be a big part of model building).
//      ConfusionMatrix cm = null;
//      int last = cms.length-1;
//      while( last > 0 && cms[last]==null ) last--;
//      cm = 0 <= last && last < cms.length ? cms[last] : null;
//
//      // Display the CM
//      if( cm != null && domain != null ) {
//        // Top row of CM
//        assert cm._arr.length==domain.length;
//        DocGen.HTML.title(sb,"Scoring");
//        if( testKey == null ) {
//          if (_have_cv_results)
//            sb.append("<div class=\"alert\">Reported on ").append(num_folds).append("-fold cross-validated training data</div>");
//          else {
//            sb.append("<div class=\"alert\">Reported on ").append(title.contains("DRF") ? "out-of-bag" : "training").append(" data");
//            if (num_folds > 0) sb.append(" (cross-validation results are being computed - please reload this page later)");
//            sb.append(".");
//            if (_priorClassDist!=null && _modelClassDist!=null) sb.append("<br />Data were resampled to balance class distribution.");
//            sb.append("</div>");
//          }
//        } else {
//          RString rs = new RString("<div class=\"alert\">Reported on <a href='Inspect2.html?src_key=%$key'>%key</a></div>");
//          rs.replace("key", testKey);
//          DocGen.HTML.paragraph(sb,rs.toString());
//        }
//        if (validAUC == null) { //AUC shows the CM already
//          // generate HTML for CM
//          DocGen.HTML.section(sb, "Confusion Matrix");
//          cm.toHTML(sb, domain);
//        }
//      }
//
//      if( errs != null ) {
//        if (!isClassifier() && num_folds > 0) {
//          if (_have_cv_results)
//            DocGen.HTML.section(sb, num_folds + "-fold cross-validated Mean Squared Error: " + String.format("%5.3f", errs[errs.length-1]));
//          else
//            DocGen.HTML.section(sb, num_folds + "-fold cross-validated Mean Squared Error is being computed - please reload this page later.");
//        }
//        DocGen.HTML.section(sb,"Mean Squared Error by Tree");
//        DocGen.HTML.arrayHead(sb);
//        sb.append("<tr style='min-width:60px'><th>Trees</th>");
//        last = errs.length-1-(_have_cv_results?1:0); // for regressor reports all errors (except for cross-validated result)
//        for( int i=last; i>=0; i-- )
//          sb.append("<td style='min-width:60px'>").append(i).append("</td>");
//        sb.append("</tr>");
//        sb.append("<tr><th class='warning'>MSE</th>");
//        for( int i=last; i>=0; i-- )
//          sb.append(!Double.isNaN(errs[i]) ? String.format("<td style='min-width:60px'>%5.3f</td>",errs[i]) : "<td style='min-width:60px'>---</td>");
//        sb.append("</tr>");
//        DocGen.HTML.arrayTail(sb);
//      }
//      // Show AUC for binary classifiers
//      if (validAUC != null) generateHTMLAUC(sb);
//
//      // Show tree stats
//      if (treeStats != null) generateHTMLTreeStats(sb);
//
//      // Show variable importance
//      if (varimp != null) {
//        generateHTMLVarImp(sb);
//      }
//      printCrossValidationModelsHTML(sb);
//    }
//
//    static final String NA = "---";
//    protected void generateHTMLTreeStats(StringBuilder sb) {
//      DocGen.HTML.section(sb,"Tree stats");
//      DocGen.HTML.arrayHead(sb);
//      sb.append("<tr><th>&nbsp;</th>").append("<th>Min</th><th>Mean</th><th>Max</th></tr>");
//
//      boolean valid = treeStats.isValid();
//      sb.append("<tr><th>Depth</th>")
//            .append("<td>").append(valid ? treeStats.minDepth  : NA).append("</td>")
//            .append("<td>").append(valid ? treeStats.meanDepth : NA).append("</td>")
//            .append("<td>").append(valid ? treeStats.maxDepth  : NA).append("</td></tr>");
//      sb.append("<th>Leaves</th>")
//            .append("<td>").append(valid ? treeStats.minLeaves  : NA).append("</td>")
//            .append("<td>").append(valid ? treeStats.meanLeaves : NA).append("</td>")
//            .append("<td>").append(valid ? treeStats.maxLeaves  : NA).append("</td></tr>");
//      DocGen.HTML.arrayTail(sb);
//    }
//
//    protected void generateHTMLVarImp(StringBuilder sb) {
//      if (varimp!=null) {
//        // Set up variable names for importance
//        varimp.setVariables(Arrays.copyOf(_names, _names.length-1));
//        varimp.toHTML(this, sb);
//      }
//    }
//
//    protected void generateHTMLAUC(StringBuilder sb) {
//      validAUC.toHTML(sb);
//    }
//
//    StringBuilder toString(final String res, CompressedTree ct, final StringBuilder sb ) {
//      new TreeVisitor<RuntimeException>(this,ct) {
//        @Override protected void pre( int col, float fcmp, IcedBitSet gcmp, int equal ) {
//          for( int i=0; i<_depth; i++ ) sb.append("  ");
//          if(equal == 2 || equal == 3)
//            sb.append(_names[col]).append("==").append(gcmp.toString()).append('\n');
//          else
//            sb.append(_names[col]).append(equal==1?"==":"< ").append(fcmp).append('\n');
//        }
//        @Override protected void leaf( float pred ) {
//          for( int i=0; i<_depth; i++ ) sb.append("  ");
//          sb.append(res).append("=").append(pred).append(";\n");
//        }
//      }.visit();
//      return sb;
//    }
//
//    // For GBM: learn_rate.  For DRF: mtries, sample_rate, seed.
//    abstract protected void generateModelDescription(StringBuilder sb);
//
//    // Determine whether feature is licensed.
//    private boolean isFeatureAllowed() {
//      boolean featureAllowed = false;
//      try {
//        if (treeStats.numTrees <= 10) {
//          featureAllowed = true;
//        }
//        else {
//          if (getTreeModelType() == TreeModelType.GBM) {
//            featureAllowed = H2O.licenseManager.isFeatureAllowed(LicenseManager.FEATURE_GBM_SCORING);
//          }
//          else if (getTreeModelType() == TreeModelType.DRF) {
//            featureAllowed = H2O.licenseManager.isFeatureAllowed(LicenseManager.FEATURE_RF_SCORING);
//          }
//        }
//      }
//      catch (Exception xe) {}
//
//      return featureAllowed;
//    }
//
//    public void toJavaHtml( StringBuilder sb ) {
//      if( treeStats == null ) return; // No trees yet
//      sb.append("<br /><br /><div class=\"pull-right\"><a href=\"#\" onclick=\'$(\"#javaModel\").toggleClass(\"hide\");\'" +
//                "class=\'btn btn-inverse btn-mini\'>Java Model</a></div><br /><div class=\"hide\" id=\"javaModel\">");
//
//      boolean featureAllowed = isFeatureAllowed();
//      if (! featureAllowed) {
//        sb.append("<br/><div id=\'javaModelWarningBlock\' class=\"alert\" style=\"background:#eedd20;color:#636363;text-shadow:none;\">");
//        sb.append("<b>You have requested a premium feature (> 10 trees) and your H<sub>2</sub>O software is unlicensed.</b><br/><br/>");
//        sb.append("Please enter your email address below, and we will send you a trial license shortly.<br/>");
//        sb.append("This will also temporarily enable downloading Java models.<br/>");
//        sb.append("<form class=\'form-inline\'><input id=\"emailForJavaModel\" class=\"span5\" type=\"text\" placeholder=\"Email\"/> ");
//        sb.append("<a href=\"#\" onclick=\'processJavaModelLicense();\' class=\'btn btn-inverse\'>Send</a></form></div>");
//        sb.append("<div id=\"javaModelSource\" class=\"hide\">");
//      }
//      if( ntrees() * treeStats.meanLeaves > 5000 ) {
//        String modelName = JCodeGen.toJavaId(_key.toString());
//        sb.append("<pre style=\"overflow-y:scroll;\"><code class=\"language-java\">");
//        sb.append("/* Java code is too large to display, download it directly.\n");
//        sb.append("   To obtain the code please invoke in your terminal:\n");
//        sb.append("     curl http:/").append(H2O.SELF.toString()).append("/h2o-model.jar > h2o-model.jar\n");
//        sb.append("     curl http:/").append(H2O.SELF.toString()).append("/2/").append(this.getClass().getSimpleName()).append("View.java?_modelKey=").append(_key).append(" > ").append(modelName).append(".java\n");
//        sb.append("     javac -cp h2o-model.jar -J-Xmx2g -J-XX:MaxPermSize=128m ").append(modelName).append(".java\n");
//        if (GEN_BENCHMARK_CODE)
//          sb.append("     java -cp h2o-model.jar:. -Xmx2g -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m ").append(modelName).append('\n');
//        sb.append("*/");
//        sb.append("</code></pre>");
//      } else {
//        sb.append("<pre style=\"overflow-y:scroll;\"><code class=\"language-java\">");
//        DocGen.HTML.escape(sb, toJava());
//        sb.append("</code></pre>");
//      }
//      if (!featureAllowed) sb.append("</div>"); // close license blog
//      sb.append("</div>");
//      sb.append("<script type=\"text/javascript\">$(document).ready(showOrHideJavaModel);</script>");
//    }
//
//    @Override protected SB toJavaInit(SB sb, SB fileContextSB) {
//      sb = super.toJavaInit(sb, fileContextSB);
//
//      String modelName = JCodeGen.toJavaId(_key.toString());
//
//      // Generate main method with benchmark
//      if (GEN_BENCHMARK_CODE) {
//        sb.i().p("/**").nl();
//        sb.i().p(" * Sample program harness providing an example of how to call predict().").nl();
//        sb.i().p(" */").nl();
//        sb.i().p("public static void main(String[] args) throws Exception {").nl();
//        sb.i(1).p("int iters = args.length > 0 ? Integer.valueOf(args[0]) : DEFAULT_ITERATIONS;").nl();
//        sb.i(1).p(modelName).p(" model = new ").p(modelName).p("();").nl();
//        sb.i(1).p("model.bench(iters, DataSample.DATA, new float[NCLASSES+1], NTREES);").nl();
//        sb.i().p("}").nl();
//        sb.di(1);
//        sb.p(TO_JAVA_BENCH_FUNC);
//      }
//
//      JCodeGen.toStaticVar(sb, "NTREES", ntrees(), "Number of trees in this model.");
//      JCodeGen.toStaticVar(sb, "NTREES_INTERNAL", ntrees()*nclasses(), "Number of internal trees in this model (= NTREES*NCLASSES).");
//      if (GEN_BENCHMARK_CODE) JCodeGen.toStaticVar(sb, "DEFAULT_ITERATIONS", 10000, "Default number of iterations.");
//      // Generate a data in separated class since we do not want to influence size of constant pool of model class
//      if (GEN_BENCHMARK_CODE) {
//        if( _dataKey != null ) {
//          Value dataval = DKV.get(_dataKey);
//          if (dataval != null) {
//            water.fvec.Frame frdata = dataval.get();
//            water.fvec.Frame frsub = frdata.subframe(_names);
//            JCodeGen.toClass(fileContextSB, "// Sample of data used by benchmark\nclass DataSample", "DATA", frsub, 10, "Sample test data.");
//          }
//        }
//      }
//      return sb;
//    }
//    // Convert Tree model to Java
//    @Override protected void toJavaPredictBody( final SB bodySb, final SB classCtxSb, final SB fileCtxSb) {
//      // AD-HOC maximal number of trees in forest - in fact constant pool size for Forest class (all UTF String + references to static classes).
//      // TODO: in future this parameter can be a parameter for generator, as well as maxIters
//      final int maxfsize = 4000;
//      int fidx = 0; // forest index
//      int treesInForest = 0;
//      SB forest = new SB();
//      // divide trees into small forests per 100 trees
//      /* DEBUG line */ bodySb.i().p("// System.err.println(\"Row (gencode.predict): \" + java.util.Arrays.toString(data));").nl();
//      bodySb.i().p("java.util.Arrays.fill(preds,0f);").nl();
//      if (isFromSpeeDRF) {
//        bodySb.i().p("// Call forest predicting class ").p(0).nl();
//        bodySb.i().p("preds").p(" =").p(" Forest_").p(fidx).p("_class_").p(0).p(".predict(data, maxIters - " + fidx * maxfsize + ");").nl();
//      }
//      for( int c=0; c<nclasses(); c++ ) {
//        toJavaForestBegin(bodySb, forest, c, fidx++, maxfsize);
//        for( int i=0; i < treeKeys.length; i++ ) {
//          CompressedTree cts[] = ctree(i);
//          if( cts[c] == null ) continue;
//          if (!isFromSpeeDRF) {
//            forest.i().p("if (iters-- > 0) pred").p(" +=").p(" Tree_").p(i).p("_class_").p(c).p(".predict(data);").nl();
//          } else {
//            forest.i().p("pred[(int)").p(" Tree_").p(i).p("_class_").p(c).p(".predict(data) + 1] += 1;").nl();
//          }
//          // append representation of tree predictor
//          toJavaTreePredictFct(fileCtxSb, cts[c], i, c);
//          if (++treesInForest == maxfsize) {
//            toJavaForestEnd(bodySb, forest, c, fidx);
//            toJavaForestBegin(bodySb, forest, c, fidx++, maxfsize);
//            treesInForest = 0;
//          }
//        }
//        toJavaForestEnd(bodySb, forest, c, fidx);
//        treesInForest = 0;
//        fidx = 0;
//      }
//      fileCtxSb.p(forest);
//      toJavaUnifyPreds(bodySb);
//      toJavaFillPreds0(bodySb);
//    }
//
//    private void toJavaForestBegin(SB predictBody, SB forest, int c, int fidx, int maxTreesInForest) {
//      // ugly hack here
//      if (!isFromSpeeDRF) {
//        predictBody.i().p("// Call forest predicting class ").p(c).nl();
//        predictBody.i().p("preds[").p(c + 1).p("] +=").p(" Forest_").p(fidx).p("_class_").p(c).p(".predict(data, maxIters - " + fidx * maxTreesInForest + ");").nl();
//        forest.i().p("// Forest representing a subset of trees scoring class ").p(c).nl();
//        forest.i().p("class Forest_").p(fidx).p("_class_").p(c).p(" {").nl().ii(1);
//        forest.i().p("public static ").p(PRED_TYPE).p(" predict(double[] data, int maxIters) {").nl().ii(1);
//        forest.i().p(PRED_TYPE).p(" pred  = 0;").nl();
//        forest.i().p("int   iters = maxIters;").nl();
//      } else {
//        forest.i().p("// Forest representing a subset of trees scoring class ").p(c).nl();
//        forest.i().p("class Forest_").p(fidx).p("_class_").p(c).p(" {").nl().ii(1);
//        forest.i().p("public static ").p(PRED_TYPE).p("[] predict(double[] data, int maxIters) {").nl().ii(1);
//        forest.i().p(PRED_TYPE).p("[] pred = new float["+(nclasses()+1)+"];").nl();
//        forest.i().p("java.util.Arrays.fill(pred,0f);").nl();
//        forest.i().p("int   iters = maxIters;").nl();
//      }
//    }
//    private void toJavaForestEnd(SB predictBody, SB forest, int c, int fidx) {
//      if (!isFromSpeeDRF) {
//        forest.i().p("return pred;").nl();
//        forest.i().p("}").di(1).nl(); // end of function
//        forest.i().p("}").di(1).nl(); // end of forest classs
//      } else {
//        if (c ==0) {
//          forest.i().p("float sum = 0;").nl();
//          forest.i().p("for (int i=1; i <= " + nclasses() + "; i++) {").p("sum += pred[i];").p("}").nl();
//          forest.i().p("for (int i=1; i <= " + nclasses() + "; i++) {").p("pred[i] /= sum;").p("}").nl();
//        }
//        forest.i().p("return pred;").nl();
//        forest.i().p("}").di(1).nl(); // end of function
//        forest.i().p("}").di(1).nl(); // end of forest classs
//      }
//    }
//
//    // Produce prediction code for one tree
//    protected void toJavaTreePredictFct(final SB sb, final CompressedTree cts, int treeIdx, int classIdx) {
//      // generate top-level class definition
//      sb.nl();
//      sb.i().p("// Tree predictor for ").p(treeIdx).p("-tree and ").p(classIdx).p("-class").nl();
//      sb.i().p("class Tree_").p(treeIdx).p("_class_").p(classIdx).p(" {").nl().ii(1);
//      new TreeJCodeGen(this,cts, sb).generate();
//      sb.i().p("}").nl(); // close the class
//    }
//
//    @Override protected String toJavaDefaultMaxIters() { return String.valueOf(this.N);  }
//  }
//
  //public Random rngForChunk( int cidx ) {
  //  Random rand = createRNG(_seed);
  //  // Argh - needs polishment
  //  for( int i=0; i<cidx; i++ ) rand.nextLong();
  //  long seed = rand.nextLong();
  //  return createRNG(seed);
  //}
