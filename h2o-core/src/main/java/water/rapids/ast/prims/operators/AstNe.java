package water.rapids.ast.prims.operators;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.vals.ValFrame;
import water.util.MathUtils;
import water.util.StringUtils;

/**
 */
public class AstNe extends AstBinOp {
  public String str() {
    return "!=";
  }

  public double op(double l, double r) {
    return MathUtils.equalsWithinOneSmallUlp(l, r) ? 0 : 1;
  }

  @Override
  public ValFrame frame_op_scalar(Frame fr, final double d) {
    return new ValFrame(new MRTask() {
      @Override
      public void map(ChunkAry chks, NewChunkAry cress) {
        VecAry vecs = _fr.vecs();
        for (int c = 0; c < chks._numCols; c++) {

          BufferedString bStr = new BufferedString();
          if (vecs.isString(c))
            for (int i = 0; i < chks._len; i++)
              cress.addNum(c,str_op(chks.atStr(bStr, i,c), Double.isNaN(d) ? null : new BufferedString(String.valueOf(d))));
          else if (vecs.isNumeric(c)) cress.addZeros(c,chks._len);
          else
            for (int i = 0; i < chks._len; i++)
              cress.addNum(op(chks.atd(i,c), d));
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
      return StringUtils.isNullOrEmpty(r) ? 0 : 1;
    else
      return l.equals(r) ? 0 : 1;
  }
}
