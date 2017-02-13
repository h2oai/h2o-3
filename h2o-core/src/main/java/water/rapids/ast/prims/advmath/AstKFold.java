package water.rapids.ast.prims.advmath;

import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.VecUtils;

import java.util.Random;

import static water.util.RandomUtils.getRNG;

public class AstKFold extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "nfolds", "seed"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (kfold_column x nfolds seed)

  @Override
  public String str() {
    return "kfold_column";
  }

  public static VecAry kfoldColumn(Vec v, final int nfolds, final long seed) {
    return kfoldColumn(new VecAry(v),nfolds,seed);
  }
  public static VecAry kfoldColumn(VecAry v, final int nfolds, final long seed) {
    new MRTask() {
      @Override
      public void map(ChunkAry c) {
        long start = c._start;
        for (int i = 0; i < c._len; ++i) {
          int fold = Math.abs(getRNG(start + seed + i).nextInt()) % nfolds;
          c.set(i,0, fold);
        }
      }
    }.doAll(v);
    return v;
  }

  public static VecAry moduloKfoldColumn(Vec v, final int nfolds) {
    return moduloKfoldColumn(new VecAry(v),nfolds);
  }
  public static VecAry moduloKfoldColumn(VecAry v, final int nfolds) {
    new MRTask() {
      @Override
      public void map(ChunkAry c) {
        long start = c._start;
        for (int i = 0; i < c._len; ++i)
          c.set(i, 0, (int) ((start + i) % nfolds));
      }
    }.doAll(v);
    return v;
  }

  public static VecAry stratifiedKFoldColumn(VecAry y, final int nfolds, final long seed) {
    // for each class, generate a fold column (never materialized)
    // therefore, have a seed per class to be used by the map call
    if (!(y.isCategorical() || (y.isNumeric() && y.isInt())))
      throw new IllegalArgumentException("stratification only applies to integer and categorical columns. Got: " + y.get_type_str());
    final long[] classes = new VecUtils.CollectDomain().doAll(y).domain();
    final int nClass = y.isNumeric() ? classes.length : y.domain().length;
    final long[] seeds = new long[nClass]; // seed for each regular fold column (one per class)
    for (int i = 0; i < nClass; ++i)
      seeds[i] = getRNG(seed + i).nextLong();

    return new MRTask() {

      private int getFoldId(long absoluteRow, long seed) {
        return Math.abs(getRNG(absoluteRow + seed).nextInt()) % nfolds;
      }

      // dress up the foldColumn (y[1]) as follows:
      //   1. For each testFold and each classLabel loop over the response column (y[0])
      //   2. If the classLabel is the current response and the testFold is the foldId
      //      for the current row and classLabel, then set the foldColumn to testFold
      //
      //   How this balances labels per fold:
      //      Imagine that a KFold column was generated for each class. Observe that this
      //      makes the outer loop a way of selecting only the test rows from each fold
      //      (i.e., the holdout rows). Each fold is balanced sequentially in this way
      //      since y[1] is only updated if the current row happens to be a holdout row
      //      for the given classLabel.
      //
      //      Next observe that looping over each classLabel filters down each KFold
      //      so that it contains labels for just THAT class. This is how the balancing
      //      can be made so that it is independent of the chunk distribution and the
      //      per chunk class distribution.
      //
      //      Downside is this performs nfolds*nClass passes over each ByteArraySupportedChunk. For
      //      "reasonable" classification problems, this could be 100 passes per ByteArraySupportedChunk.
      @Override
      public void map(ChunkAry y) {
        long start = y._start;
        for (int testFold = 0; testFold < nfolds; ++testFold) {
          for (int classLabel = 0; classLabel < nClass; ++classLabel) {
            for (int row = 0; row < y._len; ++row) {
              // missing response gets spread around
              if (y.isNA(row,0)) {
                if ((start + row) % nfolds == testFold)
                  y.set(row, 1, testFold);
              } else {
                if (y.at8(row,0) == (classes == null ? classLabel : classes[classLabel])) {
                  if (testFold == getFoldId(start + row, seeds[classLabel]))
                    y.set(row, 1, testFold);
                }
              }
            }
          }
        }
      }
    }.doAll(new Frame(y.append(y.makeZero())))._fr.vec(1);
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Vec foldVec = stk.track(asts[1].exec(env)).getFrame().anyVec().makeZero();
    int nfolds = (int) asts[2].exec(env).getNum();
    long seed = (long) asts[3].exec(env).getNum();
    return new ValFrame(new Frame(kfoldColumn(foldVec, nfolds, seed == -1 ? new Random().nextLong() : seed)));
  }
}

