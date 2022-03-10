package hex.genmodel.easy;

import hex.genmodel.CategoricalEncoding;
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
public class EnumLimitedColumnMapperTest {

  @Mock
  private GenModel mockModel;

  @Test
  public void create() {
    Map<String, Integer> expected = new HashMap<>();
    expected.put("col1", 0);
    expected.put("col2", 1);
    expected.put("col3", 2);
    expected.put("col4", 3);
    expected.put("col5", 4);
    expected.put("col6", 5);
    expected.put("col7", 6); 
    
    when(mockModel.getOrigNames()).thenReturn(new String[]{"col1", "col2", "col3", "col4","col5", "col6", "col7"});

    Map<String, Integer> result = CategoricalEncoding.EnumLimited.createColumnMapping(mockModel);
    assertEquals(expected, result);
  }

}
