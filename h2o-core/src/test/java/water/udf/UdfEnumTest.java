package water.udf;

import org.junit.Test;
import water.udf.fp.Function;
import water.udf.specialized.EnumColumn;
import water.udf.specialized.Enums;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test for UDF
 */
public class UdfEnumTest extends UdfTestBase {

  int requiredCloudSize() { return 1; }

  final String[] domain = {"Red", "White", "Blue"};
  
  @Test
  public void testOfEnums() throws Exception {
    EnumColumn c = generate(1 << 20);
    assertEquals(0, c.apply(0).intValue());
    assertEquals(0, c.apply(42).intValue());
    assertEquals(1, c.apply(100).intValue());
    assertEquals(2, c.apply(20000).intValue());

    final Enums factory2 = Enums.enums(new String[]{"Red", "White", "Blue"});
    Column<Integer> materialized = factory2.materialize(c);

    assertNotNull(materialized);
    
    for (int i = 0; i < 100000; i++) {
      assertEquals(c.apply(i), materialized.apply(i));
    }
  }

  final Enums sampleEnumeration = Enums.enums(new String[]{"Red", "White", "Blue"});

  EnumColumn generate(int n) throws IOException {
    return willDrop(sampleEnumeration
        .newColumn(n, new Function<Long, Integer>() {
       public Integer apply(Long i) { return (int)( i % 3); }
    }));
  }

  @Test
  public void testOfEnumFun() throws Exception {
    Column<Integer> x =generate(1 << 20);

    Column<String> y = new FunColumn<>(new Function<Integer, String>() {
      public String apply(Integer i) { return domain[i]; }
    }, x);
    
    assertEquals("Red", y.apply(0));
    assertEquals("Red", y.apply(42));
    assertEquals("White", y.apply(100));
    assertEquals("Blue", y.apply(20000));
  }

  @Test
  public void testOfOneHotColumn() throws Exception {
    EnumColumn x = generate(100);
    x.set(42, null);
    Column<List<Integer>> encodedColumn = x.oneHotEncode();

    assertEquals(Arrays.asList(0,1,0,0), encodedColumn.apply(7));
    final List<Integer> at42 = encodedColumn.apply(42);
    assertEquals(Arrays.asList(0,0,0,1), at42);
    
  }
  @Test
  public void testOfOneHotFrame() throws Exception {
    EnumColumn x = generate(100);
    x.set(42, null);

    UnfoldingFrame<Integer, DataColumn<Integer>> encodedFrame = x.oneHotEncodedFrame("test");

    assertArrayEquals(
        new String[]{"test.Red","test.White","test.Blue","test.missing(NA)"},
        encodedFrame.names());

    List<DataColumn<Integer>> columns = encodedFrame.materialize();

      for (int i = 0; i < x.size(); i++) {
      for (int j = 0; j < 3; j++) {
        int expected = (i != 42 && i%3 == j) ? 1 : 0;
        int actual = columns.get(j).get(i);
        assertEquals("column " + j + ", row " + i, expected, actual);
      }
      boolean na = columns.get(3).get(i) == 1;
      assertEquals(i == 42, na);
    }
  }

}