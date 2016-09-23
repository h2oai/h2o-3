package hex.aggregator;

import hex.*;
import hex.pca.PCAModel;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;

public class AggregatorModel extends Model<AggregatorModel,AggregatorModel.AggregatorParameters,AggregatorModel.AggregatorOutput> implements Model.ExemplarMembers {

  @Override
  public ToEigenVec getToEigenVec() {
    return LinearAlgebraUtils.toEigen;
  }

  public static class AggregatorParameters extends Model.Parameters {
    public String algoName() { return "Aggregator"; }
    public String fullName() { return "Aggregator"; }
    public String javaName() { return AggregatorModel.class.getName(); }
    @Override public long progressUnits() { return 5 + 2*train().anyVec().nChunks() - 1; } // nChunks maps and nChunks-1 reduces, multiply by two for main job overhead

    public double _radius_scale=1.0;
    public DataInfo.TransformType _transform = DataInfo.TransformType.NORMALIZE; // Data transformation
    public PCAModel.PCAParameters.Method _pca_method = PCAModel.PCAParameters.Method.Power;   // Method for dimensionality reduction
    public int _k = 1;                     // Number of principal components
    public int _max_iterations = 1000;     // Max iterations for SVD
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
  public Key<Vec> _exemplar_assignment_vec_key;

  public AggregatorModel(Key selfKey, AggregatorParameters parms, AggregatorOutput output) { super(selfKey,parms,output); }

  @Override
  protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, final Job j) {
    return null;
  }

  @Override
  protected Futures remove_impl(Futures fs) {
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

  public Frame createFrameOfExemplars(Frame orig, Key destination_key) {
    final long[] keep = new long[_exemplars.length];
    for (int i=0;i<keep.length;++i)
      keep[i]=_exemplars[i].gid;

    Vec exAssignment = _exemplar_assignment_vec_key.get();
    // preserve the original row order
    Vec booleanCol = new MRTask() {
      @Override
      public void map(Chunk c, Chunk c2) {
        for (int i=0;i<keep.length;++i) {
          if (keep[i] < c.start()) continue;
          if (keep[i] >= c.start()+c._len) continue;
          c2.set((int)(keep[i]-c.start()), 1);
        }
      }
    }.doAll(new Frame(new Vec[]{exAssignment, exAssignment.makeZero()}))._fr.vec(1);

    Vec[] vecs = Arrays.copyOf(orig.vecs(), orig.vecs().length+1);
    vecs[vecs.length-1] = booleanCol;

    Frame ff = new Frame(orig.names(), orig.vecs());
    ff.add("predicate", booleanCol);
    Frame res = new Frame.DeepSelect().doAll(orig.types(),ff).outputFrame(destination_key, orig.names(), orig.domains());
    booleanCol.remove();
    assert(res.numRows()==_exemplars.length);

    Vec cnts = res.anyVec().makeZero();
    Vec.Writer vw = cnts.open();
    for (int i=0;i<_counts.length;++i)
      vw.set(i, _counts[i]);
    vw.close();
    res.add("counts", cnts);
    DKV.put(destination_key, res);
    return res;
  }

  @Override
  public Frame scoreExemplarMembers(Key<Frame> destination_key, final int exemplarIdx) {
    Vec booleanCol = new MRTask() {
      @Override
      public void map(Chunk c, NewChunk nc) {
        for (int i=0;i<c._len;++i)
          nc.addNum(c.at8(i)==_exemplars[exemplarIdx].gid ? 1 : 0,0);
      }
    }.doAll(Vec.T_NUM, new Frame(new Vec[]{_exemplar_assignment_vec_key.get()})).outputFrame().anyVec();

    Frame orig = _parms.train();
    Vec[] vecs = Arrays.copyOf(orig.vecs(), orig.vecs().length+1);
    vecs[vecs.length-1] = booleanCol;

    Frame ff = new Frame(orig.names(), orig.vecs());
    ff.add("predicate", booleanCol);
    Frame res = new Frame.DeepSelect().doAll(orig.types(),ff).outputFrame(destination_key, orig.names(), orig.domains());
    assert(res.numRows()==_counts[exemplarIdx]);
    booleanCol.remove();
    return res;
  }

  public void checkConsistency() {
    long sum = 0;
    for (long l : this._counts) sum += l;
    assert (sum == _parms.train().numRows());
    final long[] exemplarGIDs = new long[this._counts.length];
    for (int i = 0; i < this._exemplars.length; ++i)
      exemplarGIDs[i] = this._exemplars[i].gid;
    long[] counts = new long[this._exemplars.length];
    for (int i = 0; i < _parms.train().numRows(); ++i) {
      long ass = (_exemplar_assignment_vec_key.get()).at8(i);
      for (int j = 0; j < exemplarGIDs.length; ++j) {
        if (exemplarGIDs[j] == ass) {
          counts[j]++;
          break;
        }
      }
    }
    sum = 0;
    for (long l : counts) sum += l;
    assert (sum == _parms.train().numRows());

    for (int i = 0; i < counts.length; ++i) {
      assert (counts[i] == this._counts[i]);
    }
  }

}
