package ai.h2o.automl.targetencoding.strategy;

import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;

import static org.junit.Assert.*;

public class ThresholdTEApplicationStrategyTest extends water.TestUtil {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void shouldReturnCatColumnsWithCardinalityHigherThanThresholdTest() {
    Frame fr=null;

    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      printOutColumnsMetadata(fr);
      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
      assertArrayEquals(new String[]{"cabin", "home.dest"}, strategy.getColumnsToEncode());

    } finally {
      if(fr != null) fr.delete();
    }
  }
  
  @Test
  public void thresholdValueIsIncludedTest() {
    Frame fr=null;

    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      printOutColumnsMetadata(fr);
      //Cardinality of the cabin column is 186
      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 186);
      assertArrayEquals(new String[]{"cabin", "home.dest"}, strategy.getColumnsToEncode());
      
      TEApplicationStrategy strategy2 = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 187);
      assertArrayEquals(new String[]{"home.dest"}, strategy2.getColumnsToEncode());

    } finally {
      if(fr != null) fr.delete();
    }
  }
}
