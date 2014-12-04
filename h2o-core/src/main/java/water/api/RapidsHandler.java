package water.api;

import water.fvec.Frame;
import water.rapids.Env;
import water.util.Log;

class RapidsHandler extends Handler {

  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public RapidsV1 exec(int version, RapidsV1 rapids) {
    if (rapids == null) return null;
    Throwable e = null;
    Env env = null;
    try {
      //learn all fcns
      if(rapids.funs != null) {
        for (String f : rapids.funs) {
          water.rapids.Exec.new_func(f);
        }
      }
      if (rapids.ast == null || rapids.ast.equals("")) return rapids;
      env = water.rapids.Exec.exec(rapids.ast);
      StringBuilder sb = env._sb;
      if( sb.length()!=0 ) sb.append("\n");
      if (env.isAry()) {
        Frame fr = env.pop0Ary();
        rapids.key = fr._key;
        rapids.num_rows = fr.numRows();
        rapids.num_cols = fr.numCols();
        rapids.col_names = fr.names();
        rapids.string = null;
        //TODO: colSummary  cols = new Inspect2.ColSummary[num_cols];
      } else if (env.isNum()) {
        rapids.scalar = env.popDbl();
        sb.append(Double.toString(rapids.scalar));
        rapids.string = null;
      } else if (env.isStr()) {
        rapids.string = env.popStr();
        sb.append(rapids.string);
      }
      rapids.result = sb.toString();
      return rapids;
    }
    catch( IllegalArgumentException pe ) { e=pe;}
    catch( Throwable e2 ) { Log.err(e=e2); }
    finally {
      if (e != null) e.printStackTrace();
      if (e != null) rapids.exception = e.getMessage() == null ? e.toString() : e.getMessage();
      if (e != null && e instanceof ArrayIndexOutOfBoundsException) rapids.exception = e.toString();
      if (env != null) {
        try {env.remove_and_unlock(); }
        catch (Exception xe) { Log.err("env.remove_and_unlock() failed", xe); }
      }
    }
    return rapids;
  }
}
