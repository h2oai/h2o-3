package water.parser;

import water.AutoBuffer;
import water.Iced;
import water.util.StringUtils;

import java.util.Arrays;
import java.util.Formatter;

/**
 * A mutable wrapper to hold String as a byte array.
 *
 * It can be modified by set of methods, the hash code is computed
 * on the fly. There is no speed up benefit of cashing the hash in
 * a dedicated private field. See the speed test in {@code ParseTest2#testSpeedOfCategoricalUpdate}.
 *
 * Warning: This data structure is not designed for parallel access!
 */
public class BufferedString extends Iced implements Comparable<BufferedString> {
   protected byte [] _buf;
   protected int _off;
   protected int _len;

   public BufferedString(byte[] buf, int off, int len) { 
     _buf = buf;  
     _off = off;  
     _len = len; 
     assert len >= 0 :  "Bad length in constructor " + len;
   }

   private BufferedString(byte[] buf) { this(buf,0,buf.length); }
   // Cloning constructing used during collecting unique categoricals
   BufferedString(BufferedString from) {
     this(Arrays.copyOfRange(from._buf,from._off,from._off+from._len));
   }

   public BufferedString(String from) { this(StringUtils.bytesOf(from)); }
   // Used to make a temp recycling BufferedString in hot loops
   public BufferedString() { }

   public final AutoBuffer write_impl(AutoBuffer ab) {
     if( _buf == null ) return ab.putInt(-1);
     ab.putInt(_len);
     return ab.putA1(_buf,_off,_off+_len);
   }

  public final BufferedString read_impl(AutoBuffer ab){
    _buf = ab.getA1();
    if(_buf != null) _len = _buf.length;
    return this;
  }

  /**
   * Comparison, according to Comparable interface
   * @param o other string to compare
   * @return -1 or 0 or 1, as specified in Comparable
   */
   @Override public int compareTo( BufferedString o ) {
     int len = Math.min(_len,o._len);
     for( int i=0; i<len; i++ ) {
       int x = (0xFF&_buf[_off+i]) - (0xFF&o._buf[o._off+i]);
       if( x != 0 ) return x;
     }
     return _len - o._len;
   }

   @Override public int hashCode(){
     int hash = 0;
     int n = _off + _len;
     for (int i = _off; i < n; ++i) // equivalent to String.hashCode (not actually)
       hash = 31 * hash + (char)_buf[i];
     return hash;
   }

   // TODO(vlad): make sure that this method is not as destructive as it now is (see tests) 
   void addChar() {
     _len++;
   }

   void removeChar(){
     _len--;
   }

   void addBuff(byte [] bits){
     byte [] buf = new byte[_len];
     int l1 = _buf.length- _off;
     System.arraycopy(_buf, _off, buf, 0, l1);
     System.arraycopy(bits, 0, buf, l1, _len-l1);
     _off = 0;
     _buf = buf;
   }


  // WARNING: LOSSY CONVERSION!!!
  // Converting to a String will truncate all bytes with high-order bits set,
  // even if they are otherwise a valid member of the field/BufferedString.
  // Converting back to a BufferedString will then make something with fewer
  // characters than what you started with, and will fail all equals() tests.
  // TODO(Vlad): figure out what to do about the buffer being not UTF-8 (who guarantees?)
  @Override
  public String toString() {
    return _buf == null ? null : StringUtils.toString(_buf, Math.max(0, _off), Math.min(_buf.length, _len));
  }

  /**
   * Converts this BufferedString into an ASCII String where all original non-ASCII characters
   * are represented in a hexadecimal notation.
   * @return Sanitized String value (safe to use eg. in domains).
   */
  public String toSanitizedString() {
    StringBuilder sb = new StringBuilder(_len * 2);
    Formatter formatter = new Formatter(sb);
    boolean inHex = false;
    for (int i = 0; i < _len; i++) {
      if ((_buf[_off + i] & 0x80) == 128) {
        if (!inHex) sb.append("<0x");
        formatter.format("%02X", _buf[_off + i]);
        inHex = true;
      } else { // ASCII
        if (inHex) {
          sb.append(">");
          inHex = false;
        }
        formatter.format("%c", _buf[_off + i]);
      }
    }
    if (inHex) sb.append(">"); // close hex values as trailing char
    return sb.toString();
  }

  public static String[] toString(BufferedString bStr[]) {
    if( bStr==null ) return null;
    String[] ss = new String[bStr.length];
    for( int i=0; i<bStr.length; i++ )
      ss[i] = bStr[i].toString();
    return ss;
  }

  public static BufferedString[] toBufferedString(String[] strings) {
    if (strings == null) return null;
    BufferedString[] res = new BufferedString[strings.length];
    for (int i = 0; i < strings.length; i++) {
      res[i] = new BufferedString(strings[i]);
    }
    return res;
  }

  public final BufferedString set(byte[] buf) {
    return set(buf, 0, buf.length);
  }

  public BufferedString set(byte[] buf, int off, int len) {
    _buf = buf;
    _off = off;
    _len = len;
    assert len >= 0 : "Bad length in setter " + len;
    return this;
  }

  public final BufferedString set(String s) {
    return set(StringUtils.bytesOf(s));
  }

  public void setOff(int off) {
    _off=off;
  }

  public void setLen(int len) {
    _len=len;
  }

  @Override public boolean equals(Object o){
    if(o instanceof BufferedString) {
      BufferedString str = (BufferedString) o;
      if (str._len != _len) return false;
      for (int i = 0; i < _len; ++i)
        if (_buf[_off + i] != str._buf[str._off + i]) return false;
      return true;
    }
    return false;
  }

  /**
   * Tests whether this BufferedString is equal to a given ASCII string
   * @param str string sequence made of ASCII characters (0..127)
   * @return true if this BufferedString represents a given ASCII String, false if sequences differ or if the test
   * string is not actually made of just ASCII characters
   */
  public boolean equalsAsciiString(String str) {
    if (str == null || str.length() != _len) return false;
    for (int i = 0; i < _len; ++i)
      if (_buf[_off + i] != str.charAt(i)) return false;
    return true;
  }

  // Thou Shalt Not use accessors in performance critical code - because it
  // obfuscates the code's cost model.  All file-local uses of the accessors
  // has been stripped, please do not re-insert them.  In particular, the
  // hashcode and equals calls are made millions (billions?) of times a second
  // when parsing categoricals.
  public final byte [] getBuffer() {return _buf;} 
  public final int getOffset() {return _off;}
  public final int length() {return _len;}

  public static final byte NA  =  0;
  public static final byte INT =  1;
  public static final byte REAL=  2;

  public final byte getNumericType() {
    int i = 0;
    int decimalCnt = 0;
    if (_len == 0) return NA;
    if (_buf[_off] == '+' || _buf[_off] == '-') i++;
    while( i < _len) {
      if (_buf[_off+i] == '.') decimalCnt++;
      else if (_buf[_off+i] < '0' || _buf[_off+i] > '9') return NA;
      i++;
    }
    if (decimalCnt > 0)
      if (decimalCnt == 1) return REAL;
      else return NA; //more than one decimal, NaN
    else return INT;
  }
}

