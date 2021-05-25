package water.rapids.prims.tree;

import hex.Model;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValStr;

/**
 * Re-weights auxiliary trees in a TreeModel
 */
public class AstTreeUpdateWeights extends AstPrimitive<AstTreeUpdateWeights> {

    @Override
    public String[] args() {
        return new String[]{"model"};
    }

    @Override
    public int nargs() {
        return 1 + 3;
    } // (tree.update.weights model frame weightsColumn)

    @Override
    public String str() {
        return "tree.update.weights";
    }

    @Override
    public ValStr apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Model.UpdateAuxTreeWeights model = (Model.UpdateAuxTreeWeights) stk.track(asts[1].exec(env)).getModel();
        Frame frame = stk.track(asts[2].exec(env)).getFrame();
        String weightsColumn = stk.track(asts[3].exec(env)).getStr();
        model.updateAuxTreeWeights(frame, weightsColumn);
        return new ValStr("OK");
    }

}
