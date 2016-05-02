package ai.h2o.automl.utils;

import ai.h2o.automl.transforms.Expr;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Random;

/**
 * Useful utilities
 */
public class AutoMLUtils {
  public static Vec[] makeWeights(Vec v, final double trainRatio) {
    Vec randVec = v.makeZero();
    final long seed = water.util.RandomUtils.getRNG(new Random().nextLong()).nextLong();
    new MRTask() {
      @Override public void map(Chunk c){
        long start = c.start();
        Random rng = new water.util.RandomUtils.PCGRNG(start,1);
        for(int i = 0; i < c._len; ++i) {
          rng.setSeed(seed+start+i); // Determinstic per-row
          c.set(i, rng.nextFloat() < trainRatio ? 1 : 0);
        }
      }
    }.doAll(randVec);
    return new Vec[]{randVec, Expr.binOp("-", 1, randVec).toWrappedVec().makeVec()};  // gives weight and 1-weight
  }

  public static Frame[] makeTrainTest(Frame fr, final double trainRatio) {
    Frame[] res= new Frame[2];
    Vec[] trainTestWeights = makeWeights(fr.anyVec(), 0.8);
    Vec[] vecs = new Vec[fr.numCols()+1];
    String[] names = new String[fr.numCols()+1];
    System.arraycopy(fr.names(),0,names,0,fr.names().length);
    System.arraycopy(fr.vecs(),0,vecs,0,fr.vecs().length);
    names[names.length-1]="weight";
    vecs[vecs.length-1] = trainTestWeights[0];
    res[0] = new Frame(Key.make(),names.clone(),vecs.clone());  // reclone for safety
    DKV.put(res[0]);
    vecs = vecs.clone();
    vecs[vecs.length-1] = trainTestWeights[1];
    res[1] = new Frame(Key.make(), names.clone(),vecs.clone()); // reclone for safety
    DKV.put(res[1]);
    return res;
  }
}
