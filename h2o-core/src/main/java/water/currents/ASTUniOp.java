package water.currents;

import water.Futures;
import water.H2O;
import water.Key;
import water.MRTask;
import water.fvec.*;

import java.util.Arrays;
import java.util.Random;

/**
 * Subclasses auto-widen between scalars and Frames, and have exactly one argument
 */
abstract class ASTUniOp extends ASTPrim {
  @Override int nargs() { return 1+1; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val val = stk.track(asts[1].exec(env));
    switch( val.type() ) {
    case Val.NUM: return new ValNum(op(val.getNum()));
    case Val.FRM: 
      Frame fr = val.getFrame();
      return new ValFrame(new MRTask() {
          @Override public void map( Chunk cs[], NewChunk ncs[] ) {
            for( int col=0; col<cs.length; col++ ) {
              Chunk c = cs[col];
              NewChunk nc = ncs[col];
              for( int i=0; i<c._len; i++ )
                nc.addNum(op(c.atd(i)));
            }
          }
        }.doAll(fr.numCols(),fr).outputFrame());
    case Val.STR: throw H2O.unimpl();
    default: throw H2O.fail();
    }
  }
  abstract double op( double d );
}

class ASTACos  extends ASTUniOp { String str() { return "acos" ; } double op(double d) { return Math.acos (d); } }
class ASTAbs   extends ASTUniOp { String str() { return "abs"  ; } double op(double d) { return Math.abs  (d); } }
class ASTCeiling extends ASTUniOp{String str() { return "ceiling";}double op(double d) { return Math.ceil (d); } }
class ASTCos   extends ASTUniOp { String str() { return "cos"  ; } double op(double d) { return Math.cos  (d); } }
class ASTCosh  extends ASTUniOp { String str() { return "cosh" ; } double op(double d) { return Math.cosh (d); } }
class ASTExp   extends ASTUniOp { String str() { return "exp"  ; } double op(double d) { return Math.exp  (d); } }
class ASTFloor extends ASTUniOp { String str() { return "floor"; } double op(double d) { return Math.floor(d); } }
class ASTLog   extends ASTUniOp { String str() { return "log"  ; } double op(double d) { return Math.log  (d); } }
class ASTNot   extends ASTUniOp { String str() { return "!!"   ; } double op(double d) { return d==0?1:0; } }
class ASTSin   extends ASTUniOp { String str() { return "sin"  ; } double op(double d) { return Math.sin  (d); } }
class ASTSqrt  extends ASTUniOp { String str() { return "sqrt" ; } double op(double d) { return Math.sqrt (d); } }
class ASTTan   extends ASTUniOp { String str() { return "tan"  ; } double op(double d) { return Math.tan  (d); } }
class ASTTanh  extends ASTUniOp { String str() { return "tanh" ; } double op(double d) { return Math.tanh (d); } }
class ASTTrunc extends ASTUniOp { String str() { return "trunc"; } double op(double d) { return d>=0?Math.floor(d):Math.ceil(d);}}

// Split out in it's own function, instead of Yet Another UniOp, because it
// needs a "is.NA" check instead of just using the Double.isNaN hack... because
// it works on UUID and String columns.
class ASTIsNA  extends ASTPrim {
  @Override String str() { return "is.na"; } 
  @Override int nargs() { return 1+1; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val val = stk.track(asts[1].exec(env));
    switch( val.type() ) {
    case Val.NUM: return new ValNum(op(val.getNum()));
    case Val.FRM: 
      Frame fr = val.getFrame();
      return new ValFrame(new MRTask() {
          @Override public void map( Chunk cs[], NewChunk ncs[] ) {
            for( int col=0; col<cs.length; col++ ) {
              Chunk c = cs[col];
              NewChunk nc = ncs[col];
              for( int i=0; i<c._len; i++ )
                nc.addNum(c.isNA(i) ? 1 : 0);
            }
          }
        }.doAll(fr.numCols(),fr).outputFrame());
    case Val.STR: return new ValNum(val.getStr()==null ? 1 : 0);
    default: throw H2O.fail();
    }
  }
  double op(double d) { return Double.isNaN(d)?1:0; } 
}

class ASTRunif extends ASTPrim {
  @Override int nargs() { return 1+2; } // (h2o.runif frame seed)
  @Override String str() { return "h2o.runif"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr  = stk.track(asts[1].exec(env)).getFrame();
    long seed = (long)asts[2].exec(env).getNum();
    if( seed == -1 ) seed = new Random().nextLong();
    return new ValFrame(new Frame(new String[]{"rnd"}, new Vec[]{fr.anyVec().makeRand(seed)}));
  }
}

class ASTNrow extends ASTPrim {
  @Override int nargs() { return 1+1; }
  @Override String str() { return "nrow"; }
  @Override Val apply(Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    return new ValNum(fr.numRows());
  }
}


class ASTNLevels extends ASTPrim {
  @Override int nargs() { return 1+1; } // (nlevels x)
  @Override String str() { return "nlevels"; }
  @Override ValNum apply(Env env, Env.StackHelp stk, AST asts[] ) {
    int nlevels;
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() == 1) {
      Vec v = fr.anyVec();
      nlevels = v.isEnum()?v.domain().length:0;
      return new ValNum(nlevels);
    } else throw new IllegalArgumentException("nlevels applies to a single column. Got: " + fr.numCols());
  }
}

class ASTLevels extends ASTPrim {
  @Override int nargs() { return 1+1; } // (levels x)
  @Override String str() { return "levels"; }
  @Override ValFrame apply(Env env, Env.StackHelp stk, AST asts[] ) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    Futures fs = new Futures();
    Key[] keys = Vec.VectorGroup.VG_LEN1.addVecs(f.numCols());
    Vec[] vecs = new Vec[keys.length];

    // compute the longest vec... that's the one with the most domain levels
    int max=0;
    for(int i=0;i<f.numCols();++i )
      if( f.vec(i).isEnum() )
        if( max < f.vec(i).domain().length ) max = f.vec(i).domain().length;

    for( int i=0;i<f.numCols();++i ) {
      AppendableVec v = new AppendableVec(keys[i]);
      NewChunk nc = new NewChunk(v,0);
      String[] dom = f.vec(i).domain();
      int numToPad = dom==null?max:max-dom.length;
      if( dom != null )
        for(int j=0;j<dom.length;++j) nc.addNum(j);
      for(int j=0;j<numToPad;++j)     nc.addNA();
      nc.close(0,fs);
      vecs[i] = v.close(fs);
      vecs[i].setDomain(dom);
    }
    fs.blockForPending();
    Frame fr2 = new Frame(vecs);
    return new ValFrame(fr2);
  }
}

class ASTSetLevel extends ASTPrim {
  @Override int nargs() { return 1+2; } // (setLevel x level)
  @Override String str() { return "setLevel"; }
  @Override ValFrame apply(Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1) throw new IllegalArgumentException("`setLevel` works on a single column at a time.");
    String[] doms = fr.anyVec().domain().clone();
    if( doms == null )
      throw new IllegalArgumentException("Cannot set the level on a non-factor column!");

    String lvl = asts[2].exec(env).getStr();

    final int idx = Arrays.asList(doms).indexOf(lvl);
    if (idx == -1) throw new IllegalArgumentException("Did not find level `" + lvl + "` in the column.");


    // COW semantics
    Frame fr2 = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        for (int i=0;i<c._len;++i)
          nc.addNum(idx);
      }
    }.doAll(1, fr.anyVec()).outputFrame(null, fr.names(), fr.domains());
    return new ValFrame(fr2);
  }
}


class ASTSetDomain extends ASTPrim {
  @Override int nargs() { return 1+2;} // (setDomain x [list of strings])
  @Override String str() { return "setDomain"; }
  @Override ValFrame apply(Env env, Env.StackHelp stk, AST asts[] ) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    if( f.numCols()!=1 ) throw new IllegalArgumentException("Must be a single column. Got: " + f.numCols() + " columns.");
    Vec v = f.anyVec().makeCopy();
    if( !v.isEnum() ) throw new IllegalArgumentException("Vector must be a factor column. Got: "+v.get_type_str());
    String[] domains = ((ASTStrList)asts[2])._strs;
    if( domains!=null && domains.length != v.domain().length)
      throw new IllegalArgumentException("Number of replacement factors must equal current number of levels. Current number of levels: " + v.domain().length + " != " + domains.length);
    v.setDomain(domains);
    return new ValFrame(new Frame(v));
  }
}