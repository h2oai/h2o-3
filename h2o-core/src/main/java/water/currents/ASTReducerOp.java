package water.currents;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.*;
import water.fvec.*;
import water.util.ArrayUtils;

/** Subclasses take a Frame and produces a scalar.  NAs -> NAs */
abstract class ASTReducerOp extends ASTPrim {
  @Override int nargs() { return -1; }
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    // NOTE: no *initial* value needed for the reduction.  Instead, the
    // reduction op is used between pairs of actual values, and never against
    // the empty list.  NaN is returned if there are *no* values in the
    // reduction.
    double d = Double.NaN;
    for( int i=1; i<asts.length; i++ ) {
      Val val = asts[i].exec(env);
      double d2 = val.isFrame() ? new RedOp().doAll(stk.track(val).getFrame())._d : val.getNum();
      if( i==1 ) d = d2;
      else d = op(d,d2);
    }
    return new ValNum(d);
  }
  /** Override to express a basic math primitive */
  abstract double op( double l, double r );

  class RedOp extends MRTask<RedOp> {
    double _d;
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for( Chunk C : chks ) {
        if( !C.vec().isNumeric() ) throw new IllegalArgumentException("Numeric columns only");
        double sum = _d;
        for( int r = 0; r < rows; r++ )
          sum = op(sum, C.atd(r));
        _d = sum;
        if( Double.isNaN(sum) ) break; // Shortcut if the reduction is already NaN
      }
    }
    @Override public void reduce( RedOp s ) { _d = op(_d,s._d); }
  }

//  class NaRmRedOp extends MRTask<NaRmRedOp> {
//    double _d;
//    @Override public void map( Chunk chks[] ) {
//      int rows = chks[0]._len;
//      for( Chunk C : chks ) {
//        if( !C.vec().isNumeric() ) throw new IllegalArgumentException("Numeric columns only");
//        double sum = _d;
//        for( int r = 0; r < rows; r++ ) {
//          double d = C.atd(r);
//          if( !Double.isNaN(d) )
//            sum = op(sum, d);
//        }
//        _d = sum;
//        if( Double.isNaN(sum) ) break; // Shortcut if the reduction is already NaN
//      }
//    }
//    @Override public void reduce( NaRmRedOp s ) { _d = op(_d, s._d); }
//  }

  @Override double rowApply( double ds[] ) {
    double d = ds[0];
    for( int i=1; i<ds.length; i++ )
      d = op(d,ds[i]);
    return d;
  }
}

/** Optimization for the RollupStats: use them directly */
abstract class ASTRollupOp extends ASTReducerOp {
  abstract double rup( Vec vec );
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec[] vecs = fr.vecs();
    if( vecs.length==0 || vecs[0].naCnt() > 0 ) return new ValNum(Double.NaN);
    double d = rup(vecs[0]);
    for( int i=1; i<vecs.length; i++ ) {
      if( vecs[i].naCnt() > 0 ) return new ValNum(Double.NaN);
      d = op(d,rup(vecs[i]));
    }
    return new ValNum(d);
  }
}

class ASTSum  extends ASTRollupOp { String str() { return "sum" ; } double op( double l, double r ) { return          l+r ; } double rup( Vec vec ) { return vec.mean()*vec.length(); } }
class ASTMin  extends ASTRollupOp { String str() { return "min" ; } double op( double l, double r ) { return Math.min(l,r); } double rup( Vec vec ) { return vec.min(); } }
class ASTMax  extends ASTRollupOp { String str() { return "max" ; } double op( double l, double r ) { return Math.max(l,r); } double rup( Vec vec ) { return vec.max(); } }

class ASTMedian extends ASTPrim {
  @Override String str() { return "median"; }
  @Override int nargs() { return 1+2; }  // (median fr method)
  @Override ValNum apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    boolean narm = asts[2].exec(env).getNum()==1;
    if( !narm && (fr.anyVec().length()==0 || fr.anyVec().naCnt() > 0) ) return new ValNum(Double.NaN);
    // does linear interpolation for even sample sizes by default
    return new ValNum(median(fr,QuantileModel.CombineMethod.INTERPOLATE));
  }

  static double median(Frame fr, QuantileModel.CombineMethod combine_method) {
    if( fr.numCols() !=1 || !fr.anyVec().isNumeric() )
      throw new IllegalArgumentException("median only works on a single numeric column");
    // Frame needs a Key for Quantile, might not have one from currents
    Key tk=null;
    if( fr._key == null ) { DKV.put(tk=Key.make(), fr=new Frame(tk, fr.names(),fr.vecs())); }
    // Quantiles to get the median
    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    parms._probs = new double[]{0.5};
    parms._train = fr._key;
    parms._combine_method = combine_method;
    QuantileModel q = new Quantile(parms).trainModel().get();
    double median = q._output._quantiles[0][0];
    q.delete();
    if( tk!=null ) { DKV.remove(tk); }
    return median;
  }

  static double median(Vec v, QuantileModel.CombineMethod combine_method) {
    return median(new Frame(v),combine_method);
  }
}

class ASTMad extends ASTPrim {
  @Override int nargs() { return 1+3; } //(mad fr combine_method const)
  @Override String str() { return "h2o.mad"; }
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec[] vecs = fr.vecs();
    if( vecs.length==0 || vecs[0].naCnt() > 0 ) return new ValNum(Double.NaN);
    if( vecs.length > 1 ) throw new IllegalArgumentException("MAD expects a single numeric column");
    QuantileModel.CombineMethod cm = QuantileModel.CombineMethod.valueOf(asts[2].exec(env).getStr().toUpperCase());
    double constant = asts[3].exec(env).getNum();
    return new ValNum(mad(fr,cm,constant));
  }

  static double mad(Frame f, QuantileModel.CombineMethod cm, double constant) {
    // need Frames everywhere because of QuantileModel demanding a Frame...
    Key tk=null;
    if( f._key == null ) { DKV.put(tk = Key.make(), f = new Frame(tk, f.names(), f.vecs())); }
    final double median = ASTMedian.median(f,cm);
    Frame abs_dev = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        for(int i=0;i<c._len;++i)
          nc.addNum(Math.abs(c.at8(i)-median));
      }
    }.doAll(1, f).outputFrame();
    if( abs_dev._key == null ) { DKV.put(tk=Key.make(), abs_dev=new Frame(tk, abs_dev.names(),abs_dev.vecs())); }
    double mad = ASTMedian.median(abs_dev,cm);
    DKV.remove(f._key); // drp mapping, keep vec
    DKV.remove(abs_dev._key);
    return constant*mad;
  }
}

// Debugging primitive; takes either a scalar or a vector.  TRUE if all values are 1.
class ASTAll extends ASTPrim { 
  @Override String str() { return "all" ; }
  @Override int nargs() { return 1+1; }
  @Override ValStr apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val val = stk.track(asts[1].exec(env));
    if( val.isNum() ) return new ValStr(val.getNum() == 0 ? "FALSE" : "TRUE");
    for( Vec vec : val.getFrame().vecs() )
      if( vec.min() != 1 || vec.max() != 1 )
        return new ValStr("FALSE");
    return new ValStr("TRUE");
  }
}

class ASTAny extends ASTPrim {
  @Override int nargs() { return 1+1; } // (any x)
  @Override String str() { return "any"; }
  @Override ValStr apply( Env env, Env.StackHelp stk, AST asts[] ) {
    boolean any;
    Val val = stk.track(asts[1].exec(env));
    if( val.isNum() ) any = val.getNum()!=0;
    else {
      Frame fr = val.getFrame();
      for(int i=0;i<fr.numCols();i++) {
        Vec v = fr.vec(i);
        if( !v.isInt() ) throw new IllegalArgumentException("all columns must be a columns of 1s and 0s.");
        if( v.isConst() )
          if( !(v.min() == 0 || v.min() == 1) ) throw new IllegalArgumentException("columns must be a columns of 1s and 0s");
          else
          if( v.min() != 0 && v.max() != 1 ) throw new IllegalArgumentException("columns must be a columns of 1s and 0s");
        if( v.naCnt() > 0 ) return new ValStr("TRUE");
      }
      any = new AnyTask().doAll(fr).any;
    }
    return new ValStr(any?"TRUE":"FALSE");
  }

  private static class AnyTask extends MRTask<AnyTask> {
    private boolean any=false;
    @Override public void map(Chunk[] c) {
      int j=0;
      for (Chunk aC : c) {
        for( j=0; j<c[0]._len;++j ) {
          if( !any ) {
            if(aC.isNA(j)) {
              any = false;
              break;
            }
            any |= aC.atd(j) == 1;
          } else break;
        }
        if( j!=c[0]._len) break;
      }
    }
    @Override public void reduce(AnyTask t) { any &= t.any; }
  }
}

class ASTAnyNA extends ASTPrim {
  @Override int nargs() { return 1+1; } // (any.na x)
  @Override String str() { return "any.na"; }
  @Override ValStr apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String res = "FALSE";
    for (int i = 0; i < fr.vecs().length; ++i)
      if (fr.vecs()[i].naCnt() > 0) { res = "TRUE"; break; }
    return new ValStr(res);
  }
}


// ----------------------------------------------------------------------------
/** Subclasses take a Frame and produces a scalar.  NAs are dropped */
//abstract class ASTNARedOp extends ASTReducerOp {
//  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
//    Frame fr = stk.track(asts[1].exec(env)).getFrame();
//    return new ValNum(new NaRmRedOp().doAll(fr)._d);
//  }
//}

/** Optimization for the RollupStats: use them directly */
abstract class ASTNARollupOp extends ASTRollupOp {
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec[] vecs = fr.vecs();
    if( vecs.length==0 ) return new ValNum(Double.NaN);
    double d = rup(vecs[0]);
    for( int i=1; i<vecs.length; i++ )
      d = op(d,rup(vecs[i]));
    return new ValNum(d);
  }
}

class ASTSumNA extends ASTNARollupOp { String str() { return "sumNA" ; } double op( double l, double r ) { return          l+r ; } double rup( Vec vec ) { return vec.mean()*vec.length(); } }
class ASTMinNA extends ASTNARollupOp { String str() { return "minNA" ; } double op( double l, double r ) { return Math.min(l,r); } double rup( Vec vec ) { return vec.min(); } }
class ASTMaxNA extends ASTNARollupOp { String str() { return "maxNA" ; } double op( double l, double r ) { return Math.max(l,r); } double rup( Vec vec ) { return vec.max(); } }

// ----------------------------------------------------------------------------
// Unlike the other reducer ops, this one only works on a single column
class ASTMeanNA extends ASTPrim {
  @Override int nargs() { return 1+1; }
  @Override String str() { return "meanNA"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols()==1 ) {
      if( !fr.anyVec().isNumeric() ) return new ValNum(Double.NaN);
      return new ValNum(fr.anyVec().mean());
    }
    Futures fs = new Futures();
    Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
    AppendableVec v = new AppendableVec(key);
    NewChunk chunk = new NewChunk(v, 0);
    for( int i=0;i<fr.numCols();++i ) chunk.addNum(fr.vec(i).isNumeric()?fr.vec(i).mean():Double.NaN);
    chunk.close(0,fs);
    Vec vec = v.close(fs);
    fs.blockForPending();
    Frame fr2 = new Frame(Key.make(), new String[]{"C1"}, new Vec[]{vec});
    DKV.put(fr2);
    return new ValFrame(fr2);
  }
}

class ASTMean extends ASTPrim {
  @Override String str() { return "mean"; }
  @Override int nargs() { return 1+1; }
  @Override Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols() == 1) {
      if( !fr.anyVec().isNumeric() ) return new ValNum(Double.NaN);
      if( fr.anyVec().length()==0 || fr.anyVec().naCnt() >0 ) return new ValNum(Double.NaN);
      return new ValNum(fr.anyVec().mean());
    }
    Futures fs = new Futures();
    Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
    AppendableVec v = new AppendableVec(key);
    NewChunk chunk = new NewChunk(v, 0);
    for( int i=0;i<fr.numCols();++i ) chunk.addNum((fr.vec(i).isNumeric()||fr.vec(i).naCnt()>0)?fr.vec(i).mean():Double.NaN);
    chunk.close(0,fs);
    Vec vec = v.close(fs);
    fs.blockForPending();
    Frame fr2 = new Frame(Key.make(), new String[]{"C1"}, new Vec[]{vec});
    DKV.put(fr2);
    return new ValFrame(fr2);
  }
}

class ASTSdev extends ASTPrim { // TODO: allow for multiple columns, package result into Frame
  @Override int nargs() { return 1+1; }
  @Override String str() { return "sd"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() == 1) {
      if (!fr.anyVec().isNumeric()) return new ValNum(Double.NaN);
      return new ValNum(fr.anyVec().sigma());
    }
    Futures fs = new Futures();
    Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
    AppendableVec v = new AppendableVec(key);
    NewChunk chunk = new NewChunk(v, 0);
    for( int i=0;i<fr.numCols();++i ) chunk.addNum(fr.vec(i).isNumeric()?fr.vec(i).sigma():Double.NaN);
    chunk.close(0,fs);
    Vec vec = v.close(fs);
    fs.blockForPending();
    Frame fr2 = new Frame(Key.make(), new String[]{"C1"}, new Vec[]{vec});
    DKV.put(fr2);
    return new ValFrame(fr2);
  }
}


class ASTProd extends ASTPrim {
  @Override int nargs() { return 1+1; } // (prod x)
  @Override String str(){ return "prod";}
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    for(Vec v : fr.vecs()) if (v.isEnum() || v.isUUID() || v.isString()) throw new IllegalArgumentException("`"+str()+"`" + " only defined on a data frame with all numeric variables");
    double prod=new RedProd().doAll(fr)._d;
    return new ValNum(prod);
  }

  private static class RedProd extends MRTask<RedProd> {
    double _d;
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
        double prod=1.;
        for (int r = 0; r < rows; r++)
          prod *= C.atd(r);
        _d = prod;
        if( Double.isNaN(prod) ) break;
      }
    }
    @Override public void reduce( RedProd s ) { _d *= s._d; }
  }
}

class ASTProdNA extends ASTPrim {
  @Override int nargs() { return 1+1; } // (prod x)
  @Override String str(){ return "prod.na";}
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    for(Vec v : fr.vecs()) if (v.isEnum() || v.isUUID() || v.isString()) throw new IllegalArgumentException("`"+str()+"`" + " only defined on a data frame with all numeric variables");
    double prod=new RedProd().doAll(fr)._d;
    return new ValNum(prod);
  }

  private static class RedProd extends MRTask<RedProd> {
    double _d;
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
        double prod=1.;
        for (int r = 0; r < rows; r++) {
          if( C.isNA(r) ) continue;
          prod *= C.atd(r);
        }
        _d = prod;
        if( Double.isNaN(prod) ) break;
      }
    }
    @Override public void reduce( RedProd s ) { _d += s._d; }
  }
}

abstract class ASTCumu extends ASTPrim {
  @Override int nargs() { return 1+1; } // (cumu x)
  @Override String str() { throw H2O.unimpl(); }
  abstract double op(double l, double r);
  abstract double init();
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();

    if( f.numCols()!=1 ) throw new IllegalArgumentException("Must give a single numeric column.");
    if( !f.anyVec().isNumeric() ) throw new IllegalArgumentException("Column must be numeric.");

    CumuTask t = new CumuTask(f.anyVec().nChunks(),init());
    t.doAll(1,f.anyVec());
    final double[] chkCumu = t._chkCumu;
    Vec cumuVec = t.outputFrame().anyVec();
    new MRTask() {
      @Override public void map(Chunk c) {
        if( c.cidx()!=0 ) {
          double d=chkCumu[c.cidx()-1];
          for(int i=0;i<c._len;++i)
            c.set(i, op(c.atd(i), d));
        }
      }
    }.doAll(cumuVec);
    return new ValFrame(new Frame(cumuVec));
  }

  protected class CumuTask extends MRTask<CumuTask> {
    final int _nchks;   // IN
    final double _init; // IN
    double[] _chkCumu;  // OUT, accumulation over each chunk

    CumuTask(int nchks, double init) { _nchks = nchks; _init=init; }
    @Override public void setupLocal() { _chkCumu = new double[_nchks]; }
    @Override public void map(Chunk c, NewChunk nc) {
      double acc=_init;
      for(int i=0;i<c._len;++i)
        nc.addNum(acc=op(acc,c.atd(i)));
      _chkCumu[c.cidx()]=acc;
    }
    @Override public void reduce(CumuTask t) { if( _chkCumu != t._chkCumu ) ArrayUtils.add(_chkCumu, t._chkCumu); }
    @Override public void postGlobal() {
      for(int i=1;i<_chkCumu.length;++i) _chkCumu[i] = op(_chkCumu[i],_chkCumu[i-1]);
    }
  }
}

class ASTCumSum extends ASTCumu {
  @Override int nargs() { return 1+1; } // (cumsum x)
  @Override String str() { return "cumsum"; }
  @Override double op(double l, double r) { return l+r; }
  @Override double init() { return 0; }
}

class ASTCumProd extends ASTCumu {
  @Override int nargs() { return 1+1; } // (cumprod x)
  @Override String str() { return "cumprod"; }
  @Override double op(double l, double r) { return l*r; }
  @Override double init() { return 1; }
}

class ASTCumMin extends ASTCumu {
  @Override int nargs() { return 1+1; }
  @Override String str() { return "cummin"; }
  @Override double op(double l, double r) { return Math.min(l, r); }
  @Override double init() { return Double.MAX_VALUE; }
}

class ASTCumMax extends ASTCumu {
  @Override int nargs() { return 1+1; }
  @Override String str() { return "cummax"; }
  @Override double op(double l, double r) { return Math.max(l,r); }
  @Override double init() { return -Double.MAX_VALUE; }
}
