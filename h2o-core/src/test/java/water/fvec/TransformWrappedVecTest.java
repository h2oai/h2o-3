package water.fvec;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.MRTask;
import water.rapids.ast.AstFunction;
import water.rapids.Rapids;
import water.runner.CloudSize;
import water.runner.H2ORunner;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TransformWrappedVecTest {
  
  @Test public void testInversion() {
    Vec v=null;
    try {
      v = Vec.makeZero(1<<20);
      AstFunction ast = (AstFunction) Rapids.parse("{ x . (- 1 x) }");
      Vec iv = new TransformWrappedVec(v, ast);
      new MRTask() {
        @Override public void map(Chunk c) {
          for(int i=0;i<c._len;++i)
            if( c.atd(i)!=1 )
              throw new RuntimeException("moo");
        }
      }.doAll(iv);
      iv.remove();
    } finally {
      if( null!=v ) v.remove();
    }
  }
}
