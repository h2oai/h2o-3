package ai.h2o.targetencoding.strategy;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static ai.h2o.targetencoding.TargetEncoderHelper.addKFoldColumn;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ThresholdTEApplicationStrategyTest extends water.TestUtil {

  @Test
  public void shouldReturnCatColumnsWithCardinalityHigherThanThresholdTest() {
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);

      String responseColumnName = "survived";
      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, 4,  new String[]{responseColumnName});
      assertArrayEquals(new String[]{"cabin", "home.dest"}, strategy.getColumnsToEncode());

    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void thresholdValueIsIncludedTest() {
    Scope.enter();

    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      
      //Cardinality of the cabin column is 186
      assertEquals(186, fr.vec("cabin").cardinality() );

      String responseColumnName = "survived";
      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, 186,  new String[]{responseColumnName});
      assertArrayEquals(new String[]{"cabin", "home.dest"}, strategy.getColumnsToEncode());
      
      TEApplicationStrategy strategy2 = new ThresholdTEApplicationStrategy(fr, 187,  new String[]{responseColumnName});
      assertArrayEquals(new String[]{"home.dest"}, strategy2.getColumnsToEncode());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void foldColumnShouldBeExcludedTest() {
    Scope.enter();

    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String foldColumnName = "fold";
      int threshold = 187;
      int nfoldsHigherThanThreshold = 200;
      addKFoldColumn(fr, foldColumnName, nfoldsHigherThanThreshold, 1234); 
      fr.replace(fr.find(foldColumnName), fr.vec(foldColumnName).toCategoricalVec());
      Scope.track(fr);

      String responseColumnName = "survived";
      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, threshold,  new String[]{responseColumnName});
      assertArrayEquals(new String[]{"home.dest", foldColumnName}, strategy.getColumnsToEncode());

      TEApplicationStrategy strategy2 = new ThresholdTEApplicationStrategy(fr, threshold,  new String[]{responseColumnName, foldColumnName});
      assertArrayEquals(new String[]{"home.dest"}, strategy2.getColumnsToEncode());

    } finally {
      Scope.exit();
    }
  }

}
