package water.rapids.ast.prims.math;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.vals.ValFrame;

public class AstKm extends AstBuiltin<AstKm> {
  @Override
  public int nargs() {
    return 1 + 2;
  }

  @Override
  public String[] args() {
    return new String[]{"frame", "col"};
  }

  @Override
  public String str() {
    return "km";
  }

  @Override
  protected Val exec(Val[] args) {
    Frame frame = args[1].getFrame();
    
    String columnName = args[2].getStr();
    
    new MRTask() {

      @Override
      public void map(Chunk c) {
        for (int i = 0; i < c.len(); i++) {
          c.set(i, c.atd(i) * 1.6);
        }
      }
    }.doAll(frame.vec(columnName));

    return new ValFrame(frame);
  }
}
