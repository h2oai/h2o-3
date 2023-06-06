package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

import static org.junit.Assert.*;

public class ChunkTest extends TestUtil {

    @BeforeClass
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testSetVolatileFloats() {
        final int N = 10_000;
        final float[] data = new float[N];
        for (int i = 0; i < N; i++) {
            data[i] = i;
        }
        Vec v = Vec.makeConN(N, 1);
        try {
            Chunk c = v.chunkForChunkIdx(0).setVolatile(data);
            assertTrue(c instanceof C4FVolatileChunk);
            assertArrayEquals(data, ((C4FVolatileChunk) c).getValues(), 0);   // chunk created?
            assertArrayEquals(c.getBytes(), v.chunkForChunkIdx(0).getBytes()); // and is installed in DKV (correctly)?
        } finally {
            v.remove();
        }
    }

}
