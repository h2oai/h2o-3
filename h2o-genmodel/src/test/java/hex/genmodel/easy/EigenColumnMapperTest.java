package hex.genmodel.easy;

import hex.genmodel.CategoricalEncodings;
import hex.genmodel.GenModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EigenColumnMapperTest {

  @Mock
  private GenModel mockModel;

  @Test
  public void create() {
    Map<String, Integer> expected = new HashMap<>();
    expected.put("col1", 0);
    expected.put("col2", 1);
    expected.put("col3", 2);
    expected.put("response", 3);

    when(mockModel.getOrigNames()).thenReturn(new String[]{"col1", "col2", "col3", "response"});

    Map<String, Integer> result = CategoricalEncodings.Eigen.createColumnMapping(mockModel);
    assertEquals(expected, result);
  }

}
