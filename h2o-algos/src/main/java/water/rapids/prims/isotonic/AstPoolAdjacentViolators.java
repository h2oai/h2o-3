package water.rapids.prims.isotonic;

import hex.isotonic.PoolAdjacentViolatorsDriver;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.prims.rulefit.AstPredictRule;
import water.rapids.vals.ValFrame;

public class AstPoolAdjacentViolators extends AstPrimitive<AstPredictRule> {

    @Override
    public String[] args() {
        return new String[]{"frame"};
    }

    @Override
    public int nargs() {
        return 1 + 1;
    } // (isotonic.pav frame )

    @Override
    public String str() {
        return "isotonic.pav";
    }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
        Frame frame = stk.track(asts[1].exec(env)).getFrame();
        Frame result = PoolAdjacentViolatorsDriver.runPAV(frame);
        return new ValFrame(result);
    }

}
