package water.rapids.ast.prims.mungers;

import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.*;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.vals.ValStr;

/**
 */
public class AstFlatten extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (flatten fr)

  @Override
  public String str() {
    return "flatten";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1 || fr.numRows() != 1) return new ValFrame(fr); // did not flatten
    Vec vec = fr.anyVec();
    switch (vec.get_type()) {
      case Vec.T_BAD:
      case Vec.T_NUM:
        return new ValNum(vec.at(0));
      case Vec.T_TIME:
        return new ValNum(vec.at8(0));
      case Vec.T_STR:
        return new ValStr(vec.atStr(new BufferedString(), 0).toString());
      case Vec.T_CAT:
        return new ValStr(vec.factor(vec.at8(0)));
      default:
        throw H2O.unimpl("The type of vector: " + vec.get_type_str() + " is not supported by " + str());
    }
  }
}
