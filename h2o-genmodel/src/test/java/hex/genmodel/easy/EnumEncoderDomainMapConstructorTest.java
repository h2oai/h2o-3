package hex.genmodel.easy;

import hex.genmodel.easy.stub.TestMojoModel;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class EnumEncoderDomainMapConstructorTest {

  @Test
  public void create() {

    TestMojoModel testMojoModel = new TestMojoModel();

    Map<String, Integer> columnMapping = new HashMap<String, Integer>() {{
      put("embarked", 0);
      put("age", 1);
      put("sex", 2);
    }};
    Map<Integer, CategoricalEncoder> domainMap = new EnumEncoderDomainMapConstructor(testMojoModel, columnMapping).create();

    checkEncode(domainMap, 0, 0, "S");
    checkEncode(domainMap, 1, 0, "Q");
    checkEncode(domainMap, 0, 2, "male");
    checkEncode(domainMap, 1, 2, "female");
    
    assertFalse(domainMap.containsKey(1));
  }
  
  private static void checkEncode(Map<Integer, CategoricalEncoder> domainMap, int expectedLabel, int colIndex, String levelName) {
    double[] actual = new double[3];
    Arrays.fill(actual, Double.NaN);
    double[] expected = new double[3];
    Arrays.fill(expected, Double.NaN);
    expected[colIndex] = expectedLabel;

    domainMap.get(colIndex).encodeCatValue(levelName, actual);
    
    assertArrayEquals(expected, actual, 0);
  }

}
