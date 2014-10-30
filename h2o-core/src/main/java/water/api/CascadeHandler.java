package water.api;

import water.Iced;
import water.Key;
import water.api.CascadeHandler.Cascade;
import water.cascade.Env;
import water.fvec.Frame;
import water.util.Log;

class CascadeHandler extends Handler<Cascade, CascadeV1> {

  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /**
   *  Cascade: A process in which information is successively passed on.
   */
  protected static final class Cascade extends Iced {
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

  public CascadeV1 exec(int version, Cascade cascade) {
    Throwable e = null;
    Env env = null;
    try {
      //learn all fcns
      if(cascade._funs != null) {
        for (String f : cascade._funs) {
          water.cascade.Exec.exec(f);
        }
      }
      env = water.cascade.Exec.exec(cascade._ast);
      StringBuilder sb = env._sb;
      if( sb.length()!=0 ) sb.append("\n");
      if (env.isAry()) {
        Frame fr = env.pop0Ary();
        cascade._key = fr._key;
        cascade._num_rows = fr.numRows();
        cascade._num_cols = fr.numCols();
        cascade._col_names = fr.names();
        cascade._string = null;
        //TODO: colSummary  cols = new Inspect2.ColSummary[num_cols];
      } else if (env.isNum()) {
        cascade._scalar = env.popDbl();
        sb.append(Double.toString(cascade._scalar));
        cascade._string = null;
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
      if (e != null) e.printStackTrace();
      if (e != null) cascade._error = e.getMessage() == null ? e.toString() : e.getMessage();
      if (e != null && e instanceof ArrayIndexOutOfBoundsException) cascade._error = e.toString();
      if (env != null) {
        try {env.remove_and_unlock(); }
        catch (Exception xe) { Log.err("env.remove_and_unlock() failed", xe); }
      }
    }
    return schema(version).fillFromImpl(cascade);
  }

  @Override protected CascadeV1 schema(int version) { return new CascadeV1(); }
}