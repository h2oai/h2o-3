package water.fvec;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import water.*;
import water.junit.rules.ScopeTracker;
import water.rapids.ast.AstFunction;
import water.rapids.Rapids;

public class TransformWrappedVecTest extends TestUtil {

  @Rule
  public transient ScopeTracker _tracker = new ScopeTracker();
  
  @BeforeClass
  public static void setup() { 
    stall_till_cloudsize(1);
  }

  
  @Test
  public void testAstInversion() {
    Vec v = _tracker.track(Vec.makeZero(1<<20));
    AstFunction ast = (AstFunction) Rapids.parse("{ x . (- 1 x) }");
    Vec iv = _tracker.track(new TransformWrappedVec(v, ast));
    new MRTask() {
      @Override
      public void map(Chunk c) {
        for (int i = 0; i < c._len; i++) 
          if (c.atd(i) != 1)
            throw new RuntimeException("moo");
      }
    }.doAll(iv);
  }

  @Test
  public void testCustomTransform() {
    Frame f = new TestFrameBuilder()
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ard(3.0, 1.1, 6.0))
            .withDataForCol(1, ard(1.4, 2.2, 3.6))
            .withDataForCol(2, ard(7.0, 0.0, -9.0))
            .withDataForCol(3, ard(3.8, 1.1, 0.2))
            .build();
    Vec expected = f.remove(3);
    RowAverageTransformFactory tf = new RowAverageTransformFactory();
    Vec tv = _tracker.track(new TransformWrappedVec(f.vecs(), tf));
    Vec result = new MRTask() {
      @Override
      public void map(Chunk c, NewChunk nc) {
        for (int i = 0; i < c._len; i++)
          nc.addNum(c.atd(i));
      }
    }.doAll(Vec.T_NUM, tv).outputFrame().vec(0);
    _tracker.track(result);
    assertVecEquals(expected, result, 1e-6);
  }

  private static class RowAverageTransformFactory
          extends Iced<RowAverageTransformFactory>
          implements TransformWrappedVec.TransformFactory<RowAverageTransformFactory>  {
    @Override
    public TransformWrappedVec.Transform create(int n_inputs) {
      return new RowAverageTransform(n_inputs);
    }
  }

  private static class RowAverageTransform implements TransformWrappedVec.Transform {
    private final int _n;
    double _sum;

    public RowAverageTransform(int n) {
      _n = n;
    }

    @Override
    public void reset() {
      _sum = 0;
    }

    @Override
    public void setInput(int i, double value) {
      _sum += value;
    }

    @Override
    public double apply() {
      return _sum / _n;
    }
  }
  
}
