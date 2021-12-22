package water.rapids.ast.prims.mungers;

import water.DKV;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstStrList;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;

public class AstAppendLevels extends AstPrimitive<AstAppendLevels> {

    @Override
    public String[] args() {
        return new String[]{"ary", "inPlace", "extraLevels"};
    }

    @Override
    public int nargs() {
        return 1 + 3;
    } // (setDomain x inPlace [list of strings])

    @Override
    public String str() {
        return "appendLevels";
    }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Frame f = stk.track(asts[1].exec(env)).getFrame();
        boolean inPlace = asts[2].exec(env).getNum() == 1;
        String[] extraLevels = ((AstStrList) asts[3])._strs;
        if (f.numCols() != 1)
            throw new IllegalArgumentException("Must be a single column. Got: " + f.numCols() + " columns.");
        if (! f.vec(0).isCategorical())
            throw new IllegalArgumentException("Vector must be a factor column. Got: " + f.vec(0).get_type_str());
        final Vec v = inPlace ? f.vec(0) : env._ses.copyOnWrite(f, new int[]{0})[0];
        v.setDomain(ArrayUtils.append(v.domain(), extraLevels));
        DKV.put(v);
        return new ValFrame(f);
    }

}
