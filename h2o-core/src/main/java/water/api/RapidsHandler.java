package water.api;

import water.DKV;
import water.Value;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Raft;
import water.util.Log;

class RapidsHandler extends Handler {

  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public RapidsV1 isEvaluated(int version, RapidsV1 rapids) {
    if (rapids == null) return null;
    if (rapids.ast_key == null) throw new IllegalArgumentException("No key supplied to getKey.");
    boolean isEval = false;
    Value v;
    if ((v=DKV.get(rapids.ast_key.key()))!=null) {
      if (!(v.get() instanceof Frame)) {
        Raft raft = v.get();
        Value vv = raft == null ? null : DKV.get(raft.get_key());
        isEval = vv != null && (vv.get() != null);
      } else isEval = true;
    }
    rapids.evaluated = isEval;
    return rapids;
  }

  public RapidsV1 getKey(int version, RapidsV1 rapids) {
    if (rapids == null) return null;
    if (rapids.ast_key == null) throw new IllegalArgumentException("No key supplied to getKey.");
    Raft raf = DKV.getGet(rapids.ast_key.key());
    rapids.raft_key = new KeyV1(raf.get_key());
    return rapids;
  }

//  public RapidsV1 force(int version, Rapids rapids) {
//    if (rapids == null) return null;
//    if (rapids.ast_key == null) throw new IllegalArgumentException("No key supplied to force.");
//    Raft raft = DKV.getGet(rapids.ast_key);
//    // get the ast and exec
//    rapids.ast = raft.get_ast();
//    return exec(version, rapids);
//  }

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
        rapids.key = new KeyV1.FrameKeyV1(fr._key);
        rapids.num_rows = fr.numRows();
        rapids.num_cols = fr.numCols();
        rapids.col_names = fr.names();
        rapids.string = null;
        String[][] head = new String[Math.min(200,fr.numCols())][(int)Math.min(100,fr.numRows())];
        for (int r = 0; r < head[0].length; ++r) {
          for (int c = 0; c < head.length; ++c) {
            head[c][r] = String.valueOf(fr.vec(c).at(r));
          }
        }
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
      if (e != null) rapids.error = e.getMessage() == null ? e.toString() : e.getMessage();
      if (e != null && e instanceof ArrayIndexOutOfBoundsException) rapids.error = e.toString();
      if (env != null) {
        try {env.remove_and_unlock(); }
        catch (Exception xe) { Log.err("env.remove_and_unlock() failed", xe); }
      }
    }
    return rapids;
  }
}
