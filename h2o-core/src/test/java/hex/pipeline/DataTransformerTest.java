package hex.pipeline;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class DataTransformerTest {
  
  
  @Test
  public void test_transform() {
    try {
      Scope.enter();
      DataTransformer dt = new AddRandomColumnTransformer("foo");
      final Frame fr = Scope.track(new TestFrameBuilder()
              .withColNames("one", "two", "target")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 2, 3))
              .withDataForCol(1, ard(1, 2, 3))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .build());

      Frame transformed = Scope.track(dt.transform(fr));
      assertEquals(4, transformed.names().length);
      assertArrayEquals(new String[]{"one", "two", "target", "foo"}, transformed.names());
    } finally {
      Scope.exit();
    }
  }
  
  
  public static class AddRandomColumnTransformer extends DataTransformer {
    
    private String name;

    public AddRandomColumnTransformer(String name) {
      this.name = name;
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      Frame tr = new Frame(fr);
      tr.add(name, tr.anyVec().makeRand(2022));
      return tr;
    }
  }
  
  public static class FrameTrackerAsTransformer extends DataTransformer {
    
    public static class Transformation {
      String frameId;
      FrameType type;
      boolean is_cv;

      public Transformation(String frameId, FrameType type, boolean is_cv) {
        this.frameId = frameId;
        this.type = type;
        this.is_cv = is_cv;
      }
    }
    
    transient List<Transformation> transformations = new ArrayList<>();

    @Override
    protected void doPrepare(PipelineContext context) {
      transformations.clear();
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      if (fr == null) return null;
      transformations.add(new Transformation(fr.getKey().toString(), type, context._params._is_cv_model));
      return fr;
    }
  }
}
