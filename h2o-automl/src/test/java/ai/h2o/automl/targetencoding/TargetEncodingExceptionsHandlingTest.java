package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * This test is checking for data leakage in case of exception during execution.
 */
public class TargetEncodingExceptionsHandlingTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Test
  public void exceptionInPrepareEncodingTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b"))
            .withDataForCol(1, ar("2", "6", "6", "6"))
            .withDataForCol(2, ar(1, 2, 2, 3))
            .build();

    String[] teColumns = {"ColA"};
    String targetColumnName = "ColB";
    String foldColumnName = "fold_column";
    int targetIndex = fr.find(targetColumnName);

    TargetEncoder tec = new TargetEncoder(teColumns);
    TargetEncoder tecSpy = spy(tec);

    doThrow(new IllegalStateException("Fake exception")).when(tecSpy).filterOutNAsFromTargetColumn(fr, targetIndex);

    Map<String, Frame> targetEncodingMap = null;
    try {
      targetEncodingMap = tecSpy.prepareEncodingMap(fr, targetColumnName, foldColumnName);
      fail();
    } catch (IllegalStateException ex) {
      assertEquals("Fake exception", ex.getMessage());
    }

    if(targetEncodingMap != null) encodingMapCleanUp(targetEncodingMap);
  }

  @Test
  public void exceptionInPrepareEncodingAtTheEndTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b"))
            .withDataForCol(1, ar("2", "6", "6", "6"))
            .withDataForCol(2, ar(1, 2, 2, 3))
            .build();

    String[] teColumns = {"ColA"};
    String targetColumnName = "ColB";
    String foldColumnName = "fold_column";
    int targetIndex = fr.find(targetColumnName);

    TargetEncoder tec = new TargetEncoder(teColumns);
    TargetEncoder tecSpy = spy(tec);

    doThrow(new IllegalStateException("Fake exception")).when(tecSpy)
            .groupThenAggregateForNumeratorAndDenominator(any(Frame.class), eq(teColumns[0]), eq(foldColumnName), eq(targetIndex));

    Map<String, Frame> targetEncodingMap = null;
    try {
      targetEncodingMap = tecSpy.prepareEncodingMap(fr, targetColumnName, foldColumnName);
      fail();
    } catch (IllegalStateException ex) {
      assertEquals("Fake exception", ex.getMessage());
    }

    if(targetEncodingMap != null) encodingMapCleanUp(targetEncodingMap);
  }

  @Test
  public void exceptionInApplyEncodingKFOLDInsideCycleTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b"))
            .withDataForCol(1, ar("2", "6", "6", "6"))
            .withDataForCol(2, ar(1, 2, 2, 3))
            .build();

    String[] teColumns = {"ColA"};
    String targetColumnName = "ColB";
    String foldColumnName = "fold_column";

    TargetEncoder tec = new TargetEncoder(teColumns);
    TargetEncoder tecSpy = spy(tec);

    Map<String, Frame> targetEncodingMap = tecSpy.prepareEncodingMap(fr, targetColumnName, foldColumnName);

    Frame resultWithEncoding = null;

      doThrow(new IllegalStateException("Fake exception")).when(tecSpy).getOutOfFoldData(any(Frame.class), eq(foldColumnName), anyLong());

    try {
      resultWithEncoding = tecSpy.applyTargetEncoding(fr, targetColumnName, targetEncodingMap,
              TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, false, 0, false, 1234, true);
      fail();
    } catch (IllegalStateException ex) {
      assertEquals("Fake exception", ex.getMessage());
    }

    if(resultWithEncoding != null) resultWithEncoding.delete();
    if(targetEncodingMap != null) encodingMapCleanUp(targetEncodingMap);
  }

  @Test
  public void exceptionInApplyEncodingKFOLDTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b"))
            .withDataForCol(1, ar("2", "6", "6", "6"))
            .withDataForCol(2, ar(1, 2, 2, 3))
            .build();

    String[] teColumns = {"ColA"};
    String targetColumnName = "ColB";
    String foldColumnName = "fold_column";

    TargetEncoder tec = new TargetEncoder(teColumns);
    TargetEncoder tecSpy = spy(tec);

    Map<String, Frame> targetEncodingMap = tecSpy.prepareEncodingMap(fr, targetColumnName, foldColumnName);

    Frame resultWithEncoding = null;

    doThrow(new IllegalStateException("Fake exception")).when(tecSpy).removeNumeratorAndDenominatorColumns(any(Frame.class));

    try {
      resultWithEncoding = tecSpy.applyTargetEncoding(fr, targetColumnName, targetEncodingMap,
              TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, false, 0, false, 1234, true);
      fail();
    } catch (IllegalStateException ex) {
      assertEquals("Fake exception", ex.getMessage());
    }

    if(resultWithEncoding != null) resultWithEncoding.delete();
    if(targetEncodingMap != null) encodingMapCleanUp(targetEncodingMap);
  }

  @Test
  public void exceptionIsNotCausingKeysLeakageInApplyEncodingLOOTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "b", "b"))
            .withDataForCol(1, ar("2", "6", "6", "6"))
            .build();

    String[] teColumns = {"ColA"};
    String targetColumnName = "ColB";

    TargetEncoder tec = new TargetEncoder(teColumns);
    TargetEncoder tecSpy = spy(tec);

    Map<String, Frame> targetEncodingMap = tecSpy.prepareEncodingMap(fr, targetColumnName, null);

    Frame resultWithEncoding = null;

    // In most cases it is better to throw exception at the end of the logic we are going to test.
    // This way we will create as much objects/frames as possible prior to the exception.
    doThrow(new IllegalStateException("Fake exception")).when(tecSpy).removeNumeratorAndDenominatorColumns(any(Frame.class));

    try {
      resultWithEncoding = tecSpy.applyTargetEncoding(fr, targetColumnName, targetEncodingMap,
              TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut,false, 0, false, 1234, true);
      fail();
    } catch (IllegalStateException ex) {
      assertEquals("Fake exception", ex.getMessage());
    }

    if(resultWithEncoding != null) resultWithEncoding.delete();
    if(targetEncodingMap != null) encodingMapCleanUp(targetEncodingMap);
  }

  @Test
  public void exceptionIsNotCausingKeysLeakageInApplyEncodingNoneTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "b", "b"))
            .withDataForCol(1, ar("2", "6", "6", "6"))
            .build();

    String[] teColumns = {"ColA"};
    String targetColumnName = "ColB";

    TargetEncoder tec = new TargetEncoder(teColumns);
    TargetEncoder tecSpy = spy(tec);

    Map<String, Frame> targetEncodingMap = tecSpy.prepareEncodingMap(fr, targetColumnName, null);

    Frame resultWithEncoding = null;

    // In most cases it is better to throw exception at the end of the logic we are going to test.
    // This way we will create as much objects/frames as possible prior to the exception.
    doThrow(new IllegalStateException("Fake exception")).when(tecSpy).removeNumeratorAndDenominatorColumns(any(Frame.class));
//    doThrow(new IllegalStateException("Fake exception")).when(tecSpy).foldColumnIsInEncodingMapCheck(nullable(String.class), any(Frame.class));

    try {
      resultWithEncoding = tecSpy.applyTargetEncoding(fr, targetColumnName, targetEncodingMap,
              TargetEncoder.DataLeakageHandlingStrategy.None,false, 0, false, 1234, true);
      fail();
    } catch (IllegalStateException ex) {
      assertEquals("Fake exception", ex.getMessage());
    }

    if(resultWithEncoding != null) resultWithEncoding.delete();
    if(targetEncodingMap != null) encodingMapCleanUp(targetEncodingMap);
  }

  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }

}
