package water.api;

import water.H2O;
import water.Iced;
import water.Key;
import water.api.CascadeHandler.Cascade;
import water.cascade.Env;
import water.fvec.Frame;
import water.util.Log;

class CascadeHandler extends Handler<Cascade, CascadeV1>{

  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @Override protected void compute2() { throw H2O.fail(); }

  /**
   *  Cascade: A process in which information is successively passed on.
   */
  protected static final class Cascade extends Iced {
    // Inputs
    String _ast; // A Lisp-like ast.

    //Outputs
    @API(help="Parsing error, if any") String _error;
    @API(help="Result key"           ) Key _key;
    @API(help="Rows in Frame result" ) long _num_rows;
    @API(help="Columns in Frame result" ) int  _num_cols;
    @API(help="Scalar result"        ) double _scalar;
    @API(help="Const String result")   String _string;
    @API(help="Function result"      ) String _funstr;
    @API(help="Column Names")          String[] _col_names;
    // Pretty-print of result.  For Frames, first 10 rows.  For scalars, just the
    // value.  For functions, the pretty-printed AST.
    @API(help="String result"        ) String _result;

  }

  CascadeV1 exec(int version, Cascade cascade) {
    Throwable e;
    Env env = null;
    try {
      env = water.cascade.Exec.exec(cascade._ast);
      StringBuilder sb = env._sb;
      if( sb.length()!=0 ) sb.append("\n");
      if (env.isAry()) {
        Frame fr = env.peekAry();
        cascade._key = fr._key;
        cascade._num_rows = fr.numRows();
        cascade._num_cols = fr.numCols();
        cascade._col_names = fr.names();
        //TODO: colSummary  cols = new Inspect2.ColSummary[num_cols];
      } else if (env.isNum()) {
        cascade._scalar = env.popDbl();
        sb.append(Double.toString(cascade._scalar));
      } else if (env.isStr()) {
        cascade._string = env.popStr();
        sb.append(cascade._string);
      }
      cascade._result = sb.toString();
      return schema(version).fillFromImpl(cascade);
    }
    catch( IllegalArgumentException pe ) { e=pe;}
    catch( Throwable e2 ) { Log.err(e=e2); }
    finally {
      if (env != null) {
        try {env.remove_and_unlock(); }
        catch (Exception xe) { Log.err("env.remove_and_unlock() failed", xe); }
      }
    }
    return schema(version).fillFromImpl(cascade);
  }

  @Override protected CascadeV1 schema(int version) { return new CascadeV1(); }
}