package water.rapids.ast.prims.advmath;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.VecUtils;

import java.util.Random;

import static water.util.RandomUtils.getRNG;

public class AstStratifiedSplit extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "test_frac", "seed"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (h2o.random_stratified_split y test_frac seed)

  @Override
  public String str() {
    return "h2o.random_stratified_split";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("Must give a single column to stratify against. Got: " + fr.numCols() + " columns.");
    Vec y = fr.anyVec();
    if (!(y.isCategorical() || (y.isNumeric() && y.isInt())))
      throw new IllegalArgumentException("stratification only applies to integer and categorical columns. Got: " + y.get_type_str());
    final double testFrac = asts[2].exec(env).getNum();
    long seed = (long) asts[3].exec(env).getNum();
    seed = seed == -1 ? new Random().nextLong() : seed;
    final long[] classes = new VecUtils.CollectDomain().doAll(y).domain();
    final int nClass = y.isNumeric() ? classes.length : y.domain().length;
    final long[] seeds = new long[nClass]; // seed for each regular fold column (one per class)
    for (int i = 0; i < nClass; ++i)
      seeds[i] = getRNG(seed + i).nextLong();
    String[] dom = new String[]{"train", "test"};
    return new ValFrame(new MRTask() {
      private boolean isTest(int row, long seed) {
        return getRNG(row + seed).nextDouble() <= testFrac;
      }

      @Override
      public void map(Chunk y, NewChunk ss) { // 0-> train, 1-> test
        int start = (int) y.start();
        for (int classLabel = 0; classLabel < nClass; ++classLabel) {
          for (int row = 0; row < y._len; ++row) {
            if (y.at8(row) == (classes == null ? classLabel : classes[classLabel])) {
              if (isTest(start + row, seeds[classLabel])) ss.addNum(1, 0);
              else ss.addNum(0, 0);
            }
          }
        }
      }
    }.doAll(1, Vec.T_NUM, new Frame(y)).outputFrame(new String[]{"test_train_split"}, new String[][]{dom}));
  }
}
