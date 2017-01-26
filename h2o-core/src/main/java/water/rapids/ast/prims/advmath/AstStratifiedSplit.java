package water.rapids.ast.prims.advmath;

import water.*;
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
    Frame oldfr = stk.track(asts[1].exec(env)).getFrame();
    Key<Frame> inputFrKey = Key.make();
    Frame fr = oldfr.deepCopy(inputFrKey.toString());
    DKV.put(fr);
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
    final String[] domains = y.domain();
    final long[] seeds = new long[nClass]; // seed for each regular fold column (one per class)
    for (int i = 0; i < nClass; ++i)
      seeds[i] = getRNG(seed + i).nextLong();
    String[] dom = new String[]{"train", "test"};
    // create frame with all 0s (default is train)
    Key<Frame> k1 = Key.make();
    Frame result = new Frame(k1, new String[]{"test_train_split"}, new Vec[]{Vec.makeCon(0.0,fr.anyVec().length())});
    DKV.put(result);
    // create index frame
    Key<Frame> k2 = Key.make();
    Frame ones = new Frame(k2, new String[]{"ones"}, new Vec[]{Vec.makeCon(1.0,fr.anyVec().length())});
    DKV.put(ones);
    Frame idx = Rapids.exec("(cumsum " + ones._key + " 0)").getFrame();
    DKV.put(idx);
    // loop through each class
    for (int classLabel = 0; classLabel < nClass; ++classLabel) {

        // extract frame with index locations of the minority class
        Frame idxFound = null;
        if (domains == null) {
          // integer case
          idxFound = Rapids.exec("(rows " + idx._key + " (== " + fr._key + " " + classes[classLabel] + "))").getFrame();
        } else {
          // enum case
          idxFound = Rapids.exec("(rows " + idx._key + " (== " + fr._key + " \"" + domains[classLabel] + "\"))").getFrame();
        }
        idxFound._key = Key.make();
        DKV.put(idxFound);

        // calculate target number of this class to go to test
        int tnum = (int) Math.max(Math.round(idxFound.anyVec().length() * testFrac), 1);

        // randomly select the target number of indexes
        int generated = 0;
        int count = 0;
        HashSet<Long> usedIdxs = new HashSet<Long>();
        while (generated < tnum) {
          long i = (long) (getRNG(count+seed).nextDouble() * idxFound.anyVec().length());
          if (usedIdxs.contains(idxFound.vec(0).at8(i) - 1)) { count+=1;continue; }
          usedIdxs.add(idxFound.vec(0).at8(i) - 1);
          generated += 1;
          count += 1;
        }
        new ClassAssignMRTask(usedIdxs).doAll(result.anyVec());
        idxFound.delete();
    }

    ones.delete();
    idx.delete();
    fr.delete();
    return new ValFrame(result);
  }
  public static class ClassAssignMRTask extends MRTask<AstStratifiedSplit.ClassAssignMRTask> {
     HashSet<Long> _idx;
     ClassAssignMRTask(HashSet<Long> idx) {
         _idx = idx;
     };
     @Override
     public void map(Chunk ck) {
       for (long i = 0; i<ck.len(); i++) {
           if (_idx.contains(ck.cidx() + i)) {
               ck.set((int)i,1.0);
           }
       }
     }

  }

}
