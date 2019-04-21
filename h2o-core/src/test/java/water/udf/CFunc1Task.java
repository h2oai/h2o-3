package water.udf;

import org.junit.Ignore;

import water.fvec.Chunk;
import water.fvec.NewChunk;

@Ignore("Support for tests, but no actual tests here")
public class CFunc1Task extends CFuncTask<CFunc1, CFunc1Task> {

  private final int len;
  private final int ofs;

  public CFunc1Task(CFuncRef cFuncRef, int ofs, int len) {
    super(cFuncRef);
    this.len = len;
    this.ofs = ofs;

  }
  public CFunc1Task(CFuncRef cFuncRef, int len) {
    this(cFuncRef, 0, len);
  }

  @Override
  public void map(Chunk c[], NewChunk nc) {
    CBlock block = new CBlock(c, ofs, len);
    for(int i = 0; i < block.rows(); i++) {
      nc.addNum(func.apply(block.row(i)));
    }
  }

  @Override
  protected Class<CFunc1> getFuncType() {
    return CFunc1.class;
  }
}
