package water.rapids.ast.prims.timeseries;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.ArrayUtils;

/**
 * Compute a difference of a time series where lag = 1
 */
public class AstDiffLag1 extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }

  @Override
  public String str() {
    return "difflag1";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env).getFrame());
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("Expected a single column for diff. Got: " + fr.numCols() + " columns.");
    if (!fr.anyVec().isNumeric())
      throw new IllegalArgumentException("Expected a numeric column for diff. Got: " + fr.anyVec().get_type_str());

    final double[] lastElemPerChk = GetLastElemPerChunkTask.get(fr.anyVec());

    return new ValFrame(new MRTask() {
      @Override
      public void map(Chunk c, NewChunk nc) {
        if (c.cidx() == 0) nc.addNA();
        else nc.addNum(c.atd(0) - lastElemPerChk[c.cidx() - 1]);
        for (int row = 1; row < c._len; ++row)
          nc.addNum(c.atd(row) - c.atd(row - 1));
      }
    }.doAll(fr.types(), fr).outputFrame(fr.names(), fr.domains()));
  }

  private static class GetLastElemPerChunkTask extends MRTask<GetLastElemPerChunkTask> {
    double[] _res;

    GetLastElemPerChunkTask(Vec v) {
      _res = new double[v.espc().length];
    }

    static double[] get(Vec v) {
      GetLastElemPerChunkTask t = new GetLastElemPerChunkTask(v);
      t.doAll(v);
      return t._res;
    }

    @Override
    public void map(Chunk c) {
      _res[c.cidx()] = c.atd(c._len - 1);
    }

    @Override
    public void reduce(GetLastElemPerChunkTask t) {
      ArrayUtils.add(_res, t._res);
    }
  }
}
