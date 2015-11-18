package water.rapids;

import water.DKV;
import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.Arrays;

/** A collection of Strings only.  This is a syntatic form only, and never
 *  executes and never gets on the execution stack.
 */
public class ASTStrList extends ASTParameter {
  public String[] _strs;
  ASTStrList( Exec e ) {
    ArrayList<String> strs  = new ArrayList<>();
    while( true ) {
      char c = e.skipWS();
      if( c==']' ) break;
      if( e.isQuote(c) ) strs.add(e.match(c));
      else throw new IllegalArgumentException("Expecting the start of a string");
    }
    e.xpeek(']');
    _strs = strs.toArray(new String[strs.size()]);
  }
  // Strange count of args, due to custom parsing
  @Override int nargs() { return -1; }
  // This is a special syntatic form; the number-list never executes and hits
  // the execution stack
  @Override public Val exec(Env env) { throw H2O.fail(); }
  @Override public String str() { return Arrays.toString(_strs); }
  // Select columns by number or String.
  @Override int[] columns( String[] names ) { 
    int[] idxs = new int[_strs.length];
    for( int i=0; i < _strs.length; i++ ) {
      int idx = idxs[i] = water.util.ArrayUtils.find(names,_strs[i]);
      if( idx == -1 ) throw new IllegalArgumentException("Column "+_strs[i]+" not found");
    }
    return idxs;
  }
}

/** Assign column names */
class ASTColNames extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "cols", "names"}; }
  @Override int nargs() { return 1+3; } // (colnames frame [#cols] ["names"])
  @Override public String str() { return "colnames="; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( asts[2] instanceof ASTNumList ) {
      if( !(asts[3] instanceof ASTStrList) )
        throw new IllegalArgumentException("Column naming requires a string-list, but found a "+asts[3].getClass());
      ASTNumList cols = ((ASTNumList)asts[2]);
      ASTStrList nams = ((ASTStrList)asts[3]);
      int d[] = cols.expand4();
      if( d.length != nams._strs.length ) 
        throw new IllegalArgumentException("Must have the same number of column choices as names");
      for( int i=0; i<d.length; i++ )
        fr._names[d[i]] = nams._strs[i];

    } else if( (asts[2] instanceof ASTNum) ) {
      int col = (int)(asts[2].exec(env).getNum());
      String name =   asts[3].exec(env).getStr() ;
      fr._names[col] = name;
    } else
      throw new IllegalArgumentException("Column naming requires a number-list, but found a "+asts[2].getClass());
    if( fr._key != null ) DKV.put(fr); // Update names in DKV
    return new ValFrame(fr);
  }  
}

/** Convert to StringVec */
class ASTAsCharacter extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (as.character col)
  @Override
  public String str() { return "as.character"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame ary = stk.track(asts[1].exec(env)).getFrame();
    Vec[] nvecs = new Vec[ary.numCols()];
    Vec vv;
    for(int c=0;c<nvecs.length;++c) {
      vv = ary.vec(c);
      try {
        nvecs[c] = vv.toStringVec();
      } catch (Exception e) {
        VecUtils.deleteVecs(nvecs, c);
        throw e;
      }
    }
    return new ValFrame(new Frame(ary._names, nvecs));
  }
}

/** Convert to a factor/categorical */
class ASTAsFactor extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (as.factor col)
  @Override public String str() { return "as.factor"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame ary = stk.track(asts[1].exec(env)).getFrame();
    Vec[] nvecs = new Vec[ary.numCols()];

    // Type check  - prescreen for correct types
    for (Vec v : ary.vecs())
      if (!(v.isCategorical() || v.isString()|| v.isNumeric()))
        throw new IllegalArgumentException("asfactor() requires a string, categorical, or numeric column. "
            +"Received "+ary.anyVec().get_type_str()
            +". Please convert column to a string or categorical first.");
    Vec vv;
    for(int c=0;c<nvecs.length;++c) {
      vv = ary.vec(c);
      try {
        nvecs[c] = vv.toCategoricalVec();
      } catch (Exception e) {
        VecUtils.deleteVecs(nvecs, c);
        throw e;
      }
    }
    return new ValFrame(new Frame(ary._names, nvecs));
  }
}

/** Convert to a numeric */
class ASTAsNumeric extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (as.numeric col)
  @Override
  public String str() { return "as.numeric"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec[] nvecs = new Vec[fr.numCols()];
    Vec vv;
    for(int c=0;c<nvecs.length;++c) {
      vv = fr.vec(c);
      try {
        nvecs[c] = vv.toNumericVec();
      } catch (Exception e) {
        VecUtils.deleteVecs(nvecs, c);
        throw e;
      }
    }
    return new ValFrame(new Frame(fr._names, nvecs));
  }
}

/** Is String Vec? */
class ASTIsCharacter extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (is.character col)
  @Override public String str() { return "is.character"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols() == 1 ) return new ValNum(fr.anyVec().isString()?1:0);
    double ds[] = new double[fr.numCols()];
    for( int i=0; i<fr.numCols(); i++ )
      ds[i] = fr.vec(i).isString() ? 1 : 0;
    Vec vec = Vec.makeVec(ds,fr.anyVec().group().addVec());
    return new ValFrame(new Frame(new String[]{"is.character"}, new Vec[]{vec}));
  }
}

/** Is a factor/categorical? */
class ASTIsFactor extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (is.factor col)
  @Override public String str() { return "is.factor"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols() == 1 ) return new ValNum(fr.anyVec().isCategorical()?1:0);
    double ds[] = new double[fr.numCols()];
    for( int i=0; i<fr.numCols(); i++ )
      ds[i] = fr.vec(i).isCategorical() ? 1 : 0;
    Vec vec = Vec.makeVec(ds,fr.anyVec().group().addVec());
    return new ValFrame(new Frame(new String[]{"is.factor"}, new Vec[]{vec}));
  }
}

/** Is a numeric? */
class ASTIsNumeric extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (is.numeric col)
  @Override public String str() { return "is.numeric"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols() == 1 ) return new ValNum(fr.anyVec().isNumeric()?1:0);
    double ds[] = new double[fr.numCols()];
    for( int i=0; i<fr.numCols(); i++ )
      ds[i] = fr.vec(i).isNumeric() ? 1 : 0;
    Vec vec = Vec.makeVec(ds,fr.anyVec().group().addVec());
    return new ValFrame(new Frame(new String[]{"is.numeric"}, new Vec[]{vec}));
  }
}

/** Any columns factor/categorical? */
class ASTAnyFactor extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (any.factor frame)
  @Override public String str() { return "any.factor"; }
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    for( Vec vec : fr.vecs() )  if( vec.isCategorical()) return new ValNum(1);
    return new ValNum(0);
  }
}
