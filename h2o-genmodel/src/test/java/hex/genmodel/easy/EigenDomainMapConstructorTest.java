package hex.genmodel.easy;

import hex.genmodel.CategoricalEncodings;
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
public class EigenDomainMapConstructorTest {

  @Mock
  private GenModel mockModel;

  @Test
  public void create() {
    Map<String, Integer> columnNameToIndex = new HashMap<>();
    columnNameToIndex.put("col1", 1);
    columnNameToIndex.put("col2", 0);
    columnNameToIndex.put("col3", 3);
    
    when(mockModel.getOrigNames()).thenReturn(new String[]{"col1", "col2", "col3", "response"});
    when(mockModel.getOrigDomainValues()).thenReturn(new String[][]{
            new String[]{"a", "b", "c"},
            null,
            new String[]{"x"},
            new String[]{"any", "thing"}
    });
    when(mockModel.getOrigNumCols()).thenReturn(3);
    when(mockModel.getOrigProjectionArray()).thenReturn(new double[]{1.1, 2.2, 3.3, 4.4});
    
    Map<Integer, CategoricalEncoder> dm = CategoricalEncodings.Eigen.createCategoricalEncoders(mockModel, columnNameToIndex);
    assertEquals(new HashSet<>(Arrays.asList(1, 3)), dm.keySet());
    for (CategoricalEncoder ce : dm.values()) {
      assertTrue(ce instanceof EigenEncoder);
    }
  }
}
