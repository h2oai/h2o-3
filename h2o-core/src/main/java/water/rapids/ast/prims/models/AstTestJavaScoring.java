package water.rapids.ast.prims.models;

import hex.Model;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

public class AstTestJavaScoring extends AstPrimitive {

    @Override
    public String[] args() {
        return new String[]{"model", "frame", "predictions", "epsilon"};
    }

    @Override
    public int nargs() {
        return 1 + 4;
    }

    @Override
    public String str() {
        return "model.testJavaScoring";
    }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Model model = stk.track(asts[1].exec(env)).getModel();
        Frame frame = stk.track(asts[2].exec(env)).getFrame();
        Frame preds = stk.track(asts[3].exec(env)).getFrame();
        double epsilon = stk.track(asts[4].exec(env)).getNum();

        boolean correct = model.testJavaScoring(frame, preds, epsilon);

        return ValFrame.fromRow(correct ? 1 : 0);
    }
}
