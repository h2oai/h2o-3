package hex.genmodel.easy;

import hex.genmodel.CategoricalEncoding;
import hex.genmodel.GenModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EnumLimitedDomainMapConstructorTest {

  @Mock
  private GenModel mockModel;

  @Test
  public void create() {
    Map<String, Integer> columnNameToIndex = new HashMap<>();
    columnNameToIndex.put("col1", 0);
    columnNameToIndex.put("col2", 1);
    columnNameToIndex.put("col3", 2);
    columnNameToIndex.put("col4", 3);
    columnNameToIndex.put("col5", 4);
    columnNameToIndex.put("col6", 5);
    columnNameToIndex.put("col7", 6);

    
    when(mockModel.getOrigNames()).thenReturn(new String[]{"col1", "col2", "col3", "col4", "col5", "col6", "col7"});
    when(mockModel.getOrigDomainValues()).thenReturn(new String[][]{
            null,
            new String[]{"a", "b", "c", "d"},
            null,
            null,
            null,
            null,
            new String[]{"a", "e", "f", "g", "h", "i", "j"},
            new String[]{"any", "thing"}
    });
    when(mockModel.getDomainValues()).thenReturn(new String[][]{
            null,
            new String[]{"b", "c", "a"},
            null,
            null,
            null,
            null,
            new String[]{"g", "h", "f", "other"},
            new String[]{"any", "thing"}
    });
   
    when(mockModel.getOrigNumCols()).thenReturn(7);
    
    Map<Integer, CategoricalEncoder> dm = CategoricalEncoding.EnumLimited.createCategoricalEncoders(mockModel, columnNameToIndex);
    assertEquals(new HashSet<>(Arrays.asList(1, 6)), dm.keySet());
    for (CategoricalEncoder ce : dm.values()) {
      assertTrue(ce instanceof EnumLimitedEncoder);
    }
  }
}
