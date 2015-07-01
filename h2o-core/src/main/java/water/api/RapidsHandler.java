package water.api;

import water.DKV;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.ValueString;
import water.rapids.Env;
import water.util.Log;
import water.util.PrettyPrint;

class RapidsHandler extends Handler {
  private static final Object _lock = new Object();

  public RapidsV99 isEvaluated(int version, RapidsV99 rapids) {
    if (rapids == null) return null;
    if (rapids.ast_key == null) throw new IllegalArgumentException("No key supplied to getKey.");
    boolean isEval = false;
    if ((DKV.get(rapids.ast_key.key()))!=null) {
      isEval = true;
    }
    rapids.evaluated = isEval;
    return rapids;
  }


  public RapidsV99 exec(int version, RapidsV99 rapids) {
    synchronized( _lock ) {
      if( rapids == null ) return null;
      Throwable e = null;
      Env env = null;
      try {
        //learn all fcns
        if (rapids.fun != null) water.rapids.Exec.new_func(rapids.fun);
        if (rapids.ast == null || rapids.ast.equals("")) return rapids;
        env = water.rapids.Exec.exec(rapids.ast);
        StringBuilder sb = env._sb;
        if (sb.length() != 0) sb.append("\n");

        if( !env.isEmpty() ) {
          if( env.isAry() ) {
            Frame fr = env.popAry();
            Key[] keys = fr.keys();
            if (keys.length > 0) {
              rapids.vec_ids = new KeyV3.VecKeyV3[keys.length];
              for (int i = 0; i < keys.length; i++)
                rapids.vec_ids[i] = new KeyV3.VecKeyV3(keys[i]);
            }
            rapids.key = new KeyV3.FrameKeyV3(fr._key);
            rapids.num_rows = fr.numRows();
            rapids.num_cols = fr.numCols();
            rapids.col_names = fr.names();
            rapids.string = null;

            // Auto-demote Frames-of-1 to a Scalar
            if (fr.numRows() == 1 && fr.numCols() == 1) {
              Vec vec = fr.anyVec();
              rapids.num_rows = 0;
              rapids.num_cols = 0;
              if (vec.isEnum()) {
                rapids.string = vec.domain()[(int) vec.at(0)];
                sb.append(rapids.string);
                rapids.result_type = RapidsV99.ARYSTR;
              } else if (vec.isString()) {
                rapids.string = vec.atStr(new ValueString(), 0).toString();
                sb.append(rapids.string);
                rapids.result_type = RapidsV99.ARYSTR;
              } else if (vec.isUUID()) {
                rapids.string = PrettyPrint.UUID(vec.at16l(0), vec.at16h(0));
                sb.append(rapids.string);
                rapids.result_type = RapidsV99.ARYSTR;
              } else {
                rapids.scalar = vec.at(0);
                sb.append(Double.toString(rapids.scalar));
                rapids.string = null;
                rapids.result_type = RapidsV99.ARYNUM;
              }
            } else {
              rapids.result_type = RapidsV99.ARY;
              String[][] head = rapids.head = new String[Math.min(200, fr.numCols())][(int) Math.min(100, fr.numRows())];
              for (int r = 0; r < head[0].length; ++r) {
                for (int c = 0; c < head.length; ++c) {
                  if (fr.vec(c).isNA(r))
                    head[c][r] = "";
                  else if (fr.vec(c).isUUID())
                    head[c][r] = PrettyPrint.UUID(fr.vec(c).at16l(r), fr.vec(c).at16h(r));
                  else if (fr.vec(c).isString())
                    head[c][r] = String.valueOf(fr.vec(c).atStr(new ValueString(), r));
                  else if (fr.vec(c).isEnum())
                    head[c][r] = fr.domains()[c][(int) fr.vec(c).at(r)];
                  else
                    head[c][r] = String.valueOf(fr.vec(c).at(r));
                }
              }
            }
            //TODO: colSummary  cols = new Inspect2.ColSummary[num_cols];
          } else if (env.isNum()) {
            rapids.scalar = env.popDbl();
            sb.append(Double.toString(rapids.scalar));
            rapids.string = null;
            rapids.result_type = RapidsV99.NUM;
          } else if (env.isStr()) {
            rapids.string = env.popStr();
            sb.append(rapids.string);
            rapids.result_type = RapidsV99.STR;
          }
        }
        rapids.result = sb.toString();
        return rapids;
      } catch (IllegalArgumentException pe) {
        e = pe;
      } catch (Throwable e2) {
        Log.err(e = e2);
      } finally {
        if (env != null) {
          try {
            env.remove_and_unlock();
          } catch (Exception xe) {
            Log.err("env.remove_and_unlock() failed", xe);
          }
        }
      }
      if (e != null) e.printStackTrace();
      if (e != null)
        rapids.error = e.getMessage() == null ? e.toString() : e.getMessage();
      if (e != null && e instanceof ArrayIndexOutOfBoundsException)
        rapids.error = e.toString();
      if (e != null)
        throw new H2OIllegalArgumentException(rapids.error);
      return rapids;
    }
  }
}
