package water.rapids.ast.prims.operators;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.vals.ValFrame;
import water.util.MathUtils;
import water.util.StringUtils;

/**
 */
public class AstEq extends AstBinOp {
  public String str() {
    return "==";
  }

  public double op(double l, double r) {
    return MathUtils.equalsWithinOneSmallUlp(l, r) ? 1 : 0;
  }

  @Override
  public ValFrame frame_op_scalar(Frame fr, final double d) {
    return new ValFrame(new MRTask() {
      @Override
      public void map(Chunk[] chks, NewChunk[] cress) {
        for (int c = 0; c < chks.length; c++) {
          Chunk chk = chks[c];
          NewChunk cres = cress[c];
          BufferedString bStr = new BufferedString();
          if (chk.vec().isString())
            for (int i = 0; i < chk._len; i++)
              cres.addNum(str_op(chk.atStr(bStr, i), Double.isNaN(d) ? null : new BufferedString(String.valueOf(d))));
          else if (!chk.vec().isNumeric()) cres.addZeros(chk._len);
          else
            for (int i = 0; i < chk._len; i++)
              cres.addNum(op(chk.atd(i), d));
        }
      }
    }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame());
  }

  @Override
  public boolean categoricalOK() {
    return true;
  }  // Make sense to run this OP on an enm?

  public double str_op(BufferedString l, BufferedString r) {
    if (StringUtils.isNullOrEmpty(l))
      return StringUtils.isNullOrEmpty(r) ? 1 : 0;
    else
      return l.equals(r) ? 1 : 0;
  }
}
