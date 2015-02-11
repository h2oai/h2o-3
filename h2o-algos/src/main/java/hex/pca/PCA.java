package hex.pca;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import hex.DataInfo;
import hex.Model;
import hex.ModelBuilder;
import hex.gram.Gram.GramTask;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.PCAV2;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.ArrayList;


/**
 * Principal Components Analysis
 * This is an algorithm for dimensionality reduction of numerical data.
 * <a href = "http://en.wikipedia.org/wiki/Principal_component_analysis">PCA on Wikipedia</a>
 * @author anqi_fu
 *
 */
public class PCA extends ModelBuilder<PCAModel,PCAModel.PCAParameters,PCAModel.PCAOutput> {
  static final int MAX_COL = 5000;

  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{
      Model.ModelCategory.Clustering
    };
  }

  // Called from an http request
  public PCA(PCAModel.PCAParameters parms ) { super("PCA",parms); init(false); }

  public ModelBuilderSchema schema() { return new PCAV2(); }

  @Override
  public Job<PCAModel> trainModel() {
    return start(new PCADriver(), 0);
  }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    //TODO sanity check all input parameters

    //int num_ecols = selectFrame(_train).numExpCols();
    //Log.info("Running PCA on dataset with " + num_ecols + " expanded columns in Gram matrix");
    //if(num_ecols > MAX_COL)
    //  throw new IllegalArgumentException("Cannot process more than " + MAX_COL + " columns, taking into account expanded categoricals");
  }

  private class PCADriver extends H2O.H2OCountedCompleter<PCADriver> {
    @Override protected void compute2() {
      PCAModel model = null;
      DataInfo dinfo = null;
      try {
        _parms.read_lock_frames(PCA.this); // Fetch & read-lock input frames
        init(true);

        // The model to be built
        model = new PCAModel(dest(), _parms, new PCAModel.PCAOutput(PCA.this));
        //Key dataKey = input("source") == null ? null : Key.make(input("source"));
        //Model(selfKey, dataKey, dinfo._adaptedFrame, /* priorClassDistribution */ null);
        model.delete_and_lock(_key);

        Frame fr = _train;  //TODO should this be a copy of the frame?
        Vec[] vecs = fr.vecs();

        // Remove constant cols and cols with too many NAs
        ArrayList<Integer> removeCols = new ArrayList<>();
        for(int i = 0; i < vecs.length; i++) {
          if(vecs[i].min() == vecs[i].max() || vecs[i].naCnt() > vecs[i].length()*0.2)
            // if(vecs[i].min() == vecs[i].max() || vecs[i].naCnt() > vecs[i].length()*0.2 || vecs[i].domain() != null)
            removeCols.add(i);
        }
        if(!removeCols.isEmpty()) {
          int[] cols = new int[removeCols.size()];
          for(int i = 0; i < cols.length; i++)
            cols[i] = removeCols.get(i);
          fr.remove(cols);
        }
        if( fr.numCols() < 2 )
          throw new IllegalArgumentException("Need more than one column to run PCA");

        dinfo = new DataInfo(Key.make(), fr, null, 0, false, _parms._standardized ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        DKV.put(dinfo._key,dinfo);
        model._output._catOffsets = dinfo._catOffsets;
        model._output._normSub = dinfo._normSub;
        model._output._normMul = dinfo._normMul;
        GramTask tsk = new GramTask(_key, dinfo).doAll(dinfo._adaptedFrame);
        // TODO: Need to ensure this maps correctly to scored data cols
        Matrix myGram = new Matrix(tsk._gram.getXX());   // X'X/n where n = num rows
        SingularValueDecomposition mySVD = myGram.svd();

        // Extract eigenvalues and eigenvectors
        // Note: Singular values ordered in weakly descending order by algorithm
        double[] Sval = mySVD.getSingularValues();
        model._output._eigVec = mySVD.getV().getArray();  // rows = features, cols = principal components
        assert Sval.length == model._output._eigVec.length;
        model._output._rank = mySVD.rank();
        // DKV.put(EigenvectorMatrix.makeKey(input("source"), destination_key), new EigenvectorMatrix(eigVec));

        // Compute standard deviation
        double[] sdev = new double[Sval.length];
        double totVar = 0;
        double dfcorr = dinfo._adaptedFrame.numRows()/(dinfo._adaptedFrame.numRows() - 1.0);
        for(int i = 0; i < Sval.length; i++) {
          // if(standardize)
          Sval[i] = dfcorr*Sval[i];   // Correct since degrees of freedom = n-1
          sdev[i] = Math.sqrt(Sval[i]);
          totVar += Sval[i];
        }
        model._output._sdev = sdev;
        model._output._namesExp = namesExp(sdev.length);
        model._output._numPC = Math.min(getNumPC(sdev, _parms._tolerance), _parms._max_pc);

        double[] propVar = new double[Sval.length];    // Proportion of total variance
        double[] cumVar = new double[Sval.length];    // Cumulative proportion of total variance
        for(int i = 0; i < Sval.length; i++) {
          propVar[i] = Sval[i]/totVar;
          cumVar[i] = i == 0 ? propVar[0] : cumVar[i-1] + propVar[i];
        }
        model._output._propVar = propVar;
        model._output._cumVar = cumVar;
        done();                 // Job done!
      } catch( Throwable t ) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        if( model != null ) model.unlock(_key);
        DKV.remove(dinfo._key);
        _parms.read_unlock_frames(PCA.this);
      }
      tryComplete();
    }

    public String[] namesExp(int sdevLen){
      final int n = _train._names.length;
      final String[][] domains = _train.domains();
      int[] nums = MemoryManager.malloc4(n);
      int[] cats = MemoryManager.malloc4(n);

      // Store indices of numeric and categorical cols
      int nnums = 0, ncats = 0;
      for(int i = 0; i < n; ++i){
        if(domains[i] != null)
          cats[ncats++] = i;
        else
          nums[nnums++] = i;
      }

      // Sort the categoricals in decreasing order according to size
      for(int i = 0; i < ncats; ++i)
        for(int j = i+1; j < ncats; ++j)
          if(domains[cats[i]].length < domains[cats[j]].length) {
            int x = cats[i];
            cats[i] = cats[j];
            cats[j] = x;
          }

      // Construct expanded col names, with categoricals first followed by numerics
      int k = 0;
      String[] names = new String[sdevLen];
      for(int i = 0; i < ncats; ++i){
        for(int j = 1; j < domains[cats[i]].length; ++j)
          names[k++] = _train._names[cats[i]] + "." + domains[cats[i]][j];
      }
      for(int i = 0; i < nnums; ++i) {
        names[k++] = _train._names[nums[i]];
      }
      return names;
    }

    public int getNumPC(double[] sdev, double tol) {
      if(sdev == null) return 0;
      double cutoff = tol*sdev[0];
      for( int i=0; i<sdev.length; i++ )
        if( sdev[i] < cutoff )
          return i;
      return sdev.length;
    }
  }
}
