package water.udf;

import com.google.common.io.Files;
import org.junit.Test;
import water.udf.fp.Function;
import water.udf.fp.Predicate;
import water.udf.fp.PureFunctions;
import water.udf.specialized.EnumColumn;
import water.udf.specialized.Enums;
import water.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static water.udf.specialized.Dates.Dates;
import static water.udf.specialized.Doubles.Doubles;
import static water.udf.specialized.Strings.Strings;

/**
 * Test for UDF
 */
public class UdfEnumTest extends UdfTestBase {
  
  int requiredCloudSize() { return 5; }

  @Test
  public void testOfEnums() throws Exception {
    EnumColumn c = willDrop(Enums.enums(new String[]{"Red", "White", "Blue"})
        .newColumn(1 << 20, new Function<Long, Integer>() {
       public Integer apply(Long i) { return (int)( i % 3); }
    }));
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

  @Test
  public void testOfEnumFun() throws Exception {
    final String[] domain = {"Red", "White", "Blue"};
    Column<Integer> x = willDrop(Enums.enums(domain)
        .newColumn(1 << 20, new Function<Long, Integer>() {
           public Integer apply(Long i) { return (int)( i % 3); }
        }));

    Column<String> y = new FunColumn<>(new Function<Integer, String>() {
      public String apply(Integer i) { return domain[i]; }
    }, x);
    
    assertEquals("Red", y.apply(0));
    assertEquals("Red", y.apply(42));
    assertEquals("White", y.apply(100));
    assertEquals("Blue", y.apply(20000));
  }

}