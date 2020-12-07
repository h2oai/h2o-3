package ai.h2o.targetencoding.strategy;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class AllCategoricalTEApplicationStrategyTest extends water.TestUtil {

  @Test public void shouldReturnAllCategoricalColumnTest() {
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);

      String responseColumnName = "survived";
      TEApplicationStrategy strategy = new AllCategoricalTEApplicationStrategy(fr, new String[] {responseColumnName});
      assertArrayEquals(new String[]{"sex", "cabin", "embarked", "home.dest"}, strategy.getColumnsToEncode());

    } finally {
      Scope.exit();
    }
  }
  
  @Test public void emptyIfNoCategoricalColumns() {
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      String responseColumnName = "survived";

      List<String> alpha = Arrays.asList("sex", "cabin", "embarked", "home.dest");
      alpha.forEach(item-> fr.remove(item));

      TEApplicationStrategy strategy = new AllCategoricalTEApplicationStrategy(fr, new String[] {responseColumnName});
      assertArrayEquals(new String[]{}, strategy.getColumnsToEncode());

    } finally {
      Scope.exit();
    }
  }
}
