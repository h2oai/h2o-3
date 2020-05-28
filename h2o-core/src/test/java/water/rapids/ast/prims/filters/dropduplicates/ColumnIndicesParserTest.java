package water.rapids.ast.prims.filters.dropduplicates;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Val;
import water.rapids.vals.ValNums;
import water.rapids.vals.ValStr;
import water.rapids.vals.ValStrs;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertArrayEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ColumnIndicesParserTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void testParseSingleStringIndex() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1")
          .withDataForCol(0, new double[]{1d, 1d, 1d})
          .withVecTypes(Vec.T_NUM)
          .build();
      final Val value = new ValStr("C1");

      final int[] indices = ColumnIndicesParser.parseAndCheckComparedColumnIndices(frame, value);
      assertArrayEquals(new int[]{0}, indices);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParseStringIndices() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d})
          .withDataForCol(1, new double[]{1d, 1d, 1d})
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();
      final Val value = new ValStrs(new String[]{"C1", "C2"});

      final int[] indices = ColumnIndicesParser.parseAndCheckComparedColumnIndices(frame, value);
      assertArrayEquals(new int[]{0, 1}, indices);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParseIntegerIndices() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d})
          .withDataForCol(1, new double[]{1d, 1d, 1d})
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();
      final Val value = new ValNums(new double[]{0d, 1d});

      final int[] indices = ColumnIndicesParser.parseAndCheckComparedColumnIndices(frame, value);
      assertArrayEquals(new int[]{0, 1}, indices);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParseNonExistentSingleStringIndex() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1")
          .withDataForCol(0, new double[]{1d, 1d, 1d})
          .withVecTypes(Vec.T_NUM)
          .build();
      final Val value = new ValStr("NonExistentColumn");

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Unknown column name: 'NonExistentColumn'");
      ColumnIndicesParser.parseAndCheckComparedColumnIndices(frame, value);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParseNonExistentStringIndices() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d})
          .withDataForCol(1, new double[]{1d, 1d, 1d})
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();
      final Val value = new ValStrs(new String[]{"NonExistentColumn", "C2"});

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Unknown column name: 'NonExistentColumn'");
      ColumnIndicesParser.parseAndCheckComparedColumnIndices(frame, value);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParseNonExistentIntegerIndices() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d})
          .withDataForCol(1, new double[]{1d, 1d, 1d})
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();
      final Val value = new ValNums(new double[]{999d, 1d});

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("No such column index: '999', frame has 2 columns,maximum index is 1.");
      ColumnIndicesParser.parseAndCheckComparedColumnIndices(frame, value);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParseBadColumnTypeNums() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{Double.NaN, Double.NaN, Double.NaN})
          .withDataForCol(1, new double[]{1d, 1d, 1d})
          .withVecTypes(Vec.T_BAD, Vec.T_NUM)
          .build();
      final Val value = new ValNums(new double[]{0d, 1d});

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Column 'C1' is of unsupported type BAD for row de-duplication.");
      ColumnIndicesParser.parseAndCheckComparedColumnIndices(frame, value);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParseStringColumnTypeNums() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new String[]{"A", "B", "C"})
          .withDataForCol(1, new double[]{1d, 1d, 1d})
          .withVecTypes(Vec.T_STR, Vec.T_NUM)
          .build();
      final Val value = new ValNums(new double[]{0d, 1d});

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Column 'C1' is of unsupported type String for row de-duplication.");
      ColumnIndicesParser.parseAndCheckComparedColumnIndices(frame, value);
    } finally {
      Scope.exit();
    }
  }

}
