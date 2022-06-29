package water.rapids.ast.prims.models;

import hex.Model;
import water.DKV;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

public class AstTransformFrame extends AstPrimitive {
    
    @Override
    public int nargs()      { return 1 + 2; }

    @Override
    public String str() {
        return "transform";
    }

    @Override
    public String[] args() {
        return new String[]{"model key", "frame key"};
    }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Model model = stk.track(asts[1].exec(env)).getModel();
        Frame fr = DKV.get(asts[2].toString()).get();
        return new ValFrame(model.transform(fr));
    }
}
