package ai.h2o.automl.targetencoding.strategy;

import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import static org.junit.Assert.*;

public class TEApplicationStrategyTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void KFoldSmokeTest() {
    Frame fr=null;
    
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      TEApplicationStrategy strategy = new AllCategoricalTEApplicationStrategy(fr, fr.vec("survived"));
      assertArrayEquals(new String[]{"sex", "cabin", "embarked", "home.dest"}, strategy.getColumnsToEncode());

    } finally {
      if(fr != null) fr.delete();
    }
  }
}
