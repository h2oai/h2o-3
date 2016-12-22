package water.rapids.ast.prims.mungers;

import water.H2O;
import water.operations.FrameUtils;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;

/**
 * Split out in it's own function, instead of Yet Another UniOp, because it
 * needs a "is.NA" check instead of just using the Double.isNaN hack... because
 * it works on UUID and String columns.
 */
public class AstIsNa extends AstBuiltin<AstIsNa> {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public String str() {
    return "is.na";
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }

  @Override
  public Val exec(Val... args) {
    Val val = args[1];
    switch (val.type()) {
      case Val.NUM:
        return new ValNum(op(val.getNum()));

      case Val.FRM:
          return new ValFrame(FrameUtils.isNA(val));

      case Val.STR:
        return new ValNum(val.getStr() == null ? 1 : 0);

      default:
        throw H2O.unimpl("is.na unimpl: " + val.getClass());
    }
  }

  double op(double d) {
    return Double.isNaN(d) ? 1 : 0;
  }
}
