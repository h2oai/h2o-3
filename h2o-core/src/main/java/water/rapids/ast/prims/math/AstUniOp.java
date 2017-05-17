package water.rapids.ast.prims.math;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.vals.ValRow;

/**
 * Subclasses auto-widen between scalars and Frames, and have exactly one argument
 */
public abstract class AstUniOp<T extends AstUniOp<T>> extends AstBuiltin<T> {
  @Override
  public String[] args() {
    return new String[]{"ary"};
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
        Frame fr = val.getFrame();
        for (int i = 0; i < fr.numCols(); i++)
          if (!fr.vec(i).isNumeric())
            throw new IllegalArgumentException(
                "Operator " + str() + "() cannot be applied to non-numeric column " + fr.name(i));

        // Get length of columns in fr and append `op(colName)`. For example, a column named "income" that had
        // a log transformation would now be changed to `log(income)`.
        String[] newNames = new String[fr.numCols()];
        for (int i = 0; i < newNames.length; i++) {
          newNames[i] = str() + "(" + fr.name(i) + ")";
        }
        return new ValFrame(new MRTask() {
          @Override
          public void map(Chunk cs[], NewChunk ncs[]) {
            for (int col = 0; col < cs.length; col++) {
              Chunk c = cs[col];
              NewChunk nc = ncs[col];
              for (int i = 0; i < c._len; i++)
                nc.addNum(op(c.atd(i)));
            }
          }
        }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame(newNames, null));

      case Val.ROW:
        double[] ds = new double[val.getRow().length];
        for (int i = 0; i < ds.length; ++i)
          ds[i] = op(val.getRow()[i]);
        String[] names = ((ValRow) val).getNames().clone();
        return new ValRow(ds, names);

      default:
        throw H2O.unimpl("unop unimpl: " + val.getClass());
    }
  }

  public abstract double op(double d);
}


