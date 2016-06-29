package water.util;
import java.io.IOException;
import java.io.InputStream;
import com.google.common.base.Throwables;

public abstract class RIStream extends InputStream {

  public interface ProgressMonitor {
    void update(long n);
  }

  InputStream _is;
  ProgressMonitor _pmon;
  public final int _retries = 5;
  String [] _bk;
  private long _off;
  boolean _knownSize;
  long _expectedSz;
  protected RIStream( long off, ProgressMonitor pmon){
    _off = off;
  }

  public final long off(){return _off;}
  public final long expectedSz(){
    return _knownSize?_expectedSz:-1;
  }
  public void setExpectedSz(long sz){
    _knownSize = true;
    _expectedSz = sz;
  }
  public final void open(){
    assert _is == null;
    try{
      _is = open(_off);
    } catch(IOException e){
      throw new RuntimeException(e);
    }
  }

  protected abstract InputStream open(long offset) throws IOException;

  public void closeQuietly(){
    try{close();} catch(Exception e){} // ignore any errors
  }
  private void try2Recover(int attempt, IOException e) {
    if(attempt == _retries) Throwables.propagate(e);
    Log.warn("[H2OS3InputStream] Attempt("+attempt + ") to recover from " + e.getMessage() + "), off = " + _off);
    try{_is.close();}catch(IOException ex){}
    _is = null;
    if(attempt > 0) try {Thread.sleep(256 << attempt);}catch(InterruptedException ex){}
    open();
    return;
  }

  private void updateOffset(int off) {
    if(_knownSize)assert (off + _off) <= _expectedSz;
    _off += off;
  }

  @Override
  public boolean markSupported(){
    return false;
  }
  @Override
  public void mark(int readLimit){throw new UnsupportedOperationException();}
  @Override
  public void reset(){throw new UnsupportedOperationException();}

  private void checkEof() throws IOException {
    if(_knownSize && _off < _expectedSz)
      throw new IOException("premature end of file reported, expected " + _expectedSz + " bytes, but got eof after " + _off + " bytes");
  }
  @Override
  public final int available() throws IOException {
    int attempts = 0;
    while(true){
      try {
        int res = _is.available();
        if(res == 0) checkEof();
        return _is.available();
      } catch (IOException e) {
        try2Recover(attempts++,e);
      }
    }
  }

  @Override
  public int read() throws IOException {
    int attempts = 0;
    while(true){
      try{
        int res = _is.read();
        if(res == -1) checkEof();
        if(res != -1){
          updateOffset(1);
          if(_pmon != null)_pmon.update(1);
        }
        return res;
      }catch (IOException e){
        try2Recover(attempts++,e);
      }
    }
  }

  @Override
  public int read(byte [] b) throws IOException {
    int attempts = 0;
    while(true){
      try {
        int res =  _is.read(b);
        if(res == -1) checkEof();
        if(res > 0){
          updateOffset(res);
          if(_pmon != null)_pmon.update(res);
        }
        return res;
      } catch(IOException e) {
        try2Recover(attempts++,e);
      }
    }
  }

  @Override
  public int read(byte [] b, int off, int len) throws IOException {
    int attempts = 0;
    while(true){
      try {
        int res = _is.read(b,off,len);
        if(res == -1) checkEof();
        if(res > 0){
          updateOffset(res);
          if(_pmon != null)_pmon.update(res);
        }
        return res;
      } catch(IOException e) {
        try2Recover(attempts++,e);
      }
    }
  }

  @Override
  public void close() throws IOException {
    if(_is != null){
      _is.close();
      _is = null;
    }
  }

  @Override
  public long skip(long n) throws IOException {
    int attempts = 0;
    while(true){
      try{
        long res = _is.skip(n);
        if(res > 0){
          updateOffset((int)res);
          if(_pmon != null)_pmon.update(res);
        }
        return res;
      } catch (IOException e) {
        try2Recover(attempts++,e);
      }
    }
  }
}