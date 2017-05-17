package water.rapids.ast.prims.search;

import water.fvec.Vec;

public class AstWhichMin extends AstWhichFunc {
    @Override
    public int nargs() { return 1 + 3; } // "frame", "na_rm", "axis"

    @Override
    public String str() {
        return "which.min";
    }

    @Override
    public double op(Vec l) {
        return l.min();
    }

    @Override
    public String searchVal(){
        return "min";
    }

    @Override
    public double init() {
        return 0;
    }
}