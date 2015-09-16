package water.currents;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Random;

import static water.util.RandomUtils.getRNG;

public class ASTKFold extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "nfolds", "seed"}; }
  @Override public int nargs() { return 1+3; } // (kfold_column x nfolds seed)
  @Override String str() { return "kfold_column"; }

  public static Vec kfoldColumn(Vec v, final int nfolds, final long seed) {
    new MRTask() {
      @Override public void map(Chunk c) {
        long start = c.start();
        for( int i=0; i<c._len; ++i ) {
          int fold = Math.abs(getRNG(start + seed + i).nextInt()) % nfolds;
          c.set(i,fold);
        }
      }
    }.doAll(v);
    return v;
  }

  public static Vec moduloKfoldColumn(Vec v, final int nfolds) {
    new MRTask() {
      @Override public void map(Chunk c) {
        long start = c.start();
        for( int i=0; i<c._len; ++i)
          c.set(i, (int) ((start + i) % nfolds));
      }
    }.doAll(v);
    return v;
  }

  public static Vec stratifiedKFoldColumn(Vec y, final int nfolds, final long seed) {
    // for each class, generate a fold column (never materialized)
    // therefore, have a seed per class to be used by the map call
    if( !(y.isEnum() || (y.isNumeric() && y.isInt())) )
      throw new IllegalArgumentException("stratification only applies to integer and categorical columns. Got: " + y.get_type_str());
    final long[] classes = new Vec.CollectDomain().doAll(y).domain();
    final int nClass = y.isNumeric() ? classes.length : y.domain().length;
    final long[] seeds = new long[nClass]; // seed for each regular fold column (one per class)
    for( int i=0;i<nClass;++i)
      seeds[i] = getRNG(seed + i).nextLong();

    return new MRTask() {

      private int getFoldId(int absoluteRow, long seed) {
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
      //      Downside is this performs nfolds*nClass passes over each Chunk. For
      //      "reasonable" classification problems, this could be 100 passes per Chunk.
      @Override public void map(Chunk[] y) {
        int start = (int)y[0].start();
        for(int testFold=0; testFold < nfolds; ++testFold) {
          for(int classLabel = 0; classLabel < nClass; ++classLabel) {
            for(int row=0;row<y[0]._len;++row ) {
              if( y[0].at8(row) == (classes==null?classLabel:classes[classLabel]) ) {
                if( testFold == getFoldId(start+row,seeds[classLabel]) )
                  y[1].set(row,testFold);
              }
            }
          }
        }
      }
    }.doAll(new Frame(y,y.makeZero()))._fr.vec(1);
  }

  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Vec foldVec = stk.track(asts[1].exec(env)).getFrame().anyVec().makeZero();
    int nfolds = (int)asts[2].exec(env).getNum();
    long seed  = (long)asts[3].exec(env).getNum();
    return new ValFrame(new Frame(kfoldColumn(foldVec,nfolds,seed==-1?new Random().nextLong():seed)));
  }
}

class ASTModuloKFold extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "nfolds"}; }
  @Override public int nargs() { return 1+2; } // (modulo_kfold_column x nfolds)
  @Override String str() { return "modulo_kfold_column"; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Vec foldVec = stk.track(asts[1].exec(env)).getFrame().anyVec().makeZero();
    int nfolds = (int)asts[2].exec(env).getNum();
    return new ValFrame(new Frame(ASTKFold.moduloKfoldColumn(foldVec,nfolds)));
  }
}

class ASTStratifiedKFold extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "nfolds", "seed"}; }
  @Override public int nargs() { return 1+2; } // (stratified_kfold_column x nfolds seed)
  @Override String str() { return "stratified_kfold_column"; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Vec foldVec = stk.track(asts[1].exec(env)).getFrame().anyVec().makeZero();
    int nfolds = (int)asts[2].exec(env).getNum();
    long seed  = (long)asts[3].exec(env).getNum();
    return new ValFrame(new Frame(ASTKFold.stratifiedKFoldColumn(foldVec,nfolds,seed==-1?new Random().nextLong():seed)));
  }
}