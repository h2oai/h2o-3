package ai.h2o.automl.utils;

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
  public static Vec[] makeWeights(Vec responseVec, final double trainRatio, final double[] weightMult) {
    Vec[] weights = new Vec[2];
    weights[0] = responseVec.makeZero();
    final long seed = water.util.RandomUtils.getRNG(new Random().nextLong()).nextLong();
    new MRTask() {
      @Override public void map(Chunk[] c){
        long start = c[0].start();
        Random rng = new water.util.RandomUtils.PCGRNG(start,1);
        int yval;
        for(int i = 0; i < c[0]._len; ++i) {
          yval=(int)c[0].at8(i);
          rng.setSeed(seed+start+i); // Determinstic per-row
          c[1].set(i, rng.nextFloat() < trainRatio ? (weightMult==null?1:weightMult[yval]) : 0);
        }
      }
    }.doAll(responseVec,weights[0]);
    if( null!=weightMult ) {
      weights[1] = new MRTask() {
        @Override public void map(Chunk[] cs, NewChunk n) {
          for(int i=0;i<cs[0]._len;++i) {
            if (0 == cs[1].at8(i))
              n.addNum(weightMult[(int) cs[0].at8(i)]);
            else
              n.addNum(0);
          }
        }
      }.doAll(Vec.T_NUM, new Frame(responseVec,weights[0])).outputFrame().anyVec();
    }
    return weights;
  }

  public static Vec[] makeStratifiedWeights(Vec responseVec, final double trainRatio, final double[] weightMult) {
    Vec[] weights = new Vec[2];
    long seed = getRNG(new Random().nextLong()).nextLong();
    final int nClass = responseVec.domain().length;
    final long[] seeds = new long[nClass]; // seed for each regular fold column (one per class)
    for( int i=0;i<nClass;++i)
      seeds[i] = getRNG(seed + i).nextLong();
    weights[0] = new MRTask() {
      private boolean isTest(int row, long seed) { return getRNG(row+seed).nextDouble() > trainRatio; }
      @Override public void map(Chunk y, NewChunk ss) { // 1-> train, 0-> test
        int start = (int)y.start();
        int yval;
        for(int classLabel=0; classLabel<nClass; ++classLabel)
          for(int row=0;row<y._len;++row)
            if( (yval=(int)y.at8(row)) == classLabel )
              ss.addNum( isTest(start+row,seeds[classLabel])?0:(weightMult==null?1:weightMult[yval]));
      }
    }.doAll(Vec.T_NUM, responseVec).outputFrame().anyVec();
    if( null!=weightMult ) {
      new MRTask() {
        @Override public void map(Chunk[] cs) {
          for(int i=0; i<cs[0]._len; ++i) {
            if (0 == cs[1].at8(i)) cs[2].set(i,weightMult[(int) cs[0].at8(i)]);
          }
        }
      }.doAll(responseVec,weights[0],weights[1]=weights[0].makeZero());
    }
    return weights;
  }

  public static Frame[] makeTrainTest(Frame fr, String response, final double trainRatio, boolean stratified, double[] weightMult) {
    Frame[] res= new Frame[2];
    Vec[] trainTestWeights = stratified ? makeStratifiedWeights(fr.vec(response),trainRatio, weightMult) : makeWeights(fr.vec(response), trainRatio, weightMult);
    Vec[] vecs = new Vec[fr.numCols()+1];
    String[] names = new String[fr.numCols()+1];
    System.arraycopy(fr.names(),0,names,0,fr.names().length);
    System.arraycopy(fr.vecs(),0,vecs,0,fr.vecs().length);
    names[names.length-1]="weight";
    vecs[vecs.length-1] = trainTestWeights[0];
    res[0] = new Frame(Key.<Frame>make(),names.clone(),vecs.clone());  // reclone for safety
    DKV.put(res[0]);
    vecs = vecs.clone();
    vecs[vecs.length-1] = trainTestWeights[1];
    res[1] = new Frame(Key.<Frame>make(), names.clone(),vecs.clone()); // reclone for safety
    DKV.put(res[1]);
    return res;
  }

  public static Frame[] makeTrainTestFromWeight(Frame fr, Vec[] trainTestWeight) {
    Frame[] res= new Frame[2];
    Vec[] vecs = new Vec[fr.numCols()+1];
    String[] names = new String[fr.numCols()+1];
    System.arraycopy(fr.names(),0,names,0,fr.names().length);
    System.arraycopy(fr.vecs(),0,vecs,0,fr.vecs().length);
    names[names.length-1]="weight";
    vecs[vecs.length-1] = trainTestWeight[0];
    res[0] = new Frame(Key.<Frame>make(),names.clone(),vecs.clone());  // reclone for safety
    DKV.put(res[0]);
    vecs = vecs.clone();
    vecs[vecs.length-1] = trainTestWeight[1];
    res[1] = new Frame(Key.<Frame>make(), names.clone(),vecs.clone()); // reclone for safety
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
