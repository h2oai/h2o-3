package hex.segments;

import hex.Model;
import org.junit.Test;
import water.Key;

import static org.junit.Assert.*;

public class SegmentModelsUtilsTest {

  @Test
  public void makeUniqueModelKey() {
    Key<Model> mKey = SegmentModelsUtils.makeUniqueModelKey(Key.make("prefix_1"), 42);
    assertEquals("prefix_1_42", mKey.toString());
  }
}
