package water.fvec;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Futures;
import water.MRTask;
import water.TestUtil;

import static org.junit.Assert.*;

public class C4FVolatileChunkTest extends TestUtil {

    @BeforeClass
    public static void setup() { stall_till_cloudsize(1); }
    
    private float[] _data;
    private double[] _dataDs;
    private C4FVolatileChunk _chunk;
    
    @Before
    public void initData() {
        final int N = 10_000;
        _data = new float[N];
        _dataDs = new double[N];
        _data[0] = Float.NaN;
        _dataDs[0] = Double.NaN;
        for (int i = 2; i < _data.length; i++) {
            _data[i] = _data[i - 1] + 0.01f;
            _dataDs[i] = _data[i];
        }
        _chunk = new C4FVolatileChunk(_data);
    }
    
    @Test
    public void testIsVolatile() {
        assertTrue(_chunk.isVolatile());
    }
    
    @Test
    public void testGetValues() {
        assertSame(_data, _chunk.getValues());
    }

    @Test
    public void testGetDoubles() {
        assertArrayEquals(_dataDs, _chunk.getDoubles(), 0);
    }

    @Test
    public void testReadValues() {
        assertTrue(_chunk.isNA(0));
        for (int i = 1; i < _data.length; i++) {
            assertEquals(_dataDs[i], _chunk.atd(i), 0);
            assertEquals((long) _dataDs[i], _chunk.at8(i));
        }
    }

    @Test
    public void testSerialization() {
        byte[] bytes = _chunk.asBytes();
        assertNotNull(bytes);
        assertEquals(_data.length * 4, bytes.length);
        C4FVolatileChunk clone = new C4FVolatileChunk(new float[0]);
        clone.setBytes(bytes);
        clone.initFromBytes();
        assertArrayEquals(_data, clone.getValues(), 0);
    }
    
    @Test
    public void testClose() {
        Vec v = Vec.makeConN(_data.length, 1);
        try {
            assertEquals(1, v.nChunks());
            assertEquals(_data.length, v.length());
            _chunk._vec = v;
            Futures fs = new Futures();
            _chunk.close(0, fs);
            fs.blockForPending();
            // the volatile chunk should now be in DKV instead of the old chunk
            Chunk c = v.chunkForChunkIdx(0);
            assertTrue(c.isVolatile());
            assertTrue(c instanceof C4FVolatileChunk);
            assertArrayEquals(_dataDs, c.getDoubles(), 0);
        } finally {
            v.remove();
        }
    }

    @Test
    public void testMRTaskMutate() {
        Vec v = Vec.makeConN(_data.length, 1);
        try {
            _chunk._vec = v;
            Futures fs = new Futures();
            _chunk.close(0, fs);
            fs.blockForPending();

            new MRTask() {
                @Override
                public void map(Chunk c) {
                    c.set(2, Double.NaN);
                    c.set(3, 42L);
                    c.set(5, Math.E);
                }
                @Override
                protected boolean modifiesVolatileVecs() {
                    return true;
                }
            }.doAll(v);

            assertTrue(v.isNA(2));
            assertEquals(42L, v.at8(3));
            assertEquals(Math.E, v.at(5), 1e-6); // remember, these are float chunks - precision is lost
        } finally {
            v.remove();
        }
    }

}
