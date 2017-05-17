package water.rapids.ast.prims.search;

import water.fvec.Vec;

public class AstWhichMax extends AstWhichFunc {
    @Override
    public int nargs() { return 1 + 3; } // "frame", "na_rm", "axis"

    @Override
    public String str() {
        return "which.max";
    }

    @Override
    public double op(Vec l) {
        return l.max();
    }

    @Override
    public String searchVal(){
        return "max";
    }

    @Override
    public double init() {
        return 0;
    }
}