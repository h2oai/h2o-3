package water.currents;

import java.util.ArrayList;
import java.util.Arrays;
import water.H2O;
import water.DKV;
import water.fvec.Frame;
import water.fvec.Vec;

/** A collection of Strings only.  This is a syntatic form only, and never
 *  executes and never gets on the execution stack.
 */
class ASTStrList extends AST {
  String[] _strs;
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
  @Override Val exec( Env env ) { throw H2O.fail(); }
  @Override public String str() { return Arrays.toString(_strs); }
}

/** Assign column names */
class ASTColNames extends ASTPrim {
  @Override int nargs() { return 1+3; } // (colnames frame [#cols] ["names"])
  @Override String str() { return "colnames="; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( asts[2] instanceof ASTNumList ) {
      if( !(asts[3] instanceof ASTStrList) )
        throw new IllegalArgumentException("Column naming requires a string-list, but found a "+asts[3].getClass());
      ASTNumList cols = ((ASTNumList)asts[2]);
      ASTStrList nams = ((ASTStrList)asts[3]);
      double d[] = cols.expand();
      if( d.length != nams._strs.length ) 
        throw new IllegalArgumentException("Must have the same number of column choices as names");
      for( int i=0; i<d.length; i++ )
        fr._names[(int)d[i]] = nams._strs[i];

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

/** Convert to a factor/categorical */
class ASTAsFactor extends ASTPrim {
  @Override int nargs() { return 1+1; } // (as.factor col)
  @Override String str() { return "as.factor"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols() != 1 ) throw new IllegalArgumentException("as.factor requires a single column");
    Vec v0 = fr.anyVec();
    if( !v0.isEnum() ) v0 = v0.toEnum();
    return new ValFrame(new Frame(fr._names, new Vec[]{v0}));
  }
}
