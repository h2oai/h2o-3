package water.parser;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import water.Key;
import water.fvec.AppendableVec;
import water.fvec.Chunk;
import water.fvec.Vec;

import java.util.Random;

public class FVecParseWriterTest {

  private FVecParseWriter writer;

  @Before
  public void setup() {
    writer = makeWriter();
  }

  @Test
  public void addNumColLosesPrecision() { // needs to be fixed: PUBDEV-5840
    writer.addNumCol(0, Math.PI);
    double h2oPI = writer._nvs[0].compress().atd(0);
    Assert.assertEquals(h2oPI, Math.PI, 1e-15);
    Assert.assertNotEquals(h2oPI, Math.PI, 0);
  }

  @Test
  public void testBackwardsCompatibility() {
    Random r = new Random(42);
    FVecParseWriter wr2 = makeWriter();
    for (int i = 0; i < 2000; i++) {
      double v = r.nextDouble();
      wr2.addNumCol(0, v);
      oldAddNumDecompose(v, 0, writer);
    }
    Chunk nc = writer._nvs[0].compress();
    Chunk nc2 = wr2._nvs[0].compress();
    for (int i = 0; i < nc._len; i++) {
      Assert.assertEquals(nc2.atd(i), nc.atd(i), 0);
    }
  }

  private static void oldAddNumDecompose(final double value, int colIdx, FVecParseWriter w) {
    double d = value;
    int exp = 0;
    long number = (long)d;
    while (number != d) {
      d *= 10;
      --exp;
      number = (long) d;
    }
    w.addNumCol(colIdx, number, exp);
  }

  @Test(timeout = 10000)
  public void addNumCol() {
    double values[] = {2E19, -123.123, 0.0, 1.0, Math.exp(1), 3};

    for (double v : values)
      writer.addNumCol(0, v);

    Chunk ds = writer._nvs[0].compress();

    for (int i = 0; i < values.length; i++)
      Assert.assertEquals(i+"th values differ", values[i], ds.atd(i), 0);
  }

  private static FVecParseWriter makeWriter() {
    byte bits[] = new byte[512];
    bits[0] = Key.VEC;
    AppendableVec vec = new AppendableVec(Key.<Vec>make(bits), Vec.T_NUM);
    return new FVecParseWriter(
            Vec.VectorGroup.VG_LEN1,
            1,
            new Categorical[1],
            new byte[]{Vec.T_NUM},
            1,
            new AppendableVec[]{vec},
            null
    );
  }

}