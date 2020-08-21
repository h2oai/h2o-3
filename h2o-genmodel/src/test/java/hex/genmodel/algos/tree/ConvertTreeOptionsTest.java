package hex.genmodel.algos.tree;

import org.junit.Test;

import static org.junit.Assert.*;

public class ConvertTreeOptionsTest {

  @Test
  public void enableCheckTreeConsistency() {
    ConvertTreeOptions opts = new ConvertTreeOptions();
    assertFalse(opts._checkTreeConsistency);

    assertTrue(opts.withTreeConsistencyCheckEnabled()._checkTreeConsistency);
  }
}
