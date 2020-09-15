package hex.genmodel.easy;

import hex.genmodel.CategoricalEncodings;
import hex.genmodel.GenModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OneHotEncoderColumnMapperTest {

  @Mock
  private GenModel mockModel;

  @Test
  public void create() {
    Map<String, Integer> expected = new HashMap<>();
    expected.put("col1", 1);
    expected.put("col2", 0);
    expected.put("col3", 5);

    when(mockModel.getOrigNames()).thenReturn(new String[]{"col1", "col2", "col3", "response"});
    when(mockModel.getOrigDomainValues()).thenReturn(new String[][]{
            new String[]{"a", "b", "c"},
            null,
            new String[]{"x"},
            new String[]{"any", "thing"}
    });
    when(mockModel.getOrigNumCols()).thenReturn(3);

    Map<String, Integer> result = CategoricalEncodings.OneHotExplicit.createColumnMapping(mockModel);
    assertEquals(expected, result);
  }

}
