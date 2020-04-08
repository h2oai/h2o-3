package water.rapids.prims.model;


import hex.Model;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValModel;
import water.rapids.vals.ValNum;

public class AstModelResetThreshold extends AstPrimitive {
    
    @Override
    public String[] args() {
        return new String[]{"model", "threshold"};
    }

    @Override
    public int nargs() {
        return 1 + 2;
    } // (+ Model + threshold)

    @Override
    public String str() {
        return "model.reset.threshold";
    }

    @Override
    public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) { Model model = stk.track(asts[1].exec(env)).getModel();
        double oldTh = model._output.defaultThreshold();
        double threshold = stk.track(asts[2].exec(env)).getNum();
        model.resetThreshold(threshold);
        return new ValNum(oldTh);
    }
}
