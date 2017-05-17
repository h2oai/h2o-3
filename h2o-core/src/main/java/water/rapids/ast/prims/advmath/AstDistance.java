package water.rapids.ast.prims.advmath;

import water.H2O;
import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

/**
 * Calculate Distance Metric between pairs of rows
 */
public class AstDistance extends AstBuiltin<AstDistance> {
  @Override
  public String[] args() {
    return new String[]{"ary", "x", "y", "measure"};
  }

  @Override
  public int nargs() {
    return 1 + 3; /* (distance X Y measure) */
  }

  @Override
  public String str() {
    return "distance";
  }

  @Override
  public String description() {
    return "Compute a pairwise distance measure between all rows of two numeric H2OFrames.\n" +
            "For a given (usually larger) reference frame (N rows x p cols),\n" +
            "and a (usually smaller) query frame (M rows x p cols), we return a numeric Frame of size (N rows x M cols),\n" +
            "where the ij-th element is the distance measure between the i-th reference row and the j-th query row.\n" +
            "Note1: The output frame is symmetric.\n" +
            "Note2: Since N x M can be very large, it may be more efficient (memory-wise) to make multiple calls with smaller query Frames.";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame frx = stk.track(asts[1].exec(env)).getFrame();
    Frame fry = stk.track(asts[2].exec(env)).getFrame();
    String measure = stk.track(asts[3].exec(env)).getStr();
    return computeCosineDistances(frx, fry, measure);
  }

  public Val computeCosineDistances(Frame references, Frame queries, String distanceMetric) {
    Log.info("Number of references: " + references.numRows());
    Log.info("Number of queries   : " + queries.numRows());
    String[] options = new String[]{"cosine","cosine_sq","l1","l2"};
    if (!ArrayUtils.contains(options, distanceMetric.toLowerCase()))
      throw new IllegalArgumentException("Invalid distance measure provided: " + distanceMetric + ". Mustbe one of " + Arrays.toString(options));
    if (references.numRows() * queries.numRows() * 8 > H2O.CLOUD.free_mem() )
      throw new IllegalArgumentException("Not enough free memory to allocate the distance matrix (" +
          references.numRows() + " rows and " + queries.numRows() + " cols. Try specifying a smaller query frame.");
    if (references.numCols() != queries.numCols())
      throw new IllegalArgumentException("Frames must have the same number of cols, found " + references.numCols() + " and " + queries.numCols());
    if (queries.numRows() > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Queries can't be larger than 2 billion rows.");
    if (queries.numCols() != references.numCols())
      throw new IllegalArgumentException("Queries and References must have the same dimensionality");
    for (int i=0;i<queries.numCols();++i) {
      if (!references.vec(i).isNumeric())
        throw new IllegalArgumentException("References column " + references.name(i) + " is not numeric.");
      if (!queries.vec(i).isNumeric())
        throw new IllegalArgumentException("Queries column " + references.name(i) + " is not numeric.");
      if (references.vec(i).naCnt()>0)
        throw new IllegalArgumentException("References column " + references.name(i) + " contains missing values.");
      if (queries.vec(i).naCnt()>0)
        throw new IllegalArgumentException("Queries column " + references.name(i) + " contains missing values.");
    }
    return new ValFrame(new DistanceComputer(queries, distanceMetric).doAll((int)queries.numRows(), Vec.T_NUM, references).outputFrame());
  }

  static public class DistanceComputer extends MRTask<DistanceComputer> {
    Frame _queries;
    String _measure;

    DistanceComputer(Frame queries, String measure) {
      _queries = queries;
      _measure = measure;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      int p = cs.length; //dimensionality
      int Q = (int) _queries.numRows();
      int R = cs[0]._len;
      Vec.Reader[] Qs = new Vec.Reader[p];
      for (int i = 0; i < p; ++i) {
        Qs[i] = _queries.vec(i).new Reader();
      }
      double[] denomR = null;
      double[] denomQ = null;

      final boolean cosine = _measure.toLowerCase().equals("cosine");
      final boolean cosine_sq = _measure.toLowerCase().equals("cosine_sq");
      final boolean l1 = _measure.toLowerCase().equals("l1");
      final boolean l2 = _measure.toLowerCase().equals("l2");

      if (cosine || cosine_sq) {
        denomR = new double[R];
        denomQ = new double[Q];
        for (int r = 0; r < R; ++r) { // Reference row (chunk-local)
          for (int c = 0; c < p; ++c) { //cols
            denomR[r] += Math.pow(cs[c].atd(r), 2);
          }
        }
        for (int q = 0; q < Q; ++q) { // Query row (global)
          for (int c = 0; c < p; ++c) { //cols
            denomQ[q] += Math.pow(Qs[c].at(q), 2);
          }
        }
      }

      for (int r = 0; r < cs[0]._len; ++r) { // Reference row (chunk-local)
        for (int q = 0; q < Q; ++q) { // Query row (global)
          double distRQ = 0;
          if (l1) {
            for (int c = 0; c < p; ++c) { //cols
              distRQ += Math.abs(cs[c].atd(r) - Qs[c].at(q));
            }
          } else if (l2) {
            for (int c = 0; c < p; ++c) { //cols
              distRQ += Math.pow(cs[c].atd(r) - Qs[c].at(q), 2);
            }
          } else if (cosine || cosine_sq) {
            for (int c = 0; c < p; ++c) { //cols
              distRQ += cs[c].atd(r) * Qs[c].at(q);
            }
            if (cosine_sq) {
              distRQ *= distRQ;
              distRQ /= denomR[r] * denomQ[q];
            } else {
              distRQ /= Math.sqrt(denomR[r] * denomQ[q]);
            }
          }
          ncs[q].addNum(distRQ); // one Q distance per Reference
        }
      }
    }
  }
}