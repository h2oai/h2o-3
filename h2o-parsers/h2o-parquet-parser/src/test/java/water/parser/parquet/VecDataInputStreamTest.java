package water.parser.parquet;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.MRTask;
import water.TestUtil;
import water.fvec.*;
import water.persist.VecDataInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class VecDataInputStreamTest extends TestUtil {

  @BeforeClass
  static public void setup() { TestUtil.stall_till_cloudsize(1); }

  @Test
  public void testReadVecAsInputStream() throws Exception {
    Vec v0 = Vec.makeCon(0.0d, 10000L, 10, true);
    Vec v = makeRandomByteVec(v0);
    try {
      InputStream t = new TestInputStream(v);
      assertTrue(t.read() >= 0);
      assertTrue(t.skip(1) >= 0);
      assertTrue(t.read() >= 0);
      assertTrue(t.skip(5000) >= 0);
      assertTrue(t.read() >= 0);
      assertTrue(t.read(new byte[1333], 33, 67) >= 0);
      assertTrue(t.skip(100000L) >= 0);
      assertEquals(-1, t.read()); // reached the end of the stream
      assertEquals(0, t.available());
    } finally {
      if (v0 != null) v0.remove();
      if (v != null) v.remove();
    }
  }

  private static class TestInputStream extends InputStream {

    private InputStream ref;
    private InputStream tst;

    private TestInputStream(Vec v) {
      this.ref = new ByteArrayInputStream(chunkBytes(v));
      this.tst = new VecDataInputStream(v);
    }

    @Override
    public int read(byte[] b) throws IOException {
      byte[] ref_b = Arrays.copyOf(b, b.length);
      int res = tst.read(b);
      assertEquals(ref.read(ref_b), res);
      assertArrayEquals(ref_b, b);
      return res;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      byte[] ref_b = Arrays.copyOf(b, b.length);
      int res = tst.read(b, off, len);
      assertEquals(ref.read(ref_b, off, len), res);
      assertArrayEquals(ref_b, b);
      return res;
    }

    @Override
    public long skip(long n) throws IOException {
      long res = tst.skip(n);
      assertEquals(ref.skip(n), res);
      return res;
    }

    @Override
    public int available() throws IOException {
      int res = tst.available();
      assertEquals(ref.available(), res);
      return res;
    }

    @Override
    public int read() throws IOException {
      int res = tst.read();
      assertEquals(ref.read(), res);
      return res;
    }

    @Override
    public synchronized void mark(int readlimit) {
      throw new UnsupportedOperationException("Intentionally not implemented");
    }

    @Override
    public synchronized void reset() throws IOException {
      throw new UnsupportedOperationException("Intentionally not implemented");
    }

    @Override
    public boolean markSupported() {
      throw new UnsupportedOperationException("Intentionally not implemented");
    }

    @Override
    public void close() throws IOException {
      throw new UnsupportedOperationException("Intentionally not implemented");
    }
  }

  private static Vec makeRandomByteVec(Vec blueprint) {
    final Vec v0 = new Vec(blueprint.group().addVec(), blueprint._rowLayout, null, Vec.T_NUM);
    final int nchunks = v0.nChunks();
    new MRTask() {
      @Override protected void setupLocal() {
        for( int i=0; i<nchunks; i++ ) {
          Key k = v0.chunkKey(i);
          if (k.home()) {
            int len = (int) (v0.espc()[i + 1] - v0.espc()[i]);
            byte[] bytes = new byte[len];
            new Random(i).nextBytes(bytes);
            DKV.put(k,new C1NChunk(bytes),_fs);
          }
        }
      }
    }.doAllNodes();
    DKV.put(v0._key, v0);
    return v0;
  }

  private static byte[] chunkBytes(Vec v) {
    byte[] local = new byte[(int) v.length()];
    int len = 0;
    for (int i = 0; i < v.nChunks(); i++) {
      byte src[] = v.chunkForChunkIdx(i).asBytes();
      System.arraycopy(src, 0, local, len, src.length);
      len += src.length;
    }
    return local;
  }

}