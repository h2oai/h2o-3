package hex.genmodel.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * Simplified version and drop-in replacement of water.util.AutoBuffer
 */
public final class ByteBufferWrapper {
    // The direct ByteBuffer for schlorping data about.
    // Set to null to indicate the ByteBufferWrapper is closed.
    ByteBuffer _bb;

    /** Read from a fixed byte[]; should not be closed. */
    public ByteBufferWrapper(byte[] buf) {
        assert buf != null : "null fed to ByteBuffer.wrap";
        _bb = ByteBuffer.wrap(buf, 0, buf.length).order(ByteOrder.nativeOrder());
    }

    public int position() {
        return _bb.position();
    }

    public boolean hasRemaining() {
      return _bb.hasRemaining();
    }

    /** Skip over some bytes in the byte buffer.  Caller is responsible for not
     *  reading off end of the bytebuffer; generally this is easy for
     *  array-backed autobuffers and difficult for i/o-backed bytebuffers. */
    public void skip(int skip) {
        _bb.position(_bb.position() + skip);
    }

    // -----------------------------------------------
    // Unlike original getX() methods, these will not attempt to auto-widen the buffer.

    public int get1U() {
        return _bb.get() & 0xFF;
    }
    public char get2() {
        return _bb.getChar();
    }
    public int get3() {
        return get1U() | (get1U() << 8) | (get1U() << 16);
    }
    public int get4() {
        return _bb.getInt();
    }
    public float get4f() {
      return _bb.getFloat();
    }
    public double get8d() {
      return _bb.getDouble();
    }
}
