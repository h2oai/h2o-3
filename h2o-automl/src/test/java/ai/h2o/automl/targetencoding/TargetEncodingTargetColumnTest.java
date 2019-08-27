package ai.h2o.automl.targetencoding;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.*;
import water.fvec.*;

import java.io.File;

import static org.junit.Assert.*;

public class TargetEncodingTargetColumnTest extends TestUtil {
  
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void binaryCategoricalTargetColumnWorksTest() throws Exception {
    final File fr1ExportFile = temporaryFolder.newFile();
    final File fr2ExportFile = temporaryFolder.newFile();
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("NO", "YES", "NO"))
              .withDataForCol(1, ar(1, 2, 3))   // we need extra column because parsing from file will fail for csv with one column. For timeseries do we need more then one column?
              .build();
      Scope.track(fr);

      final Frame fr2 = new TestFrameBuilder()
              .withColNames("ColA2", "ColB2")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("YES", "NO", "NO"))
              .withDataForCol(1, ar(1, 2, 3))
              .build();
      Scope.track(fr2);

      Frame.export(fr, fr1ExportFile.getAbsolutePath(), fr._key.toString(), true, 1)
              .get();
      Frame.export(fr2, fr2ExportFile.getAbsolutePath(), fr2._key.toString(), true, 1)
              .get();
      
      final Frame parsedFrame = parse_test_file(Key.make(), fr1ExportFile.getAbsolutePath());
      Scope.track(parsedFrame);
      final Frame parsedFrame2 = parse_test_file(Key.make(), fr2ExportFile.getAbsolutePath());
      Scope.track(parsedFrame2);

      String[] domains = parsedFrame.vec(0).domain();
      String[] domains2 = parsedFrame2.vec(0).domain();
      assertArrayEquals(domains, domains2);
    } finally {
      Scope.exit();
    }
  }

  @Test // BecauseRepresentationForCategoricalsIsNumericalInside frame
  public void weCanSumTargetColumnTest() throws Exception {
    final File frExportFile = temporaryFolder.newFile();
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("NO", "YES", "NO"))
              .withDataForCol(1, ar(1, 2, 3))
              .build();
      Scope.track(fr);
      
      Frame.export(fr, frExportFile.getAbsolutePath(), fr._key.toString() , true, 1)
              .get();

      final Frame parsedFrame = parse_test_file(Key.make(), frExportFile.getAbsolutePath());
      Scope.track(parsedFrame);

      assertEquals( 0,parsedFrame.vec(0).at8(0));
      assertEquals( 1,parsedFrame.vec(0).at8(1));

    } finally {
      Scope.exit();
    }
  }

  //Test that we can do sum and count on binary categorical column due to numerical representation under the hood.
  @Test
  public void groupThenAggregateWithoutFoldsForBinaryTargetTest() throws Exception {
    final File frExportFile = temporaryFolder.newFile();
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "a", "b"))
              .withDataForCol(1, ar("NO", "YES", "NO"))
              .build();

      Frame.export(fr, frExportFile.getAbsolutePath(), fr._key.toString(), true, 1)
              .get();

      final Frame parsedFrame = parse_test_file(Key.make(), frExportFile.getAbsolutePath(), true);
      Scope.track(parsedFrame);

      String[] teColumns = {"ColA"};
      TargetEncoder tec = new TargetEncoder(teColumns);

      Frame res = tec.groupThenAggregateForNumeratorAndDenominator(parsedFrame, teColumns[0], null, 1);

      Vec expectedSumColumn = vec( 1, 0);
      Vec expectedCountColumn = vec(2, 1);

      assertVecEquals(expectedSumColumn, res.vec(1), 1e-5);
      assertVecEquals(expectedCountColumn, res.vec(2), 1e-5);

      expectedSumColumn.remove();
      expectedCountColumn.remove();
      res.delete();

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void categoricalTargetHasCardinalityOfTwoTest() {
    String targetColumnName = "ColC";
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames(targetColumnName)
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("2", "6", "6", "6", "6", "2"))
              .build();
      Scope.track(fr);

      assertEquals(2, fr.vec(0).cardinality());
    } finally {
      Scope.exit();      
    }
  }
}
