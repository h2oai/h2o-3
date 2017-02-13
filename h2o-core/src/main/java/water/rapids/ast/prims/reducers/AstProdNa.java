package water.rapids.ast.prims.reducers;

import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstProdNa extends AstPrimitive {
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
    return "prod.na";
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    for (VecAry v : fr.vecs().singleVecs())
      if (v.isCategorical() || v.isUUID() || v.isString())
        throw new IllegalArgumentException("`" + str() + "`" + " only defined on a data frame with all numeric variables");
    double prod = new AstProdNa.RedProd().doAll(fr)._d;
    return new ValNum(prod);
  }

  private static class RedProd extends MRTask<AstProdNa.RedProd> {
    double _d;

    @Override
    public void map(ChunkAry chks) {
      int rows = chks._len;
      for (int c = 0; c < chks._len; ++c) {
        double prod = 1.;
        for (int r = 0; r < rows; r++) {
          if (chks.isNA(r,c)) continue;
          prod *= chks.atd(r,c);
        }
        _d = prod;
        if (Double.isNaN(prod)) break;
      }
    }

    @Override
    public void reduce(AstProdNa.RedProd s) {
      _d += s._d;
    }
  }
}
