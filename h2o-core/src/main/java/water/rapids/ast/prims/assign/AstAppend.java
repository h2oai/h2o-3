package water.rapids.ast.prims.assign;

import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstAppend extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"dst", "src", "colName"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (append dst src "colName")

  @Override
  public String str() {
    return "append";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame dst = stk.track(asts[1].exec(env)).getFrame();
    Val vsrc = stk.track(asts[2].exec(env));
    String newColName = asts[3].exec(env).getStr();

    Vec vec = dst.anyVec();
    switch (vsrc.type()) {
      case Val.NUM:
        vec = vec.makeCon(vsrc.getNum());
        break;
      case Val.STR:
        vec = vec.makeCon(vsrc.getStr());
        break;
      case Val.FRM:
        if (vsrc.getFrame().numCols() != 1) throw new IllegalArgumentException("Can only append one column");
        vec = vsrc.getFrame().anyVec();
        break;
      default:
        throw new IllegalArgumentException("Source must be a Frame or Number, but found a " + vsrc.getClass());
    }
    dst = new Frame(dst._names.clone(), dst.vecs().clone());
    dst.add(newColName, vec);
    return new ValFrame(dst);
  }
}
