package water.parser;

import java.util.Arrays;
import water.Iced;

public final class ValueString extends Iced implements Comparable<ValueString> {
   private byte [] _buf;
   private int _off;
   private int _len;

   ValueString( byte [] buf, int off, int len) { _buf = buf;  _off = off;  _len = len; }
   ValueString( byte [] buf ) { this(buf,0,buf.length); }
   ValueString( String from ) { this(from.getBytes()); }
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
     int n = get_off() + get_length();
     for (int i = get_off(); i < n; ++i)
       hash = 31 * hash + get_buf()[i];
     return hash;
   }

   void addChar(){_len++;}

   void addBuff(byte [] bits){
     byte [] buf = new byte[get_length()];
     int l1 = get_buf().length-get_off();
     System.arraycopy(get_buf(), get_off(), buf, 0, l1);
     System.arraycopy(bits, 0, buf, l1, get_length()-l1);
     _off = 0;
     _buf = buf;
   }


// WARNING: LOSSY CONVERSION!!!
  // Converting to a String will truncate all bytes with high-order bits set,
  // even if they are otherwise a valid member of the field/ValueString.
  // Converting back to a ValueString will then make something with fewer
  // characters than what you started with, and will fail all equals() tests.
  @Override public String toString(){
    return new String(_buf,_off,_len);
  }

  public static String[] toString( ValueString vs[] ) {
    if( vs==null ) return null;
    String[] ss = new String[vs.length];
    for( int i=0; i<vs.length; i++ )
      ss[i] = vs[i].toString();
    return ss;
  }

  public ValueString set(byte [] buf, int off, int len) {
    _buf = buf;
    _off = off;
    _len = len;
    return this;                // Flow coding
  }

  public ValueString setTo(String what) {
    _buf = what.getBytes();
    _off = 0;
    _len = _buf.length;
    return this;
  }
  public void setOff(int off) { _off=off; }

  @Override public boolean equals(Object o){
    if(!(o instanceof ValueString)) return false;
    ValueString str = (ValueString)o;
    if(str.get_length() != get_length())return false;
    for(int i = 0; i < get_length(); ++i)
      if(get_buf()[get_off()+i] != str.get_buf()[str.get_off()+i]) return false;
    return true;
  }
  public final byte [] get_buf() {return _buf;}
  public final int get_off() {return _off;}
  public final int get_length() {return _len;}
}
