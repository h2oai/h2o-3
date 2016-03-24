package water.rapids;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;

/**
 * R 'diff' command.
 *
 * This method is purely for the console right now.  Print stuff into the string buffer.
 * JSON response is not configured at all.
 */
class ASTDiffLag1 extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; }
  @Override public String str() { return "difflag1"; }
  @Override public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[2].exec(env).getFrame());
    if( fr.numCols() != 1 )
      throw new IllegalArgumentException("Expected a single column for diff. Got: " + fr.numCols() + " columns.");
    if( !fr.anyVec().isNumeric() )
      throw new IllegalArgumentException("Expected a numeric column for diff. Got: " + fr.anyVec().get_type_str());

    final double[] lastElemPerChk = GetLastElemPerChunkTask.get(fr.anyVec());

    return new ValFrame(new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        if( c.cidx()==0 ) nc.addNA();
        else              nc.addNum(c.atd(0) - lastElemPerChk[c.cidx()-1]);
        for(int row=1;row<c._len;++row)
          nc.addNum(c.atd(row) - c.atd(row-1));
      }
    }.doAll(fr).outputFrame());
  }

  private static class GetLastElemPerChunkTask extends MRTask<GetLastElemPerChunkTask> {
    double[] _res;
    GetLastElemPerChunkTask(Vec v) { _res = new double[v.espc().length]; }
    static double[] get(Vec v) {
      GetLastElemPerChunkTask t = new GetLastElemPerChunkTask(v);
      t.doAll(v);
      return t._res;
    }
    @Override public void map(Chunk c) { _res[c.cidx()] = c.atd(c._len-1); }
    @Override public void reduce(GetLastElemPerChunkTask t) { ArrayUtils.add(_res, t._res); }
  }
}