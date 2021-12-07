package water.rapids.prims.rulefit;

import hex.rulefit.RuleFitModel;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

/**
 * Evaluates validity of the given rules on the given data. 
 */
public class AstPredictRule extends AstPrimitive<AstPredictRule> {

    @Override
    public String[] args() {
        return new String[]{"model"};
    }

    @Override
    public int nargs() {
        return 1 + 3;
    } // (rulefit.predict.rules model frame ruleIds)

    @Override
    public String str() {
        return "rulefit.predict.rules";
    }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        RuleFitModel model = (RuleFitModel) stk.track(asts[1].exec(env)).getModel();
        Frame frame = stk.track(asts[2].exec(env)).getFrame();
        String[] ruleIds = stk.track(asts[3].exec(env)).getStrs();
        Frame result = model.predictRules(frame, ruleIds);
        return new ValFrame(result);
    }
}
