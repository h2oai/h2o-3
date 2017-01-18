package water.rapids.ast.prims.advmath;

import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.VecAry;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNums;

public class AstKurtosis extends AstPrimitive {
    @Override
    public String[] args() {
        return new String[]{"ary", "na_rm"};
    }

    @Override
    public String str() {return "kurtosis";}

    @Override
    public int nargs() {return 1 + 2;} // (kurtosis ary na.rm)

    @Override
    public ValNums apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Frame fr = stk.track(asts[1].exec(env)).getFrame();
        boolean narm = asts[2].exec(env).getNum() == 1;
        double[] ds = new double[fr.numCols()];
        VecAry vecs = fr.vecs();
        for (int i = 0; i < fr.numCols(); i++)
            ds[i] = (!vecs.isNumeric(i) || vecs._numRows == 0 || (!narm && vecs.naCnt(i) > 0)) ? Double.NaN : kurtosis(vecs.select(i));
        return new ValNums(ds);
    }

    public static double kurtosis(VecAry v) {
        return AstHist.fourth_moment(v);
    }
}
