package water.fvec;

import water.DKV;
import water.Futures;
import water.MemoryManager;
import water.Value;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'float's.
 * Can only be used locally (intentionally does not serialize).
 * Intended for temporary data which gets modified frequently.
 * Exposes data directly as float[]
 */
public class C4FVolatileChunk extends Chunk {
    private transient float[] _fs;

    C4FVolatileChunk(float[] fs) {
        _start = -1;
        _len = fs.length;
        _fs = fs;
    }

    public float[] getValues(){
        return _fs;
    }

    @Override
    protected final long at8_impl(int i) {
        float res = _fs[i];
        if (Float.isNaN(res))
            throw new IllegalArgumentException("at8_abs but value is missing");
        return (long)res;
    }

    @Override
    protected final double atd_impl(int i) {
        return _fs[i] ;
    }

    @Override
    protected final boolean isNA_impl(int i) {
        return Float.isNaN(_fs[i]);
    }

    @Override
    boolean set_impl(int idx, long l) {
        float f = (float) l;
        if (f != l)
            return false;
        _fs[idx] = f;
        return true;
    }

    @Override
    boolean set_impl(int i, double d) {
        _fs[i] = (float) d;
        return true;
    }
    @Override boolean set_impl(int i, float f) {
        _fs[i] = f;
        return true;
    }

    public boolean isVolatile() {
        return true;
    }

    @Override
    boolean setNA_impl(int idx) {
        UnsafeUtils.set4f(_mem, (idx<<2), Float.NaN);
        return true;
    }

    @Override public final void initFromBytes() {
        _len = _mem.length >> 2;
        _fs = MemoryManager.malloc4f(_len);
        for (int i = 0; i < _fs.length; ++i) {
            _fs[i] = UnsafeUtils.get4f(_mem, 4 * i);
        }
        _mem = null;
    }

    @Override public byte[] asBytes() {
        byte[] res = MemoryManager.malloc1(_len*4);
        for (int i = 0; i < _len; ++i) {
            UnsafeUtils.set4f(res, 4 * i, _fs[i]);
        }
        return res;
    }

    @Override
    public Futures close(int cidx, Futures fs) {
        if (chk2() != null)
            return chk2().close(cidx, fs);
        Value v = new Value(_vec.chunkKey(cidx), this, _len*4, Value.ICE);
        DKV.put(v._key, v, fs);
        return fs;
    }

    @Override
    public <T extends ChunkVisitor> T processRows(T v, int from, int to) {
        for (int i = from; i < to; i++) {
            v.addValue(_fs[i]);
        }
        return v;
    }

    @Override
    public <T extends ChunkVisitor> T processRows(T v, int[] ids) {
        for (int i : ids) {
            v.addValue(_fs[i]);
        }
        return v;
    }

}
