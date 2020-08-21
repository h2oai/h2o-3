package water.udf;

import org.junit.Ignore;

import water.fvec.Chunk;
import water.fvec.NewChunk;

@Ignore("Support for tests, but no actual tests here")
public class CFunc2Task extends CFuncTask<CFunc2, CFunc2Task> {

  private final int len1;
  private final int len2;
  private final int ofs1;
  private final int ofs2;

  public CFunc2Task(CFuncRef cFuncRef, int ofs1, int len1, int ofs2, int len2) {
    super(cFuncRef);
    this.len1 = len1;
    this.len2 = len2;
    this.ofs1 = ofs1;
    this.ofs2 = ofs2;

  }
  public CFunc2Task(CFuncRef cFuncRef, int len1, int len2) {
    this(cFuncRef, 0, len1, len1, len2);
  }

  @Override
  public void map(Chunk c[], NewChunk nc) {
    CBlock block1 = new CBlock(c, ofs1, len1);
    CBlock block2 = new CBlock(c, ofs2, len2);
    for(int i = 0; i < block1.rows(); i++) {
      nc.addNum(func.apply(block1.row(i), block2.row(i)));
    }
  }

  @Override
  protected Class<CFunc2> getFuncType() {
    return CFunc2.class;
  }
}
