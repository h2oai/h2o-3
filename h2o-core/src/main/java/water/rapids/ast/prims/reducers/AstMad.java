package water.rapids.ast.prims.reducers;

import hex.quantile.QuantileModel;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Median absolute deviation
 */
public class AstMad extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "combineMethod", "const"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } //(mad fr combine_method const)

  @Override
  public String str() {
    return "h2o.mad";
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec[] vecs = fr.vecs();
    if (vecs.length == 0 || vecs[0].naCnt() > 0) return new ValNum(Double.NaN);
    if (vecs.length > 1) throw new IllegalArgumentException("MAD expects a single numeric column");
    QuantileModel.CombineMethod cm = QuantileModel.CombineMethod.valueOf(asts[2].exec(env).getStr().toUpperCase());
    double constant = asts[3].exec(env).getNum();
    return new ValNum(mad(fr, cm, constant));
  }

  public static double mad(Frame f, QuantileModel.CombineMethod cm, double constant) {
    // need Frames everywhere because of QuantileModel demanding a Frame...
    Key tk = null;
    if (f._key == null) {
      DKV.put(tk = Key.make(), f = new Frame(tk, f.names(), f.vecs()));
    }
    final double median = AstMedian.median(f, cm);
    Frame abs_dev = new MRTask() {
      @Override
      public void map(Chunk c, NewChunk nc) {
        for (int i = 0; i < c._len; ++i)
          nc.addNum(Math.abs(c.at8(i) - median));
      }
    }.doAll(1, Vec.T_NUM, f).outputFrame();
    if (abs_dev._key == null) {
      DKV.put(tk = Key.make(), abs_dev = new Frame(tk, abs_dev.names(), abs_dev.vecs()));
    }
    double mad = AstMedian.median(abs_dev, cm);
    DKV.remove(f._key); // drp mapping, keep vec
    DKV.remove(abs_dev._key);
    return constant * mad;
  }
}
