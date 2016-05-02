package ai.h2o.automl.utils;

import ai.h2o.automl.transforms.Expr;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.List;
import java.util.Random;

import static water.util.RandomUtils.getRNG;

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

  public static Vec[] makeStratifiedWeights(Vec v, final double trainRatio) {
    long seed = getRNG(new Random().nextLong()).nextLong();
    final int nClass = v.domain().length;
    final long[] seeds = new long[nClass]; // seed for each regular fold column (one per class)
    for( int i=0;i<nClass;++i)
      seeds[i] = getRNG(seed + i).nextLong();
    Vec randVec = new MRTask() {
      private boolean isTest(int row, long seed) { return getRNG(row+seed).nextDouble() > trainRatio; }
      @Override public void map(Chunk y, NewChunk ss) { // 1-> train, 0-> test
        int start = (int)y.start();
        for(int classLabel=0; classLabel<nClass; ++classLabel)
          for(int row=0;row<y._len;++row)
            if( y.at8(row) == classLabel )
              ss.addNum( isTest(start+row,seeds[classLabel])?0:1,0);
      }
    }.doAll(Vec.T_NUM, v).outputFrame().anyVec();
    return new Vec[]{randVec,Expr.binOp("-",1,randVec).toWrappedVec().makeVec()};
  }

  public static Frame[] makeTrainTest(Frame fr, String response, final double trainRatio, boolean stratified) {
    Frame[] res= new Frame[2];
    Vec[] trainTestWeights = stratified ? makeStratifiedWeights(fr.vec(response),trainRatio) : makeWeights(fr.anyVec(), trainRatio);
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

  public static int[] intListToA(List<Integer> list) {
    int[] a=new int[0];
    if( list.size() >0 ) {
      a = new int[list.size()];
      for(int i=0;i<a.length;++i) a[i] = list.get(i);
    }
    return a;
  }
}
