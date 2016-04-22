package hex.aggregator;

import hex.DataInfo;
import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.pca.PCAModel;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;

public class AggregatorModel extends Model<AggregatorModel,AggregatorModel.AggregatorParameters,AggregatorModel.AggregatorOutput> implements Model.ExemplarMembers {

  public static class AggregatorParameters extends Model.Parameters {
    public String algoName() { return "Aggregator"; }
    public String fullName() { return "Aggregator"; }
    public String javaName() { return AggregatorModel.class.getName(); }
    @Override public long progressUnits() { return 3 + (_keep_member_indices ? 1 : 0); }

    public double _radius_scale=1.0;
    public boolean _keep_member_indices;
    public DataInfo.TransformType _transform = DataInfo.TransformType.NORMALIZE; // Data transformation
    public PCAModel.PCAParameters.Method _pca_method = PCAModel.PCAParameters.Method.Power;   // Method for dimensionality reduction
    public int _k = 1;                     // Number of principal components
    public int _max_iterations = 1000;     // Max iterations for SVD
    public long _seed = System.nanoTime(); // RNG seed
    public boolean _use_all_factor_levels = false;   // When expanding categoricals, should first level be kept or dropped?
  }

  public static class AggregatorOutput extends Model.Output {
    public AggregatorOutput(Aggregator b) { super(b); }
    @Override public int nfeatures() { return _output_frame.get().numCols()-1/*counts*/; }
    @Override public ModelCategory getModelCategory() { return ModelCategory.Clustering; }

    public Key<Frame> _output_frame;
  }


  public Aggregator.Exemplar[] _exemplars;
  public long[] _counts;
  public Key _exemplar_assignment_vec_key;
  public Key _diKey;

  public AggregatorModel(Key selfKey, AggregatorParameters parms, AggregatorOutput output) { super(selfKey,parms,output); }

  @Override
  protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, final Job j) {
    return null;
  }

  @Override
  protected Futures remove_impl(Futures fs) {
    _diKey.remove();
    _exemplar_assignment_vec_key.remove();
    return super.remove_impl(fs);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return null;
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return preds;
  }

  public static Frame createFrameFromRawValues(Key<Frame> outputFrameKey, String[] names, Aggregator.Exemplar[] ex, long[] counts) {
    int nrows = ex.length;
    Vec[] vecs = new Vec[ex[0].data.length+(counts==null?0:1)];
    int ncol = vecs.length;
    for (int c=0; c<ncol; ++c) {
      vecs[c] = Vec.makeZero(nrows);
      Vec.Writer vw = vecs[c].open();
      if (c==ncol-1 && counts!=null) {
        for (int r = 0; r < nrows; ++r)
          vw.set(r, counts[r]);
      } else {
        for (int r = 0; r < nrows; ++r)
          vw.set(r, ex[r].data[c]);
      }
      vw.close();
    }
    if (counts!=null) {
      names = Arrays.copyOf(names, names.length + 1);
      names[names.length - 1] = "counts";
    }

    Frame f = new Frame(outputFrameKey, names, vecs); //all numeric
    DKV.put(f);
    return f;
  }

  // Input: last column is and integer
  static private class RowSelect extends MRTask<RowSelect> {
    final long _exGID;
    final Key _diKey;
    DataInfo di; //shared per node
    public RowSelect(long exemplarGID, Key diKey) {
      _exGID = exemplarGID;
      _diKey = diKey;
    }

    @Override
    protected void setupLocal() {
      di = DKV.getGet(_diKey);
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
      assert(cs.length==nc.length+1); //1 extra col in input for exemplar assignment
      DataInfo.Row row = di.newDenseRow();
      Chunk assignment = cs[cs.length-1];
      Chunk[] chks = Arrays.copyOf(cs, cs.length-1);
      for (int i=0;i<cs[0]._len;++i) {
        if (assignment.at8(i)==_exGID) {
          row = di.extractDenseRow(chks, i, row);
          assert(row.numVals.length == nc.length);
          for (int c=0; c<nc.length; ++c) {
            nc[c].addNum(row.numVals[c]);
          }
        }
      }
    }
  }

  @Override
  public Frame scoreExemplarMembers(Key destination_key, int exemplarIdx) {
    DataInfo di = DKV.getGet(_diKey);
    Vec[] vecs = Arrays.copyOf(di._adaptedFrame.vecs(), di._adaptedFrame.numCols()+1);
    vecs[vecs.length-1] = (Vec)_exemplar_assignment_vec_key.get();
    return new RowSelect(_exemplars[exemplarIdx].gid, _diKey).doAll(_output.nfeatures(), Vec.T_NUM, new Frame(vecs)).outputFrame(destination_key, di._adaptedFrame.names(), null);
  }
}
