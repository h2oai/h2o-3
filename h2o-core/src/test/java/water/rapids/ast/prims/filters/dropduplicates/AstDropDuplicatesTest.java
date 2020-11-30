package water.rapids.ast.prims.filters.dropduplicates;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Session;
import water.rapids.Val;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class AstDropDuplicatesTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void testDropDuplicates() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 2d, 2d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 3d, 3d})
          .withChunkLayout(7)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();

      Val val = Rapids.exec(String.format("(dropdup %s ['C1', 'C2'] first)", frame._key.toString()));
      assertNotNull(val);
      assertTrue(val.isFrame());
      final Frame deduplicatedFrame = Scope.track(val.getFrame());
      assertEquals(2, deduplicatedFrame.numRows());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDropDuplicatesColumnRange() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 2d, 2d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 3d, 3d})
          .withChunkLayout(7)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .withName("test")
          .build();

      Val val = Rapids.exec(String.format("(dropdup %s [0:1] first)", frame._key.toString()));
      assertNotNull(val);
      assertTrue(val.isFrame());
      final Frame deduplicatedFrame = Scope.track(val.getFrame());
      assertEquals(2, deduplicatedFrame.numRows());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDropDuplicatesUnknownColumnName() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 2d, 2d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 3d, 3d})
          .withChunkLayout(7)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Unknown column name: 'C3'");
      Rapids.exec(String.format("(dropdup %s ['C1', 'C3'] first)", frame._key.toString()));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDropDuplicatesInvalidColumnRange() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 2d, 2d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 3d, 3d})
          .withChunkLayout(7)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .withName("test")
          .build();
      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("No such column index: '2', frame has 2 columns,maximum index is 1.");
      Rapids.exec(String.format("(dropdup %s [0:3] first)", frame._key.toString()));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDropDuplicatesColumnIndexSet() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 2d, 2d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 3d, 3d})
          .withChunkLayout(7)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .withName("test")
          .build();
      Val val = Rapids.exec(String.format("(dropdup %s [0,1] first)", frame._key.toString()));
      assertNotNull(val);
      assertTrue(val.isFrame());
      final Frame deduplicatedFrame = Scope.track(val.getFrame());
      assertEquals(2, deduplicatedFrame.numRows());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testStringColumnPresent() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withDataForCol(0, new double[]{1d, 2d, 3d})
              .withDataForCol(1, new String[]{"A", "B", "C"})
              .withVecTypes(Vec.T_NUM, Vec.T_STR)
              .build();

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Column 'C2' is of unsupported type String for row de-duplication.");
      Rapids.exec(String.format("(dropdup %s ['C1', 'C2'] first)", frame._key.toString()));
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void testDropUniquesOnColumnSubset() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2", "C3", "C4")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 2d, 2d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 3d, 3d})
          .withDataForCol(2, new double[]{3d, 2d, 2d, 2d, 2d, 3d, 3d})
          .withDataForCol(3, new double[]{4d, 2d, 2d, 2d, 2d, 3d, 3d})
          .withChunkLayout(7)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
          .build();

      Val val = Rapids.exec(String.format("(dropdup %s ['C1', 'C2'] first)", frame._key.toString()));
      assertNotNull(val);
      assertTrue(val.isFrame());
      final Frame deduplicatedFrame = Scope.track(val.getFrame());
      assertEquals(2, deduplicatedFrame.numRows());
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
    } finally {
      Scope.exit();
    }
  }

}
