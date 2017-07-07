package water.persist;

import org.apache.hadoop.fs.PositionedReadable;
import org.apache.hadoop.fs.Seekable;
import water.fvec.Chunk;
import water.fvec.Vec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Seekable and PositionedReadable implementation of InputStream backed by a Vec data source.
 */
public class VecDataInputStream extends InputStream implements Seekable, PositionedReadable {

  private static final byte[] EMPTY_BUFFER = new byte[0];

  private final Vec _v;

  private byte[] _buffer;
  private long _offset;
  private int _pos;

  public VecDataInputStream(Vec v) {
    this._v = v;
    flushBuffer(0L);
  }

  private int buffAvailable() {
    return _buffer.length - _pos;
  }

  private long globAvailable() {
    return _v.length() - (_offset + _pos);
  }

  private void fetchData(long position) {
    Chunk chk = _v.chunkForRow(position);
    _buffer = chk.asBytes();
    _offset = chk.start();
    _pos = (int) (position - _offset);
    assert _buffer.length > 0;
  }

  private void flushBuffer(long position) {
    _buffer = EMPTY_BUFFER;
    _pos = 0;
    _offset = position;
  }

  @Override
  public int read() throws IOException {
    if (buffAvailable() <= 0) {
      if (globAvailable() <= 0L) {
        return -1;
      }
      fetchData(_offset + _pos);
    }
    return _buffer[_pos++] & 0xff;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    int read = read(_offset + _pos, buffer, offset, length);
    int skipped = (int) skip(read);
    assert skipped == read;
    return read;
  }

  @Override
  public long skip(long n) throws IOException {
    if (n == 0L) {
      return 0L;
    }
    long target = _offset + _pos + n;
    if (inBuffer(target)) {
      seekInBuffer(target);
    } else {
      if (target > _v.length()) {
        n -= target - _v.length();
        target = _v.length();
      }
      flushBuffer(target);
    }
    return n;
  }

  @Override
  public int read(final long position, byte[] buffer, int offset, int length) throws IOException {
    int loaded = 0;
    long currentPosition = position;
    while ((loaded < length) && (currentPosition < _v.length())) {
      byte[] buff;
      int pos;
      if (inBuffer(currentPosition)) {
        buff = _buffer;
        pos = (int) (currentPosition - _offset);
      } else {
        Chunk chunk = _v.chunkForRow(currentPosition);
        buff = chunk.asBytes();
        pos = (int) (currentPosition - chunk.start());
      }
      int avail = Math.min(buff.length - pos, length - loaded);
      System.arraycopy(buff, pos, buffer, offset + loaded, avail);
      loaded += avail;
      currentPosition += avail;
    }
    return loaded;
  }

  @Override
  public void readFully(long position, byte[] buffer, int offset, int length) throws IOException {
    int loaded = read(position, buffer, offset, length);
    if (loaded != length) {
      throw new EOFException("Reached the end of the Vec while reading into buffer.");
    }
  }

  @Override
  public void readFully(long position, byte[] buffer) throws IOException {
    readFully(position, buffer, 0, buffer.length);
  }

  @Override
  public void seek(long position) throws IOException {
    if (inBuffer(position)) {
      seekInBuffer(position);
    } else {
      flushBuffer(position);
    }
  }

  private void seekInBuffer(long position) {
    _pos = (int) (position - _offset);
  }

  private boolean inBuffer(long position) {
    return (position >= _offset) && (position < _offset + _buffer.length);
  }

  @Override
  public long getPos() throws IOException {
    return _offset + _pos;
  }

  @Override
  public boolean seekToNewSource(long targetPos) throws IOException {
    throw new UnsupportedOperationException("Intentionally not implemented");
  }

}
