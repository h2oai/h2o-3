package water.fvec;

import water.MemoryManager;
import water.util.StringUtils;
import water.util.UnsafeUtils;
import water.parser.BufferedString;

public class C0StrChunk extends Chunk {
    private static final int _OFF=1;
    static final int NA = -1;
    public boolean _isAllASCII = false;

    public C0StrChunk(String con, int len){
        this(StringUtils.bytesOf(con), len);
    }

    public C0StrChunk(byte[] con, int len) {
        _start = -1;
        set_len(len);
        _mem = MemoryManager.malloc1(_OFF + con.length, false);
        UnsafeUtils.copyMemory(con,0,_mem,_OFF,con.length);
        _isAllASCII = true;
        for(int i = _OFF; i < _mem.length; i++) {
            byte c = _mem[i];
            if ((c & 0x80) == 128) { //value beyond std ASCII
                _isAllASCII = false;
                break;
            }
        }
        UnsafeUtils.set1(_mem, 0, (byte) (_isAllASCII ? 1 : 0)); // isAllASCII flag
    }

    @Override public boolean setNA_impl(int idx) { return false; }
    @Override public boolean set_impl(int idx, float f) { if (Float.isNaN(f)) return false; else throw new IllegalArgumentException("Operation not allowed on string vector.");}
    @Override public boolean set_impl(int idx, double d) { if (Double.isNaN(d)) return false; else throw new IllegalArgumentException("Operation not allowed on string vector.");}
    @Override public boolean set_impl(int idx, long l) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
    @Override public boolean set_impl(int idx, String str) { return false; }

    @Override public boolean isNA_impl(int idx) {
        return false;
    }

    @Override public long at8_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
    @Override public double atd_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
    @Override public BufferedString atStr_impl(BufferedString bStr, int idx) {
        return bStr.set(_mem, _OFF, _mem.length);
    }

    @Override protected final void initFromBytes () {
        _start = -1;  _cidx = -1;
        byte b = UnsafeUtils.get1(_mem,0);
        _isAllASCII = b != 0;
        set_len(_mem.length - _OFF);
    }

    @Override public ChunkVisitor processRows(ChunkVisitor nc, int from, int to){
        BufferedString bs = new BufferedString();
        for(int i = from; i < to; i++)
            nc.addValue(atStr(bs,i));
        return nc;
    }
    @Override public ChunkVisitor processRows(ChunkVisitor nc, int... rows){
        BufferedString bs = new BufferedString();
        for(int i:rows)
            nc.addValue(atStr(bs,i));
        return nc;
    }
}

