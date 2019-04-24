package water;

import org.junit.Ignore;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

@Ignore // prepackaged small H2O Frames
public class TestFrameCatalog {

  public static Frame oneChunkFewRows() {
    return new TestFrameBuilder()
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, new double[]{1.2, 3.4, 5.6})
            .withDataForCol(1, new double[]{-1, 0, 1})
            .withDataForCol(2, new String[]{"a", "b", "a"})
            .withDataForCol(3, new String[]{"y", "y", "n"})
            .build();
  }

}
