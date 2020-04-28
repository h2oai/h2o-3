package water.rapids.ast.prims.models;


import hex.Model;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

/**
 * Reset a model threshold and return the old one.
 */
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
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Model model = stk.track(asts[1].exec(env)).getModel();
        double oldThreshold = model._output.defaultThreshold();
        double newThreshold = stk.track(asts[2].exec(env)).getNum();
        model.resetThreshold(newThreshold);
        return ValFrame.fromRow(oldThreshold);
    }
}
