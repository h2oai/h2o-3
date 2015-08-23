package hex.tree.drf;


import java.util.Arrays;
import java.util.Random;

import static hex.genmodel.GenModel.getPrediction;
import hex.tree.CompressedTree;
import static hex.tree.DTreeScorer.scoreTree;
import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.ModelUtils;
import static water.util.RandomUtils.getRNG;

/** Score given tree model and preserve errors per tree in form of votes (for classification)
 * or MSE (for regression).
 *
 * This is different from Model.score() function since the MR task
 * uses inverse loop: first over all trees and over all rows in chunk.
 */
public class TreeMeasuresCollector extends MRTask<TreeMeasuresCollector> {
  /* @IN */ final private float     _rate;
  /* @IN */       private CompressedTree[/*N*/][/*nclasses*/] _trees; // FIXME: Pass only tree-keys since serialized trees are passed over wire !!!
  /* @IN */ final private int       _var;
  /* @IN */ final private boolean   _oob;
  /* @IN */ final private int       _ncols;
  /* @IN */ final private int       _nclasses;
  /* @IN */ final private boolean   _classification;
  /* @IN */ final private double   _threshold;

  /* @INOUT */ private final int _ntrees;
  /* @OUT */ private long [/*ntrees*/] _votes; // Number of correct votes per tree (for classification only)
  /* @OUT */ private long [/*ntrees*/] _nrows; // Number of scored row per tree (for classification/regression)
  /* @OUT */ private float[/*ntrees*/] _sse;   // Sum of squared errors per tree (for regression only)

  private TreeMeasuresCollector(CompressedTree[/*N*/][/*nclasses*/] trees, int nclasses, int ncols, float rate, int variable, double threshold) {
    assert trees.length > 0;
    assert nclasses == trees[0].length;
    _trees = trees; _ncols = ncols;
    _rate = rate; _var = variable;
    _oob = true; _ntrees = trees.length;
    _nclasses = nclasses;
    _classification = (nclasses>1);
    _threshold = threshold;
  }

  public static class ShuffleTask extends MRTask<ShuffleTask> {

    @Override public void map(Chunk ic, Chunk oc) {
      if (ic._len==0) return;
      // Each vector is shuffled in the same way
      Random rng = getRNG(seed(ic.cidx()));
      oc.set(0,ic.atd(0));
      for (int row=1; row<ic._len; row++) {
        int j = rng.nextInt(row+1); // inclusive upper bound <0,row>
        // Arghhh: expand the vector into double
        if (j!=row) oc.set(row, oc.atd(j));
        oc.set(j, ic.atd(row));
      }
    }

    public static long seed(int cidx) { return (0xe031e74f321f7e29L + ((long)cidx << 32L)); }

    public static Vec shuffle(Vec ivec) {
      Vec ovec = ivec.makeZero();
      new ShuffleTask().doAll(ivec, ovec);
      return ovec;
    }
  }

  @Override public void map(Chunk[] chks) {
    double[] data = new double[_ncols];
    double[] preds = new double[_nclasses+1];
    Chunk cresp = chk_resp(chks);
    int   nrows = cresp._len;
    int   [] oob = new int[2+Math.round((1f-_rate)*nrows*1.2f+0.5f)]; // preallocate
    int   [] soob = null;

    // Prepare output data
    _nrows      = new long[_ntrees];
    _votes      = _classification ? new long[_ntrees] : null;
    _sse        = _classification ? null : new float[_ntrees];
    long seedForOob = ShuffleTask.seed(cresp.cidx()); // seed for shuffling oob samples
    // Start iteration
    for( int tidx=0; tidx<_ntrees; tidx++) { // tree
      // OOB RNG for this tree
      Random rng = rngForTree(_trees[tidx], cresp.cidx());
      // Collect oob rows and permutate them
      oob = ModelUtils.sampleOOBRows(nrows, _rate, rng, oob); // reuse use the same array for sampling
      int oobcnt = oob[0]; // Get number of sample rows
      if (_var>=0) {
        if (soob==null || soob.length < oobcnt) soob = new int[oobcnt];
        ArrayUtils.shuffleArray(oob, oobcnt, soob, seedForOob, 1); // Shuffle array and copy results into <code>soob</code>
      }
      for(int j = 1; j < 1+oobcnt; j++) {
        int row = oob[j];
        if (cresp.isNA(row)) continue; // we cannot deal with this row anyhow
        // Do scoring:
        // - prepare a row data
        for (int i=0;i<_ncols;i++) data[i] = chks[i].atd(row); // 1+i - one free is expected by prediction
        // - permute variable
        if (_var>=0) data[_var] = chks[_var].atd(soob[j-1]);
        else assert soob==null;
        // - score data
        Arrays.fill(preds, 0);
        // - score only the tree
        score0(data, preds, _trees[tidx]);
        // - derive a prediction
        if (_classification) {
          int pred = getPrediction(preds, null /*FIXME: should use model's _priorClassDistribution*/, data, _threshold);
          int actu = (int) cresp.at8(row);
          // assert preds[pred] > 0 : "There should be a vote for at least one class.";
          // - collect only correct votes
          if (pred == actu) _votes[tidx]++;
        } else { /* regression */
          double pred = preds[0]; // Important!
          double actu = cresp.atd(row);
          _sse[tidx] += (actu-pred)*(actu-pred);
        }
        // - collect rows which were used for voting
        _nrows[tidx]++;
        //if (_var<0) System.err.println("VARIMP OOB row: " + (cresp._start+row) + " : " + Arrays.toString(data) + " tree/actu: " + pred + "/" + actu);
      }
    }
    // Clean-up
    _trees = null;
  }
  @Override public void reduce( TreeMeasuresCollector t ) { ArrayUtils.add(_votes,t._votes); ArrayUtils.add(_nrows, t._nrows); ArrayUtils.add(_sse, t._sse); }

  public TreeVotes resultVotes() { return new TreeVotes(_votes, _nrows, _ntrees); }
  public TreeSSE   resultSSE  () { return new TreeSSE  (_sse,   _nrows, _ntrees); }
  /* This is a copy of score0 method from DTree:615 */
  private void score0(double data[], double preds[], CompressedTree[] ts) {
    scoreTree(data, preds, ts);
  }

  private Chunk chk_resp( Chunk chks[] ) { return chks[_ncols]; }

  private Random rngForTree(CompressedTree[] ts, int cidx) {
    return _oob ? ts[0].rngForChunk(cidx) : new DummyRandom(); // k-class set of trees shares the same random number
  }

  /* For bulk scoring
  public static TreeVotes collect(TreeModel tmodel, Frame f, int ncols, float rate, int variable) {
    CompressedTree[][] trees = new CompressedTree[tmodel.ntrees()][];
    for (int tidx = 0; tidx < tmodel.ntrees(); tidx++) trees[tidx] = tmodel.ctree(tidx);
    return new TreeVotesCollector(trees, tmodel.nclasses(), ncols, rate, variable).doAll(f).result();
  }*/

  public static TreeVotes collectVotes(CompressedTree[/*nclass || 1 for regression*/] tree, int nclasses, Frame f, int ncols, float rate, int variable, double threshold) {
    return new TreeMeasuresCollector(new CompressedTree[][] {tree}, nclasses, ncols, rate, variable, threshold).doAll(f).resultVotes();
  }
  public static TreeSSE collectSSE(CompressedTree[/*nclass || 1 for regression*/] tree, int nclasses, Frame f, int ncols, float rate, int variable, double threshold) {
    return new TreeMeasuresCollector(new CompressedTree[][] {tree}, nclasses, ncols, rate, variable, threshold).doAll(f).resultSSE();
  }

  private static final class DummyRandom extends Random {
    @Override public final float nextFloat() { return 1.0f; }
  }

  /** A simple holder for set of different tree measurements. */
  public static abstract class TreeMeasures<T extends TreeMeasures> extends Iced {
    /** Actual number of trees which votes are stored in this object */
    protected int _ntrees;
    /** Number of processed row per tree. */
    protected long[/*ntrees*/]   _nrows;

    public TreeMeasures(int initialCapacity) { _nrows = new long[initialCapacity]; }
    public TreeMeasures(long[] nrows, int ntrees) { _nrows = nrows; _ntrees = ntrees;}
    /** Returns number of rows which were used during voting per individual tree. */
    public final long[] nrows() { return _nrows; }
    /** Returns number of voting predictors */
    public final int    npredictors() { return _ntrees; }
    /** Returns a list of accuracies per tree. */
    public abstract double accuracy(int tidx);
    public final double[] accuracy() {
      double[] r = new double[_ntrees];
      // Average of all trees
      for (int tidx=0; tidx<_ntrees; tidx++) r[tidx] = accuracy(tidx);
      return r;
    }
    /** Compute variable importance with respect to given votes.
     * The given {@link T} object represents correct votes.
     * This object represents votes over shuffled data.
     *
     * @param right individual tree measurements performed over not shuffled data.
     * @return computed importance and standard deviation
     */
    public abstract double[/*2*/] imp(T right);

    public abstract T append(T t);
  }

  /** A class holding tree votes. */
  public static class TreeVotes extends TreeMeasures<TreeVotes> {
    /** Number of correct votes per tree */
    private long[/*ntrees*/]   _votes;

    public TreeVotes(int initialCapacity) {
      super(initialCapacity);
      _votes = new long[initialCapacity];
    }
    public TreeVotes(long[] votes, long[] nrows, int ntrees) {
      super(nrows, ntrees);
      _votes = votes;
    }
    /** Returns number of positive votes per tree. */
    public final long[] votes() { return _votes; }

    /** Returns accuracy per individual trees. */
    @Override public final double accuracy(int tidx)  {
      assert tidx < _nrows.length && tidx < _votes.length;
      return ((double) _votes[tidx]) / _nrows[tidx];
    }

    /** Compute variable importance with respect to given votes.
     * The given {@link TreeVotes} object represents correct votes.
     * This object represents votes over shuffled data.
     *
     * @param right individual tree voters performed over not shuffled data.
     * @return computed importance and standard deviation
     */
    @Override public final double[/*2*/] imp(TreeVotes right) {
      assert npredictors() == right.npredictors();
      int ntrees = npredictors();
      double imp = 0;
      double sd  = 0;
      // Over all trees
      for (int tidx = 0; tidx < ntrees; tidx++) {
        assert right.nrows()[tidx] == nrows()[tidx];
        double delta = ((double) (right.votes()[tidx] - votes()[tidx])) / nrows()[tidx];
        imp += delta;
        sd  += delta * delta;
      }
      double av = imp / ntrees;
      double csd = Math.sqrt( (sd/ntrees - av*av) / ntrees );
      return new double[] { av, csd};
    }

    /** Append a tree votes to a list of trees. */
    public TreeVotes append(long rightVotes, long allRows) {
      assert _votes.length > _ntrees && _votes.length == _nrows.length : "TreeVotes inconsistency!";
      _votes[_ntrees] = rightVotes;
      _nrows[_ntrees] = allRows;
      _ntrees++;
      return this;
    }

    @Override public TreeVotes append(final TreeVotes tv) {
      for (int i=0; i<tv.npredictors(); i++)
        append(tv._votes[i], tv._nrows[i]);
      return this;
    }
  }

  /** A simple holder serving SSE per tree. */
  public static class TreeSSE extends TreeMeasures<TreeSSE> {
    /** SSE per tree */
    private float[/*ntrees*/]   _sse;

    public TreeSSE(int initialCapacity) {
      super(initialCapacity);
      _sse = new float[initialCapacity];
    }
    public TreeSSE(float[] sse, long[] nrows, int ntrees) {
      super(nrows, ntrees);
      _sse = sse;
    }
    @Override public double accuracy(int tidx) {
      return _sse[tidx] / _nrows[tidx];
    }
    @Override public double[] imp(TreeSSE right) {
      assert npredictors() == right.npredictors();
      int ntrees = npredictors();
      double imp = 0;
      double sd  = 0;
      // Over all trees
      for (int tidx = 0; tidx < ntrees; tidx++) {
        assert right.nrows()[tidx] == nrows()[tidx]; // check that we iterate over same OOB rows
        double delta = ((double) (_sse[tidx] - right._sse[tidx])) / nrows()[tidx];
        imp += delta;
        sd  += delta * delta;
      }
      double av = imp / ntrees;
      double csd = Math.sqrt( (sd/ntrees - av*av) / ntrees );
      return new double[] { av, csd };
    }
    @Override public TreeSSE append(TreeSSE t) {
      for (int i=0; i<t.npredictors(); i++)
        append(t._sse[i], t._nrows[i]);
      return this;
    }
    /** Append a tree sse to a list of trees. */
    public TreeSSE append(float sse, long allRows) {
      assert _sse.length > _ntrees && _sse.length == _nrows.length : "TreeVotes inconsistency!";
      _sse  [_ntrees] = sse;
      _nrows[_ntrees] = allRows;
      _ntrees++;
      return this;
    }
  }

  public static TreeVotes asVotes(TreeMeasures tm) { return (TreeVotes) tm; }
  public static TreeSSE   asSSE  (TreeMeasures tm) { return (TreeSSE)   tm; }
}
