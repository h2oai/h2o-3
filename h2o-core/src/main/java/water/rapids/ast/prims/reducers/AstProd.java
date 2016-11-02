package water.rapids.ast.prims.reducers;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstProd extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (prod x)

  @Override
  public String str() {
    return "prod";
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    for (Vec v : fr.vecs())
      if (v.isCategorical() || v.isUUID() || v.isString())
        throw new IllegalArgumentException("`" + str() + "`" + " only defined on a data frame with all numeric variables");
    double prod = new AstProd.RedProd().doAll(fr)._d;
    return new ValNum(prod);
  }

  private static class RedProd extends MRTask<AstProd.RedProd> {
    double _d;

    @Override
    public void map(Chunk chks[]) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
        double prod = 1.;
        for (int r = 0; r < rows; r++)
          prod *= C.atd(r);
        _d = prod;
        if (Double.isNaN(prod)) break;
      }
    }

    @Override
    public void reduce(AstProd.RedProd s) {
      _d *= s._d;
    }
  }
}
