package hex.aggregator;

import hex.DataInfo;
import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.pca.PCAModel;
import water.*;
import water.fvec.*;

public class AggregatorModel extends Model<AggregatorModel,AggregatorModel.AggregatorParameters,AggregatorModel.AggregatorOutput> implements Model.ExemplarMembers {

  public static class AggregatorParameters extends Model.Parameters {
    public String algoName() { return "Aggregator"; }
    public String fullName() { return "Aggregator"; }
    public String javaName() { return AggregatorModel.class.getName(); }
    @Override public long progressUnits() { return 5 + 2*train().vecs().nChunks() - 1; } // nChunks maps and nChunks-1 reduces, multiply by two for main job overhead

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
  public VecAry _exemplar_assignment_vec;

  public AggregatorModel(Key selfKey, AggregatorParameters parms, AggregatorOutput output) { super(selfKey,parms,output); }

  @Override
  protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, final Job j) {
    return null;
  }

  @Override
  protected Futures remove_impl(Futures fs) {
    _exemplar_assignment_vec.remove();
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

  public Frame createFrameOfExemplars(Key destination_key) {
    final long[] keep = new long[_exemplars.length];
    for (int i=0;i<keep.length;++i)
      keep[i]=_exemplars[i].gid;
    VecAry exAssignment = _exemplar_assignment_vec;
    // preserve the original row order
    VecAry booleanCol = new MRTask() {
      @Override
      public void map(Chunk c, Chunk c2) {
        for (int i=0;i<keep.length;++i) {
          if (keep[i] < c.start()) continue;
          if (keep[i] >= c.start()+c._len) continue;
          c2.set((int)(keep[i]-c.start()), 1);
        }
      }
    }.doAll(new VecAry(exAssignment, exAssignment.makeZero())).vecs().getVecs(1);

    Frame orig = _parms.train();
    VecAry vecs = orig.vecs();
    vecs.addVecs(booleanCol);

    Frame ff = new Frame(orig);
    ff.add("predicate", booleanCol);
    Frame res = new Frame.DeepSelect().doAll(orig.vecs().types(),ff.vecs()).outputFrame(destination_key, orig._names, orig.vecs().domains());
    booleanCol.remove();
    assert(res.numRows()==_exemplars.length);

    VecAry cnts = res.vecs().makeZero();
    VecAry.Writer vw = cnts.open();
    for (int i=0;i<_counts.length;++i)
      vw.set(i,0, _counts[i]);
    vw.close();
    res.add("counts", cnts);
    return res;
  }

  @Override
  public Frame scoreExemplarMembers(Key destination_key, final int exemplarIdx) {
    VecAry booleanCol = new MRTask() {
      @Override
      public void map(Chunk c, NewChunk nc) {
        for (int i=0;i<c._len;++i)
          nc.addNum(c.at8(i)==_exemplars[exemplarIdx].gid ? 1 : 0,0);
      }
    }.doAll(1,Vec.T_NUM, _exemplar_assignment_vec).outputVecs(null);

    Frame orig = _parms.train();
    VecAry vecs = new VecAry(orig.vecs());
    vecs.addVecs(booleanCol);

    Frame ff = new Frame(orig);
    ff.add("predicate", booleanCol);
    Frame res = new Frame.DeepSelect().doAll(orig.vecs().types(),ff.vecs()).outputFrame(destination_key, orig._names, orig.vecs().domains());
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
      long ass = _exemplar_assignment_vec.at8(i,0);
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
