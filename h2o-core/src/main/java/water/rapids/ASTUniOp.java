package water.rapids;

import hex.DMatrix;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;
import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.MathUtils;
import water.util.VecUtils;

import java.util.Arrays;
import java.util.Random;

import static water.util.RandomUtils.getRNG;

/**
 * Subclasses auto-widen between scalars and Frames, and have exactly one argument
 */
public abstract class ASTUniOp extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Val val = stk.track(asts[1].exec(env));
    switch( val.type() ) {
    case Val.NUM: return new ValNum(op(val.getNum()));
    case Val.FRM:
      Frame fr = val.getFrame();
      return new ValFrame(new MRTask() {
          @Override public void map(Chunks cs, Chunks.AppendableChunks ncs) {
            for( int col=0; col<cs.numCols(); col++ ) {
              for( int i=0; i<cs.numRows(); i++ )
                ncs.addNum(col,op(cs.atd(i,col)));
            }
          }
        }.doAll(fr.numCols(), Vec.T_NUM, fr.vecs()).outputFrame());
      case Val.ROW:
        ValRow v = (ValRow)val;
        double[] ds = new double[v._ds.length];
        for(int i=0;i<ds.length;++i)
          ds[i] = op(v._ds[i]);
        String[] names = v._names.clone();
        return new ValRow(ds,names);
    default: throw H2O.unimpl("unop unimpl: " + val.getClass());
    }
  }
  abstract double op( double d );
}

class ASTCeiling extends ASTUniOp{ public String str() { return "ceiling";}double op(double d) { return Math.ceil (d); } }
class ASTFloor extends ASTUniOp { public String str() { return "floor"; } double op(double d) { return Math.floor(d); } }
class ASTNot   extends ASTUniOp { public String str() { return "!!"   ; } double op(double d) { return Double.isNaN(d)?Double.NaN:d==0?1:0; } }
class ASTTrunc extends ASTUniOp { public String str() { return "trunc"; } double op(double d) { return d>=0?Math.floor(d):Math.ceil(d);}}
class ASTCos  extends ASTUniOp { public String str(){ return "cos";  } double op(double d) { return Math.cos(d);}}
class ASTSin  extends ASTUniOp { public String str(){ return "sin";  } double op(double d) { return Math.sin(d);}}
class ASTTan  extends ASTUniOp { public String str(){ return "tan";  } double op(double d) { return Math.tan(d);}}
class ASTACos extends ASTUniOp { public String str(){ return "acos"; } double op(double d) { return Math.acos(d);}}
class ASTASin extends ASTUniOp { public String str(){ return "asin"; } double op(double d) { return Math.asin(d);}}
class ASTATan extends ASTUniOp { public String str(){ return "atan"; } double op(double d) { return Math.atan(d);}}
class ASTCosh extends ASTUniOp { public String str(){ return "cosh"; } double op(double d) { return Math.cosh(d);}}
class ASTSinh extends ASTUniOp { public String str(){ return "sinh"; } double op(double d) { return Math.sinh(d);}}
class ASTTanh extends ASTUniOp { public String str(){ return "tanh"; } double op(double d) { return Math.tanh(d);}}
class ASTACosh extends ASTUniOp { public String str(){ return "acosh"; } double op(double d) { return FastMath.acosh(d);}}
class ASTASinh extends ASTUniOp { public String str(){ return "asinh"; } double op(double d) { return FastMath.asinh(d);}}
class ASTATanh extends ASTUniOp { public String str(){ return "atanh"; } double op(double d) { return FastMath.atanh(d);}}
class ASTCosPi extends ASTUniOp { public String str(){ return "cospi"; } double op(double d) { return Math.cos(Math.PI*d);}}
class ASTSinPi extends ASTUniOp { public String str(){ return "sinpi"; } double op(double d) { return Math.sin(Math.PI*d);}}
class ASTTanPi extends ASTUniOp { public String str(){ return "tanpi"; } double op(double d) { return Math.tan(Math.PI*d);}}
class ASTAbs  extends ASTUniOp { public String str(){ return "abs";  } double op(double d) { return Math.abs(d);}}
class ASTSgn  extends ASTUniOp { public String str(){ return "sign" ; } double op(double d) { return Math.signum(d);}}
class ASTSqrt extends ASTUniOp { public String str(){ return "sqrt"; } double op(double d) { return Math.sqrt(d);}}
class ASTLog  extends ASTUniOp { public String str(){ return "log";  } double op(double d) { return Math.log(d);}}
class ASTLog10  extends ASTUniOp { public String str(){ return "log10";  } double op(double d) { return Math.log10(d);}}
class ASTLog2  extends ASTUniOp { public String str(){ return "log2";  } double op(double d) { return Math.log(d)/Math.log(2);}}
class ASTLog1p  extends ASTUniOp { public String str(){ return "log1p";  } double op(double d) { return Math.log1p(d);}}
class ASTExp  extends ASTUniOp { public String str(){ return "exp";  } double op(double d) { return Math.exp(d);}}
class ASTExpm1  extends ASTUniOp { public String str(){ return "expm1";  } double op(double d) { return Math.expm1(d);}}
class ASTGamma  extends ASTUniOp { public String str(){ return "gamma";  } double op(double d) {  return Gamma.gamma(d);}}
class ASTLGamma extends ASTUniOp { public String str(){ return "lgamma"; } double op(double d) { return Gamma.logGamma(d);}}
class ASTDiGamma  extends ASTUniOp { public String str(){ return "digamma";  } double op(double d) {  return Double.isNaN(d)?Double.NaN:Gamma.digamma(d);}}
class ASTTriGamma  extends ASTUniOp { public String str(){ return "trigamma";  } double op(double d) {  return Double.isNaN(d)?Double.NaN:Gamma.trigamma(d);}}
class ASTNoOp extends ASTUniOp { public String str(){ return "none"; } double op(double d) { return d; }}

// Split out in it's own function, instead of Yet Another UniOp, because it
// needs a "is.NA" check instead of just using the Double.isNaN hack... because
// it works on UUID and String columns.
class ASTIsNA  extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override
  public String str() { return "is.na"; }
  @Override int nargs() { return 1+1; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Val val = stk.track(asts[1].exec(env));
    switch( val.type() ) {
    case Val.NUM: return new ValNum(op(val.getNum()));
    case Val.FRM:
      Frame fr = val.getFrame();
      return new ValFrame(new MRTask() {
          @Override public void map(Chunks cs, Chunks.AppendableChunks ncs) {
            for( int col=0; col<cs.numCols(); col++ ) {
              for( int i=0; i<cs.numRows(); i++ )
                ncs.addNum(col,cs.isNA(i,col) ? 1 : 0);
            }
          }
        }.doAll(fr.numCols(), Vec.T_NUM, fr.vecs()).outputFrame());
    case Val.STR: return new ValNum(val.getStr()==null ? 1 : 0);
    default: throw H2O.unimpl("is.na unimpl: " + val.getClass());
    }
  }
  double op(double d) { return Double.isNaN(d)?1:0; }
}

/**
 * Remove rows with NAs from the H2OFrame
 * Note: Current implementation is NOT in place replacement
 */
class ASTNAOmit extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override
  public String str() { return "na.omit"; }
  @Override int nargs() { return 1+1; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame fr2 = new MRTask() {
      BufferedString _str;
      private void copyRow(int row, Chunks cs, Chunks.AppendableChunks ncs) {
        for(int i=0;i<cs.numCols();++i) {
          if( _vecs.type(i) == Vec.T_STR ) ncs.addStr(i,cs.atStr(_str,row,i));
          else if( _vecs.type(i) == Vec.T_UUID ) ncs.addUUID(i,cs.at16l(row,i),cs.at16h(row,i));
          else ncs.addNum(i,cs.atd(row,i));
        }
      }
      @Override public void map(Chunks cs, Chunks.AppendableChunks ncs) {
        _str = new BufferedString();
        int col;
        for(int row=0;row<cs.numRows();++row) {
          for( col = 0; col < cs.numCols(); ++col)
            if( cs.isNA(row,col) ) break;
          if( col==cs.numCols() ) copyRow(row,cs,ncs);
        }
      }
    }.doAll(fr.vecs().types(),fr.vecs()).outputFrame(fr._names,fr.vecs().domains());
    return new ValFrame(fr2);
  }
}

class ASTRunif extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "seed"}; }
  @Override int nargs() { return 1+2; } // (h2o.runif frame seed)
  @Override
  public String str() { return "h2o.runif"; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr  = stk.track(asts[1].exec(env)).getFrame();
    long seed = (long)asts[2].exec(env).getNum();
    if( seed == -1 ) seed = new Random().nextLong();
    return new ValFrame(new Frame(new String[]{"rnd"}, fr.vecs().makeRand(seed)));
  }
}

class ASTStratifiedSplit extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "test_frac", "seed"}; }
  @Override int nargs() { return 1+3; } // (h2o.random_stratified_split y test_frac seed)
  @Override
  public String str() { return "h2o.random_stratified_split"; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols() != 1 ) throw new IllegalArgumentException("Must give a single column to stratify against. Got: " + fr.numCols() + " columns.");
    VecAry y = fr.vecs();
    if( !(y.isCategorical(0) || (y.isNumeric(0) && y.isInt(0))) )
      throw new IllegalArgumentException("stratification only applies to integer and categorical columns. Got: " + y.typesStr());
    final double testFrac = asts[2].exec(env).getNum();
    long seed = (long)asts[3].exec(env).getNum();
    seed = seed == -1 ? new Random().nextLong() : seed;
    final long[] classes = new VecUtils.CollectDomain().doAll(y).domain();
    final int nClass = y.isNumeric(0) ? classes.length : y.domain(0).length;
    final long[] seeds = new long[nClass]; // seed for each regular fold column (one per class)
    for( int i=0;i<nClass;++i)
      seeds[i] = getRNG(seed + i).nextLong();
    String[] dom = new String[]{"train","test"};
    return new ValFrame(new MRTask() {
      private boolean isTest(int row, long seed) { return getRNG(row+seed).nextDouble() <= testFrac; }
      @Override public void map(Chunks y, Chunks.AppendableChunks ss) { // 0-> train, 1-> test
        int start = (int)y.start();
        for(int classLabel=0; classLabel<nClass; ++classLabel) {
          for(int row=0;row<y.numRows();++row) {
            if( y.at8(row,0) == (classes==null?classLabel:classes[classLabel]) ) {
              if( isTest(start+row,seeds[classLabel]) ) ss.addNum(0,1,0);
              else                                      ss.addNum(0,0,0);
            }
          }
        }
      }
    }.doAll(1, Vec.T_NUM, y).outputFrame(null,new String[]{"test_train_split"}, new String[][]{dom} ));
  }
}

class ASTNcol extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; }
  @Override
  public String str() { return "ncol"; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    return new ValNum(fr.numCols());
  }
}

class ASTNrow extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; }
  @Override
  public String str() { return "nrow"; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    return new ValNum(fr.numRows());
  }
}

class ASTNLevels extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (nlevels x)
  @Override
  public String str() { return "nlevels"; }
  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AST asts[]) {
    int nlevels;
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() == 1) {
      VecAry v = fr.vecs();
      nlevels = v.isCategorical(0)?v.domain(0).length:0;
      return new ValNum(nlevels);
    } else throw new IllegalArgumentException("nlevels applies to a single column. Got: " + fr.numCols());
  }
}

class ASTLevels extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (levels x)
  @Override
  public String str() { return "levels"; }
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    Futures fs = new Futures();
    Key key = Vec.VectorGroup.VG_LEN1.addVec();

    // compute the longest vec... that's the one with the most domain levels
    int max=0;
    VecAry vecs = f.vecs();
    for(int i=0;i<f.numCols();++i )
      if( vecs.isCategorical(i) )
        if( max < vecs.domain(i).length ) max = vecs.domain(i).length;

    byte [] types = new byte[f.numCols()];
    Arrays.fill(types,Vec.T_NUM);
    AppendableVec av = new AppendableVec(key,types);
    Chunks.AppendableChunks acs = av.chunkForChunkIdx(0);
    for( int i=0;i<f.numCols();++i ) {
      NewChunk nc = acs.getChunk(i);
      String[] dom = vecs.domain(i);
      int numToPad = dom==null?max:max-dom.length;
      if( dom != null )
        for(int j=0;j<dom.length;++j) nc.addNum(j);
      for(int j=0;j<numToPad;++j)     nc.addNA();
    }
    Vec newVec = av.closeVec(fs,vecs.domains());
    acs.close(fs);
    fs.blockForPending();
    Frame fr2 = new Frame((Key)null,new VecAry(newVec));
    return new ValFrame(fr2);
  }
}

class ASTSetLevel extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "level"}; }
  @Override int nargs() { return 1+2; } // (setLevel x level)
  @Override
  public String str() { return "setLevel"; }
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1) throw new IllegalArgumentException("`setLevel` works on a single column at a time.");
    String[] doms = fr.vecs().domain(0).clone();
    if( doms == null )
      throw new IllegalArgumentException("Cannot set the level on a non-factor column!");
    String lvl = asts[2].exec(env).getStr();
    final int idx = Arrays.asList(doms).indexOf(lvl);
    if (idx == -1) throw new IllegalArgumentException("Did not find level `" + lvl + "` in the column.");
    // COW semantics
    Frame fr2 = new MRTask() {
      @Override public void map(Chunks c, Chunks.AppendableChunks nc) {
        for (int i=0;i<c.numRows();++i)
          nc.addNum(0,idx);
      }
    }.doAll(new byte[]{Vec.T_NUM}, fr.vecs()).outputFrame(null, fr._names, fr.vecs().domains());
    return new ValFrame(fr2);
  }
}

class ASTReLevel extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "level"}; }
  @Override int nargs() { return 1+2; } // (setLevel x level)
  @Override
  public String str() { return "relevel"; }
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1) throw new IllegalArgumentException("`setLevel` works on a single column at a time.");
    String[] doms = fr.vecs().domain(0).clone();
    if( doms == null )
      throw new IllegalArgumentException("Cannot set the level on a non-factor column!");
    String lvl = asts[2].exec(env).getStr();

    final int idx = Arrays.asList(doms).indexOf(lvl);
    if (idx == -1) throw new IllegalArgumentException("Did not find level `" + lvl + "` in the column.");
    if(idx == 0) return new ValFrame(new Frame(null,fr._names,fr.vecs().makeCopy(fr.vecs().domains())));
    String [] srcDom = fr.vecs().domain(0);
    final String [] dom = new String[srcDom.length];
    dom[0] = srcDom[idx];
    int j = 1;
    for(int i = 0; i < srcDom.length; ++i)
      if(i != idx)  dom[j++] = srcDom[i];
    return new ValFrame(new MRTask(){
      @Override public void map(Chunks c, Chunks.AppendableChunks nc) {
        int [] vals = new int[c.numRows()];
        c.getIntegers(0,vals,-1);
        for(int i = 0; i < vals.length; ++i)
          if(vals[i] == -1) nc.addNA(0);
          else if(vals[i] == idx)
            nc.addNum(0,0);
          else
            nc.addNum(0,vals[i]+(vals[i] < idx?1:0));
      }
    }.doAll(1,Vec.T_CAT,fr.vecs()).outputFrame(fr._names,new String[][]{dom}));
  }
}


class ASTSetDomain extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "newDomains"}; }
  @Override int nargs() { return 1+2;} // (setDomain x [list of strings])
  @Override
  public String str() { return "setDomain"; }
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    String[] domains = ((ASTStrList)asts[2])._strs;
    if( f.numCols()!=1 ) throw new IllegalArgumentException("Must be a single column. Got: " + f.numCols() + " columns.");
    VecAry v = f.vecs();
    if( !v.isCategorical(0) ) throw new IllegalArgumentException("Vector must be a factor column. Got: "+v.typesStr());
    if( domains!=null && domains.length != v.domain(0).length) {
      // in this case we want to recollect the domain and check that number of levels matches _domains
      VecUtils.CollectDomainFast t = new VecUtils.CollectDomainFast();
      t.doAll(v);
      final long[] dom = t.domain()[0];
      if( dom.length != domains.length)
        throw new IllegalArgumentException("Number of replacement factors must equal current number of levels. Current number of levels: " + dom.length + " != " + domains.length);
      new MRTask() {
        @Override public void map(Chunks c) {
          for(int i=0;i<c.numRows();++i) {
            if( !c.isNA(i,0) ) {
              long num = Arrays.binarySearch(dom, c.at4(i,0));
              if( num < 0 ) throw new IllegalArgumentException("Could not find the categorical value!");
              c.set(i,0,num);
            }
          }
        }
      }.doAll(v);
    }
    v.setDomain(0,domains);
    return new ValFrame(f);
  }
}

class ASTTranspose extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (t X)
  @Override
  public String str() { return "t"; }
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    return new ValFrame(DMatrix.transpose(f));
  }
}

class ASTMMult extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "ary2"}; }
  @Override int nargs() { return 1+2; } // (x X1 X2)
  @Override
  public String str() { return "x"; }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame X1 = stk.track(asts[1].exec(env)).getFrame();
    Frame X2 = stk.track(asts[2].exec(env)).getFrame();
    return new ValFrame(DMatrix.mmul(X1,X2));
  }
}


class ASTMatch extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "table", "nomatch", "incomparables"}; }
  @Override int nargs() { return 1+4; } // (match fr table nomatch incomps)
  @Override public String str() { return "match"; }
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols() != 1 || !fr.vecs().isCategorical(0))
      throw new IllegalArgumentException("can only match on a single categorical column.");

    String[] strsTable2=null;
    double[] dblsTable2=null;
    if(      asts[2] instanceof ASTNumList) dblsTable2 = ((ASTNumList)asts[2]).sort().expand();
    else if( asts[2] instanceof ASTNum    ) dblsTable2 = new double[]{asts[2].exec(env).getNum()};
    else if( asts[2] instanceof ASTStrList){strsTable2 = ((ASTStrList)asts[2])._strs; Arrays.sort(strsTable2); }
    else if( asts[2] instanceof ASTStr    ) strsTable2 = new String[]{asts[2].exec(env).getStr()};
    else throw new IllegalArgumentException("Expected numbers/strings. Got: "+asts[2].getClass());

    final double nomatch = asts[3].exec(env).getNum();

    final String[] strsTable = strsTable2;
    final double[] dblsTable = dblsTable2;

    Frame rez = new MRTask() {
      @Override public void map(Chunks c, Chunks.AppendableChunks n) {
        String[] domain = _vecs.domain(0);
        double x; int rows = c.numRows();
        for( int r = 0; r < rows; ++r) {
          x = c.isNA(r,0) ? nomatch : (strsTable==null ? in(dblsTable, c.atd(r,0), nomatch) : in(strsTable, domain[(int)c.at8(r,0)], nomatch));
          n.addNum(0,x);
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, fr.vecs()).outputFrame();
    return new ValFrame(rez);
  }
  private static double in(String[] matches, String s, double nomatch) { return Arrays.binarySearch(matches, s) >=0 ? 1: nomatch; }
  private static double in(double[] matches, double d, double nomatch) { return binarySearchDoublesUlp(matches, 0,matches.length,d) >=0 ? 1: nomatch; }

  private static int binarySearchDoublesUlp(double[] a, int from, int to, double key) {
    int lo = from;
    int hi = to-1;
    while( lo <= hi ) {
      int mid = (lo + hi) >>> 1;
      double midVal = a[mid];
      if( MathUtils.equalsWithinOneSmallUlp(midVal, key) ) return mid;
      if (midVal < key)      lo = mid + 1;
      else if (midVal > key) hi = mid - 1;
      else {
        long midBits = Double.doubleToLongBits(midVal);
        long keyBits = Double.doubleToLongBits(key);
        if (midBits == keyBits) return mid;
        else if (midBits < keyBits) lo = mid + 1;
        else                        hi = mid - 1;
      }
    }
    return -(lo + 1);  // key not found.
  }
}

// Indices of which entries are not equal to 0
class ASTWhich extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (which col)
  @Override public String str() { return "which"; }
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    // The 1-row version
    if( f.numRows()==1 && f.numCols() > 1) {
      AppendableVec v = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec(),Vec.T_NUM);
      Chunks.AppendableChunks nc = v.chunkForChunkIdx(0);
      Chunks chunks = f.vecs().getChunks(0);
      for( int i=0; i<f.numCols(); i++ ) 
        if( chunks.at8(0,0)!=0 )
          nc.addNum(0,i);
      Futures fs = nc.close(new Futures());
      VecAry vec = new VecAry(v.closeVec(fs));
      fs.blockForPending();
      return new ValFrame(new Frame((Key)null,vec));
    }

    // The 1-column version
    VecAry vec = f.vecs();
    if( f.numCols() > 1 || !vec.isInt(0) )
      throw new IllegalArgumentException("which requires a single integer column");
    Frame f2 = new MRTask() {
      @Override public void map(Chunks c, Chunks.AppendableChunks nc) {
        long start = c.start();
        for(int i=0;i<c.numRows();++i)
          if( c.at8(i,0)!=0 ) nc.addNum(0,start+i);
      }
    }.doAll(new byte[]{Vec.T_NUM},vec).outputFrame();
    return new ValFrame(f2);
  }
}

