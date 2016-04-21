package hex.aggregator;

import hex.DataInfo;
import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.pca.PCAModel;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

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

  public double[][] _exemplars;
  public long[] _counts;
  public long[][] _member_indices;
  public Key _diKey;

  public AggregatorModel(Key selfKey, AggregatorParameters parms, AggregatorOutput output) { super(selfKey,parms,output); }

  @Override
  protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, final Job j) {
    return null;
  }

  @Override
  protected Futures remove_impl(Futures fs) {
    _diKey.remove();
    return super.remove_impl(fs);
  }

  public Frame getMembersForExemplar(Key<Frame> frameKey, int exemplarId) {
    return createFrameFromRawValues(frameKey, ((DataInfo)(_diKey.get()))._adaptedFrame.names(), this.collectMembers(exemplarId), null);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return null;
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return preds;
  }

  public static Frame createFrameFromRawValues(Key<Frame> outputFrameKey, String[] names, double[][] ex, long[] counts) {
    int nrows = ex.length;
    Vec[] vecs = new Vec[ex[0].length+(counts==null?0:1)];
    int ncol = vecs.length;
    for (int c=0; c<ncol; ++c) {
      vecs[c] = Vec.makeZero(nrows);
      Vec.Writer vw = vecs[c].open();
      if (c==ncol-1 && counts!=null) {
        for (int r = 0; r < nrows; ++r)
          vw.set(r, counts[r]);
      } else {
        for (int r = 0; r < nrows; ++r)
          vw.set(r, ex[r][c]);
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

  public double[][] collectMembers(int whichExemplar) {
    long[] memberindices = _member_indices[whichExemplar];
    Set<Key> chunksFetched = new TreeSet<>();
    DataInfo di = DKV.getGet(_diKey);
    DataInfo.Row row = di.newDenseRow();
    int nrows = memberindices.length;
    int ncols = row.numVals.length;
    double[][] res = new double[nrows][ncols];
    //TODO: multi-threading
    int count=0;
    for (long r : memberindices) {
      row = di.extractDenseRow(di._adaptedFrame.vecs(), r, row);
      res[count++] = Arrays.copyOf(row.numVals, row.numVals.length);

      // for cache cleanup
      Vec v = di._adaptedFrame.vecs()[0];
      chunksFetched.add(v.chunkKey(v.elem2ChunkIdx(r)));
    }
    for (Key ck : chunksFetched) {
      Value v = DKV.get(ck);
      if (!ck.home()) {
        v.freeMem();
      }
    }
    return res;
  }

  @Override
  public Frame scoreExemplarMembers(Key destination_key, int exemplarIdx) {
    return getMembersForExemplar(destination_key, exemplarIdx);
  }
}
