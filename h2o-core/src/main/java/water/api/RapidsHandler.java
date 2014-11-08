package water.api;

import water.Iced;
import water.Key;
import water.rapids.Env;
import water.fvec.Frame;
import water.util.Log;

class RapidsHandler extends Handler<RapidsHandler.Rapids, RapidsV1> {

  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /**
   *  Rapids:
   *    A process in which information is successively passed on.
   */
  protected static final class Rapids extends Iced {
    // Inputs
    String _ast; // A Lisp-like ast.
    String[] _funs;

    //Outputs
    String   _error;
    Key      _key;
    long     _num_rows;
    int      _num_cols;
    double   _scalar;
    String   _string;
    String   _funstr;
    String[] _col_names;
    String   _result;
  }

  public RapidsV1 exec(int version, Rapids rapids) {
    if (rapids == null) return null;
    Throwable e = null;
    Env env = null;
    try {
      //learn all fcns
      if(rapids._funs != null) {
        for (String f : rapids._funs) {
          water.rapids.Exec.new_func(f);
        }
      }
      if (rapids._ast == null || rapids._ast.equals("")) return schema(version).fillFromImpl(rapids);
      env = water.rapids.Exec.exec(rapids._ast);
      StringBuilder sb = env._sb;
      if( sb.length()!=0 ) sb.append("\n");
      if (env.isAry()) {
        Frame fr = env.pop0Ary();
        rapids._key = fr._key;
        rapids._num_rows = fr.numRows();
        rapids._num_cols = fr.numCols();
        rapids._col_names = fr.names();
        rapids._string = null;
        //TODO: colSummary  cols = new Inspect2.ColSummary[num_cols];
      } else if (env.isNum()) {
        rapids._scalar = env.popDbl();
        sb.append(Double.toString(rapids._scalar));
        rapids._string = null;
      } else if (env.isStr()) {
        rapids._string = env.popStr();
        sb.append(rapids._string);
      }
      rapids._result = sb.toString();
      return schema(version).fillFromImpl(rapids);
    }
    catch( IllegalArgumentException pe ) { e=pe;}
    catch( Throwable e2 ) { Log.err(e=e2); }
    finally {
      if (e != null) e.printStackTrace();
      if (e != null) rapids._error = e.getMessage() == null ? e.toString() : e.getMessage();
      if (e != null && e instanceof ArrayIndexOutOfBoundsException) rapids._error = e.toString();
      if (env != null) {
        try {env.remove_and_unlock(); }
        catch (Exception xe) { Log.err("env.remove_and_unlock() failed", xe); }
      }
    }
    return schema(version).fillFromImpl(rapids);
  }

  @Override protected RapidsV1 schema(int version) { return new RapidsV1(); }
}