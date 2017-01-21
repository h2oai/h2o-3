package water.rapids.ast.prims.advmath;

import water.DKV;
import water.Key;
import water.MRTask;
import water.Value;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.VecUtils;
import water.util.IcedInt;

import java.util.ArrayList;
import java.util.HashSet;
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
    String inputFrKey = asts[1].str();
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
    // create frame with all 0s (default is train)
    Key<Frame> k1 = Key.make();
    Frame result = new Frame(k1, new String[]{"test_train_split"}, new Vec[]{Vec.makeCon(0.0,fr.anyVec().length())});
    // create index frame
    Key<Frame> k2 = Key.make();
    Frame ones = new Frame(k2, new String[]{"ones"}, new Vec[]{Vec.makeCon(1.0,fr.anyVec().length())});
    DKV.put(ones);
    System.out.println(ones._key);
    Frame idx = Rapids.exec("(cumsum " + ones._key + " 0)").getFrame();
    DKV.put(idx);
    System.out.println(idx._key);
    // loop through each class
    for (int classLabel = 0; classLabel < nClass; ++classLabel) {

        // extract frame with index locations of the minority class
        int clabel = classes == null ? classLabel : (int) classes[classLabel];
        Frame idxFound = Rapids.exec("(rows " + idx._key + " (== " + inputFrKey + " " + clabel + "))").getFrame();
        System.out.println(idxFound._key);
        //DKV.put(idxFound);

        // calculate target number of this class to go to test
        int tnum = (int) Math.ceil(idxFound.anyVec().length() * testFrac);

        // randomly pick the target number of indexes
        int generated = 0;
        int count = 0;
        HashSet<Integer> usedIdxs = new HashSet<Integer>();
        while (generated < tnum) {
          int i = (int) (getRNG(count+seed).nextDouble() * idxFound.anyVec().length());
          if (usedIdxs.contains(i)) { count+=1;continue; }
          usedIdxs.add(i);
          // update the train/test frame
          result.anyVec().set(idxFound.anyVec().at8(i) - 1, 1.0);
          generated += 1;
          count += 1;
        }
        idxFound.delete();
    }

    ones.delete();
    idx.delete();
    return new ValFrame(result);
  }
}
