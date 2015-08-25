package water.parser;

import com.google.common.base.Charsets;
import java.util.Arrays;
import water.Iced;

public class ValueString extends Iced implements Comparable<ValueString> {
   private byte [] _buf;
   private int _off;
   private int _len;

   ValueString( byte [] buf, int off, int len) { _buf = buf;  _off = off;  _len = len; }
   ValueString( byte [] buf ) { this(buf,0,buf.length); }
   public ValueString( String from ) { this(from.getBytes(Charsets.UTF_8)); }
   // Cloning constructing used during collecting unique enums
   ValueString( ValueString from ) { this(Arrays.copyOfRange(from._buf,from._off,from._off+from._len)); }
   // Used to make a temp recycling ValueString in hot loops
   public ValueString() { }

   @Override public int compareTo( ValueString o ) {
     int len = Math.min(_len,o._len);
     for( int i=0; i<len; i++ ) {
       int x = (0xFF&_buf[_off+i]) - (0xFF&o._buf[o._off+i]);
       if( x != 0 ) return x;
     }
     return _len - o._len;
   }

   @Override public int hashCode(){
     int hash = 0;
     int n = getOffset() + length();
     for (int i = getOffset(); i < n; ++i)
       hash = 31 * hash + getBuffer()[i];
     return hash;
   }

   void addChar(){_len++;}

   void addBuff(byte [] bits){
     byte [] buf = new byte[length()];
     int l1 = getBuffer().length- getOffset();
     System.arraycopy(getBuffer(), getOffset(), buf, 0, l1);
     System.arraycopy(bits, 0, buf, l1, length()-l1);
     _off = 0;
     _buf = buf;
   }


  // WARNING: LOSSY CONVERSION!!!
  // Converting to a String will truncate all bytes with high-order bits set,
  // even if they are otherwise a valid member of the field/ValueString.
  // Converting back to a ValueString will then make something with fewer
  // characters than what you started with, and will fail all equals() tests.
  @Override
  public String toString() {
    return new String(_buf, _off, _len, Charsets.UTF_8);
  }

  public static String[] toString( ValueString vs[] ) {
    if( vs==null ) return null;
    String[] ss = new String[vs.length];
    for( int i=0; i<vs.length; i++ )
      ss[i] = vs[i].toString();
    return ss;
  }

  public static ValueString[] toValueString(String[] strings) {
    if (strings == null) return null;
    ValueString[] res = new ValueString[strings.length];
    for (int i = 0; i < strings.length; i++) {
      res[i] = new ValueString();
      res[i].setTo(strings[i]);
    }
    return res;
  }

  public ValueString set(byte[] buf, int off, int len) {
    _buf = buf;
    _off = off;
    _len = len;
    return this;                // Flow coding
  }

  public ValueString setTo(String what) {
    _buf = what.getBytes(Charsets.UTF_8);
    _off = 0;
    _len = _buf.length;
    return this;
  }
  public void setOff(int off) { _off=off; }

  @Override public boolean equals(Object o){
    if(o instanceof ValueString) {
      ValueString str = (ValueString) o;
      if (str.length() != _len) return false;
      for (int i = 0; i < _len; ++i)
        if (getBuffer()[getOffset() + i] != str.getBuffer()[str.getOffset() + i]) return false;
      return true;
    } else if (o instanceof String) {
      String str = (String) o;
      if (str.length() != length()) return false;
      for (int i = 0; i < length(); ++i)
        if (getBuffer()[getOffset() + i] != str.charAt(i)) return false;
      return true;
    }
    return false; //FIXME find out if this is required for some case or if an exception can be thrown
  }
  public final byte [] getBuffer() {return _buf;}
  public final int getOffset() {return _off;}
  public final int length() {return _len;}

  /**
   * This detect whether the character set is valid UTF-8.
   * @return true/false
   */
  public boolean isUTF8() {
    boolean isUTF8 = true;
    for (int i=0; i < _len; i++) {
      byte c = _buf[_off+i];
      if ((c & 0x80) == 128) { //possible start of UTF-8 bytes or extended ASCII
        if ((c & 0xC0) != 192) { // not valid UTF-8
          isUTF8 = false;
          break;
        } else if ((c & 0xE0) == 192) { //possibly byte-1 of 2-byte UTF-8 char
          if ((i+1 < _len)
              && (_buf[_off + ++i] & 0xC0) == 128) break; // UTF8
          else isUTF8 = false;
        } else if ((c & 0xF0) == 224) { //possibly byte-1 of 3-byte UTF-8 char
          if ((i+2 < _len)
              && ((_buf[_off + ++i] & 0xC0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)) break; // UTF-8
          else isUTF8 = false;
        } else if ((c & 0xF8) == 240) { //possibly byte-1 of 4-byte UTF-8 char
          if ((i+3 < _len)
              && ((_buf[_off + ++i] & 0xC0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)) break; // UTF-8
          else isUTF8 = false;
        } else if ((c & 0xFC) == 248) { //possibly byte-1 of 5-byte UTF-8 char
          if ((i+4 < _len)
              && ((_buf[_off + ++i] & 0xC0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)) break; // UTF-8
          else isUTF8 = false;
        } else if ((c & 0xFE) == 252) { //possible byte-1 of 6-byte UTF-8 char
          if ((i+5 < _len)
              && ((_buf[_off + ++i] & 0xc0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)
              && ((_buf[_off + ++i] & 0xC0) == 128)) break; // UTF-8
          else isUTF8 = false;
        } else // not UTF-8
          isUTF8 = false;
      }
      if (!isUTF8) break;
    }
    return isUTF8;
  }

  public ValueString convertISO_8859_1() {
    return new ValueString(new String(_buf, _off, _len, Charsets.ISO_8859_1));
  }
}

