package water.fvec;

import org.junit.*;

import water.TestUtil;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.fail;

public class C16ChunkTest extends TestUtil {

  static UUID u(long lo, long hi) { return new UUID(hi, lo);}

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  UUID[] sampleVals = new UUID[]{
      u(6, 6),
      u(C16Chunk._LO_NA, C16Chunk._HI_NA + 1),
      u(C16Chunk._LO_NA, C16Chunk._HI_NA - 1),
      u(C16Chunk._LO_NA+1, C16Chunk._HI_NA),
      u(C16Chunk._LO_NA-1, C16Chunk._HI_NA),
      u(Long.MIN_VALUE+1, 0),
      u(Long.MIN_VALUE+1, 1L),
      u(Long.MIN_VALUE+1, -1L),
      u(Long.MIN_VALUE+1, Long.MIN_VALUE+1),
      u(0L, Long.MIN_VALUE+1),
      u(1L, Long.MIN_VALUE+1),
      u(-1L, Long.MIN_VALUE+1),
      u(Long.MAX_VALUE-1, 0),
      u(Long.MAX_VALUE-1, 1L),
      u(Long.MAX_VALUE-1, -1L),
      u(0L, Long.MAX_VALUE-1),
      u(1L, Long.MAX_VALUE-1),
      u(-1L, Long.MAX_VALUE-1),
      u(Long.MAX_VALUE-1, Long.MAX_VALUE-1),
      u(Long.MAX_VALUE, 0),
      u(Long.MAX_VALUE, 1L),
      u(Long.MAX_VALUE, -1L),
      u(0L, Long.MAX_VALUE),
      u(1L, Long.MAX_VALUE),
      u(-1L, Long.MAX_VALUE),
      u(Long.MAX_VALUE, Long.MAX_VALUE),
      u(0L, 0),
      u(0L, 1L),
      u(0L, -1L),
      u(1L, 0L),
      u(-1L, 0L),
      u(1L, 0L),
      u(1, 1L),
      u(1, -1L),
      u(-1L, 1L),
      u(12312421425L, 12312421426L),
      u(23523523423L, 23523523424L),
      u(-823048234L,  -823048235L),
      u(-123123L,     -123124L)};

  NewChunk buildTestData(boolean withNA) {
    NewChunk nc = new NewChunk(null, 0);
    if (withNA) nc.addNA();
    for (UUID u : sampleVals) nc.addUUID(u.getLeastSignificantBits(), u.getMostSignificantBits());
    nc.addNA();
    return nc;
  }

  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      boolean haveNA = l == 1;
      NewChunk nc = buildTestData(haveNA);
      int len = nc.len();
      Chunk cc = nc.compress();
      Assert.assertEquals(sampleVals.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof C16Chunk);
      checkChunk(cc, l, haveNA);
      nc = cc.extractRows(new NewChunk(null, 0),0,len);
      Assert.assertEquals(sampleVals.length + 1 + l, nc._len);
      checkChunk(nc, l, haveNA);

      Chunk cc2 = nc.compress();
      Assert.assertEquals(sampleVals.length + 1 + l, cc._len);
      Assert.assertTrue(cc2 instanceof C16Chunk);
      checkChunk(cc2, l, haveNA);

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test
  public void test_illegal_values() {
    Chunk cc = buildTestData(false).compress();
    try {
      cc.set_impl(4, C16Chunk._LO_NA, C16Chunk._HI_NA);
      fail("Expected a failure on adding an illegal value");
    } catch(IllegalArgumentException iae) {
      // as expected
    }
  }

  private UUID uuidAt(Chunk cc, int i) {
    return u(cc.at16l(i), cc.at16h(i));
  }

  private void checkChunk(Chunk cc, int l, boolean haveNA) {
    if (haveNA) Assert.assertTrue(cc.isNA(0));
    if (haveNA) Assert.assertTrue(cc.isNA_abs(0));
    for (int i = 0; i < sampleVals.length; ++i) {
      UUID expected = sampleVals[i];
      long expectedLo = expected.getLeastSignificantBits();
      long expectedHi = expected.getMostSignificantBits();
      Assert.assertEquals(expectedLo, cc.at16l(l + i));
      Assert.assertEquals(expectedLo, cc.at16l_abs(l + i));
      Assert.assertEquals(expectedHi, cc.at16h(l + i));
      Assert.assertEquals(expectedHi, cc.at16h_abs(l + i));
    }
    Assert.assertTrue(cc.isNA(sampleVals.length + l));
    Assert.assertTrue(cc.isNA_abs(sampleVals.length + l));
  }

}
