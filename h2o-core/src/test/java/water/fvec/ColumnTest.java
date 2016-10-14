package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.functional.Function;

import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Test for Column interface implementations
 */
public class ColumnTest extends TestUtil {

  @BeforeClass
  static public void setup() {  stall_till_cloudsize(1); }

  @Test
  public void testOnVectorIsNA() throws Exception {
    Vec v = willDrop(Vec.makeFromFunction(1 << 20, new Function<Long, Double>() {
      @Override public Double apply(Long i) {
        return (i > 10 && i < 20) ? null : Math.sin(i);
      }
    }));

    Col.Column<Double> c = new Col.OfDoubles(v);
    assertFalse(c.get(10).isNaN());
    assertTrue(c.get(11).isNaN());
    assertTrue(c.get(19).isNaN());
    assertFalse(c.get(20).isNaN());
  }

  @Test
  public void testGetString() throws Exception {
    Vec v = willDrop(Vec.makeFromFunction(1 << 20, new Function<Long, Double>() {
      @Override public Double apply(Long i) {
        return (i > 10 && i < 20) ? null : i*10.0;
      }
    }));

    Col.Column<Double> c = new Col.OfDoubles(v);
    assertEquals("100.0", c.getString(10));
    assertEquals("(N/A)", c.getString(12));
    assertEquals("(N/A)", c.getString(18));
    assertEquals("123450.0", c.getString(12345));
  }

  @Test
  public void testVec() throws Exception {
    Vec v = willDrop(Vec.makeFromFunction(1 << 20, new Function<Long, Double>() {
      @Override public Double apply(Long i) {
        return (i > 10 && i < 20) ? null : i*10.0;
      }
    }));
    Col.Column<Double> c = new Col.OfDoubles(v);
    assertEquals(v, c.vec());
  }

  @Test
  public void testOfDoubles() throws Exception {
    Vec v = willDrop(Vec.makeFromFunction(1 << 20, new Function<Long, Double>() {
      @Override public Double apply(Long i) {
        return i*5.0;
      }
    }));

    Col.Column<Double> c = new Col.OfDoubles(v);
    assertEquals(0.0, c.get(0), 0.000001);
    assertEquals(210.0, c.get(42), 0.000001);
    assertEquals(100000.0, c.get(20000), 0.000001);
    assertEquals(Vec.T_NUM, v.get_type());
  }

  @Test
  public void testOfStrings() throws Exception {
    Vec v = willDrop(Vec.makeFromFunction(1 << 20, new Function<Long, String>() {
      @Override public String apply(Long i) {
        return i == 42 ? null : "<<" + i + ">>";
      }
    }));
    Col.Column<String> c = new Col.OfStrings(v);
    assertEquals("<<0>>", c.get(0));
    assertEquals(null, c.get(42));
    assertEquals("<<2016>>", c.get(2016));
    assertEquals(Vec.T_STR, v.get_type());
  }

  @Test
  public void testOfEnums() throws Exception {
    Vec v = willDrop(Vec.makeFromFunction(1 << 20, new Function<Long, Integer>() {
      @Override public Integer apply(Long i) { return (int)( i % 3); }
    }));
    v.setDomain(new String[] {"Red", "White", "Blue"});
    Col.Column<Integer> c = new Col.OfEnums(v);
    assertEquals(0, c.get(0).intValue());
    assertEquals(0, c.get(42).intValue());
    assertEquals(1, c.get(100).intValue());
    assertEquals(2, c.get(20000).intValue());
    assertEquals(Vec.T_CAT, v.get_type());
  }

  @Test
  public void testOfDates() throws Exception {
    Vec v = willDrop(Vec.makeFromFunction(1 << 20, new Function<Long, Date>() {
      @Override public Date apply(Long i) {
        return new Date(i*3600000L*24);
      }
    }));
    Col.Column<Date> c = new Col.OfDates(v);
    assertEquals(new Date(0), c.get(0));
    assertEquals(new Date(70, 8, 15, 17, 0, 0), c.get(258));
    assertEquals(Vec.T_TIME, v.get_type());
  }

  @Test
  public void testOfUUIDs() throws Exception {
    Vec v = willDrop(Vec.makeFromFunction(1 << 20, new Function<Long, UUID>() {
      @Override public UUID apply(Long i) {
        return new UUID(i * 7, i * 13);
      }
    }));
    Col.Column<UUID> c = new Col.OfUUID(v);
    assertEquals(new UUID(0, 0), c.get(0));
    assertEquals(new UUID(258*7, 258*13), c.get(258));
    assertEquals(Vec.T_UUID, v.get_type());
  }

}