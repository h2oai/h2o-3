package water.rapids.ast.prims.models;

import hex.Model;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

public class AstResultFrame extends AstPrimitive {

    @Override
    public String[] args() {
        return new String[]{"model key"};
    }

    @Override
    public int nargs() {
        return 1 + 1;
    } // (segment_models_as_frame segment_models_id)

    @Override
    public String str() {
        return "result";
    }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Model model = stk.track(asts[1].exec(env)).getModel();
        return new ValFrame(model.result());
    }
}
