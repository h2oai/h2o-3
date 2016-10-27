package water.udf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Vec;

import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.*;
import water.udf.*;
import static water.udf.Col.*;

/**
 * Test for Column interface implementations
 */
public class ColumnTest extends TestUtil {

  @BeforeClass
  static public void setup() {  stall_till_cloudsize(1); }

  @Test
  public void testIsNA() throws Exception {
    Column<Double> c = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      @Override public Double apply(Long i) {
        return (i > 10 && i < 20) ? null : Math.sin(i);
      }
    }));

    assertFalse(c.get(10).isNaN());
    assertTrue(c.get(11).isNaN());
    assertTrue(c.get(19).isNaN());
    assertFalse(c.get(20).isNaN());
  }

  @Test public void testGetString() throws Exception {
    Column<Double> c = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      @Override public Double apply(Long i) {
        return (i > 10 && i < 20) ? null : i*10.0;
      }
    }));

    assertEquals("100.0", c.getString(10));
    assertEquals("(N/A)", c.getString(12));
    assertEquals("(N/A)", c.getString(18));
    assertEquals("123450.0", c.getString(12345));
  }

  @Test
  public void testOfDoubles() throws Exception {
    Column<Double> c =willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      @Override public Double apply(Long i) {
        return i*5.0;
      }
    }));

    assertEquals(0.0, c.get(0), 0.000001);
    assertEquals(210.0, c.get(42), 0.000001);
    assertEquals(100000.0, c.get(20000), 0.000001);
    assertEquals(Vec.T_NUM, c.vec().get_type());
  }

  @Test
  public void testOfStrings() throws Exception {
    Column<String> c = willDrop(Strings.newColumn(1 << 20, new Function<Long, String>() {
      @Override public String apply(Long i) {
        return i == 42 ? null : "<<" + i + ">>";
      }
    }));
    assertEquals("<<0>>", c.get(0));
    assertEquals(null, c.get(42));
    assertEquals("<<2016>>", c.get(2016));
    assertEquals(Vec.T_STR, c.vec().get_type());
  }

  @Test
  public void testOfEnums() throws Exception {
    Column<Integer> c = willDrop(Enums
        .newColumn(1 << 20, new String[] {"Red", "White", "Blue"}, new Function<Long, Integer>() {
      @Override public Integer apply(Long i) { return (int)( i % 3); }
    }));
    assertEquals(0, c.get(0).intValue());
    assertEquals(0, c.get(42).intValue());
    assertEquals(1, c.get(100).intValue());
    assertEquals(2, c.get(20000).intValue());
    assertEquals(Vec.T_CAT, c.vec().get_type());
  }

  @Test
  public void testOfDates() throws Exception {
    Column<Date> c = willDrop(Dates.newColumn(1 << 20, new Function<Long, Date>() {
      @Override public Date apply(Long i) {
        return new Date(i*3600000L*24);
      }
    }));
    assertEquals(new Date(0), c.get(0));
    assertEquals(new Date(70, 8, 15, 17, 0, 0), c.get(258));
    assertEquals(Vec.T_TIME, c.vec().get_type());
  }

  @Test
  public void testOfUUIDs() throws Exception {
    Column<UUID> c = willDrop(UUIDs.newColumn(1 << 20, new Function<Long, UUID>() {
      @Override public UUID apply(Long i) {
        return new UUID(i * 7, i * 13);
      }
    }));
    assertEquals(new UUID(0, 0), c.get(0));
    assertEquals(new UUID(258*7, 258*13), c.get(258));
    assertEquals(Vec.T_UUID, c.vec().get_type());
  }

}