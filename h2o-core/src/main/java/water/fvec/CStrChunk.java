package water.fvec;

import water.*;
import water.util.SetOfBytes;
import water.util.UnsafeUtils;
import water.parser.BufferedString;

import java.util.Arrays;

public class CStrChunk extends Chunk {
  static final int NA = -1;
  static protected final int _OFF=4+1;
  private int _valstart;
  public boolean _isAllASCII = false;

  public CStrChunk() {}
  public CStrChunk(int sslen, byte[] ss, int sparseLen, int idxLen, int[] id, int[] is) {
    _start = -1;
    _valstart = idx(idxLen);
    _len = idxLen;
    _mem = MemoryManager.malloc1(_valstart + sslen, false);
    UnsafeUtils.set4(_mem, 0, _valstart); // location of start of strings

    Arrays.fill(_mem,_OFF,_valstart,(byte)-1); // Indicate All Is NA's
    for( int i = 0; i < sparseLen; ++i ) // Copy the sparse indices
      UnsafeUtils.set4(_mem, idx(id==null ? i : id[i]), is==null ? 0 : is[i]);
    UnsafeUtils.copyMemory(ss,0,_mem,_valstart,sslen);
    _isAllASCII = true;
    for(int i = _valstart; i < _mem.length; ++i) {
      byte c = _mem[i];
      if ((c & 0x80) == 128) { //value beyond std ASCII
        _isAllASCII = false;
        break;
      }
    }
    UnsafeUtils.set1(_mem, 4, (byte) (_isAllASCII ? 1 : 0)); // isAllASCII flag
  }

  private int idx(int i) { return _OFF+(i<<2); }
  @Override public boolean setNA_impl(int idx) { return false; }
  @Override public boolean set_impl(int idx, float f) { if (Float.isNaN(f)) return false; else throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, double d) { if (Double.isNaN(d)) return false; else throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, long l) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, String str) { return false; }

  @Override public boolean isNA_impl(int idx) {
    int off = intAt(idx);
    return off == NA;
  }

  public int intAt(int i) { return UnsafeUtils.get4(_mem, idx(i)); }
  public byte byteAt(int i) { return _mem[_valstart+i]; }
  public int lengthAtOffset(int off) {
    int len = 0;
    while (byteAt(off + len) != 0) len++;
    return len;
  }
  
  @Override public long at8_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public double atd_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public BufferedString atStr_impl(BufferedString bStr, int idx) {
    int off = intAt(idx);
    if( off == NA ) return null;
    int len = lengthAtOffset(off);
    assert len >= 0 : getClass().getSimpleName() + ".atStr_impl: len=" + len + ", idx=" + idx + ", off=" + off;
    return bStr.set(_mem,_valstart+off,len);
  }

  @Override protected final void initFromBytes () {
    _start = -1;  _cidx = -1;
    _valstart = UnsafeUtils.get4(_mem, 0);
    byte b = UnsafeUtils.get1(_mem,4);
    _isAllASCII = b != 0;
    set_len((_valstart-_OFF)>>2);
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



  /**
   * Optimized toLower() method to operate across the entire CStrChunk buffer in one pass.
   * This method only changes the values of ASCII uppercase letters in the text.
   *
   * NewChunk is the same size as the original.
   *
   * @param nc NewChunk to be filled with the toLower version of ASCII strings in this chunk
   * @return Filled NewChunk
   */
  public NewChunk asciiToLower(NewChunk nc) {
    // copy existing data
    nc = this.extractRows(nc, 0,_len);
    //update offsets and byte array
    for(int i= 0; i < nc._sslen; i++) {
      if (nc._ss[i] > 0x40 && nc._ss[i] < 0x5B) // check for capital letter
        nc._ss[i] += 0x20; // lower it
    }

    return nc;
  }

  /**
   * Optimized toUpper() method to operate across the entire CStrChunk buffer in one pass.
   * This method only changes the values of ASCII lowercase letters in the text.
   *
   * NewChunk is the same size as the original.
   *
   * @param nc NewChunk to be filled with the toUpper version of ASCII strings in this chunk
   * @return Filled NewChunk
   */
  public NewChunk asciiToUpper(NewChunk nc) {
    // copy existing data
    nc = this.extractRows(nc, 0,_len);
    //update offsets and byte array
    for(int i= 0; i < nc._sslen; i++) {
      if (nc._ss[i] > 0x60 && nc._ss[i] < 0x7B) // check for capital letter
        nc._ss[i] -= 0x20; // upper it
    }

    return nc;
  }

  /**
   * Optimized trim() method to operate across the entire CStrChunk buffer in one pass.
   * This mimics Java String.trim() by only considering characters of value
   * <code>'&#92;u0020'</code> or less as whitespace to be trimmed. This means that like
   * Java's String.trim() it ignores 16 of the 17 characters regarded as a space in UTF.
   *
   * NewChunk is the same size as the original, despite trimming.
   *
   * @param nc NewChunk to be filled with trimmed version of strings in this chunk
   * @return Filled NewChunk
   */
  public NewChunk asciiTrim(NewChunk nc) {
    // copy existing data
    nc = this.extractRows(nc, 0,_len);
    //update offsets and byte array
    for(int i=0; i < _len; i++) {
      int j = 0;
      int off = UnsafeUtils.get4(_mem,idx(i));
      if (off != NA) {
        //UTF chars will appear as negative values. In Java spec, space is any char 0x20 and lower
        while( _mem[_valstart+off+j] > 0 && _mem[_valstart+off+j] < 0x21) j++;
        if (j > 0) nc.set_is(i,off + j);
        while( _mem[_valstart+off+j] != 0 ) j++; //Find end
        j--;
        while( _mem[_valstart+off+j] > 0 && _mem[_valstart+off+j] < 0x21) { //March back to find first non-space
          nc._ss[off+j] = 0; //Set new end
          j--;
        }
      }
    }
    return nc;
  }
  
  /**
   * Optimized substring() method for a buffer of only ASCII characters.
   * The presence of UTF-8 multi-byte characters would give incorrect results
   * for the string length, which is required here.
   *
   * @param nc NewChunk to be filled with substrings in this chunk
   * @param startIndex The beginning index of the substring, inclusive
   * @param endIndex The ending index of the substring, exclusive
   * @return Filled NewChunk
   */
  public NewChunk asciiSubstring(NewChunk nc, int startIndex, int endIndex) {
    // copy existing data
    nc = this.extractRows(nc, 0,_len);
    //update offsets and byte array
    for (int i = 0; i < _len; i++) {
      int off = UnsafeUtils.get4(_mem, idx(i));
      if (off != NA) {
        int len = 0;
        while (_mem[_valstart + off + len] != 0) len++; //Find length
        nc.set_is(i,startIndex < len ? off + startIndex : off + len);
        for (; len > endIndex - 1; len--) {
          nc._ss[off + len] = 0; //Set new end
        }
      }
    }
    return nc;
  }

  /**
   * Optimized length() method for a buffer of only ASCII characters.
   * This is a straight byte count for each word in the chunk. The presence
   * of UTF-8 multi-byte characters would give incorrect results.
   *
   * @param nc NewChunk to be filled with lengths of strings in this chunk
   * @return Filled NewChunk
   */
  public NewChunk asciiLength(NewChunk nc) {
    //pre-allocate since size is known
    nc.alloc_mantissa(_len);
    nc.alloc_exponent(_len); // sadly, a waste
    // fill in lengths
    for(int i=0; i < _len; i++) {
      int off = UnsafeUtils.get4(_mem,idx(i));
      int len = 0;
      if (off != NA) {
        while (_mem[_valstart + off + len] != 0) len++;
        nc.addNum(len, 0);
      } else nc.addNA();
    }
    return nc;
  }
  
  public NewChunk asciiEntropy(NewChunk nc) {
    nc.alloc_doubles(_len);
    for (int i = 0; i < _len; i++) {
      double entropy = entropyAt(i);
      if (Double.isNaN(entropy)) nc.addNA();
      else                       nc.addNum(entropy);
    }
    return nc;
  }

  double entropyAt(int i) {
    int off = intAt(i);
    if (off == NA) return Double.NaN;
    int[] frq = new int[256];
    int len = lengthAtOffset(off);
    for (int j = 0; j < len; j++) {
      frq[0xff & byteAt(off + j)]++;
    }
    double sum = 0;
    for (int b = 0; b < 256; b++) {
      int f = frq[b];
      if (f > 0) {
        double x = (double)f / len;
        sum += x * Math.log(x);
      }
    }
    return - sum / Math.log(2);
  }

  /**
   * Optimized lstrip() & rstrip() methods to operate across the entire CStrChunk buffer in one pass.
   *
   * NewChunk is the same size as the original, despite trimming.
   *
   * @param nc NewChunk to be filled with strip version of strings in this chunk
   * @param chars chars to strip, treated as ASCII
   * @return Filled NewChunk
   */
  public NewChunk asciiLStrip(NewChunk nc, String chars) {
    SetOfBytes set = new SetOfBytes(chars);
    //update offsets and byte array
    for(int i=0; i < _len; i++) {
      int off = intAt(i);
      if (off != NA) {
        while (set.contains(byteAt(off))) off++;
        int len = lengthAtOffset(off);
        nc.addStr(new BufferedString(_mem, _valstart+off, len));
      } else nc.addNA();
    }
    return nc;
  }

  public NewChunk asciiRStrip(NewChunk nc, String chars) {
    SetOfBytes set = new SetOfBytes(chars);
    //update offsets and byte array
    for(int i=0; i < _len; i++) {
      int off = intAt(i);
      if (off != NA) {
        int pos = off + lengthAtOffset(off);
        while (pos --> off && set.contains(byteAt(pos)));
        nc.addStr(new BufferedString(_mem, _valstart+off, pos - off + 1));
      } else nc.addNA();
    }
    return nc;
  }
}

