package water.fvec;

import water.*;
import water.util.UnsafeUtils;
import water.parser.BufferedString;

import java.util.HashMap;

public class CStrChunk extends Chunk {
  static final int NA = -1;
  static protected final int _OFF=4+1;
  private int _valstart;
  public boolean _isAllASCII = false;

  public CStrChunk() {}
  public CStrChunk(int sslen, byte[] ss, int sparseLen, int idxLen, int[] strIdx, boolean isAllASCII) {
    _start = -1;
    _valstart = _OFF + (idxLen<<2);
    _isAllASCII = isAllASCII;
    set_len(idxLen);

    _mem = MemoryManager.malloc1(CStrChunk._OFF + idxLen*4 + sslen, false);
    UnsafeUtils.set4(_mem, 0, CStrChunk._OFF + idxLen * 4); // location of start of strings
    if (_isAllASCII) UnsafeUtils.set1(_mem, 4, (byte)1); // use byte to store _isAllASCII
    else UnsafeUtils.set1(_mem, 4, (byte)0);

    for( int i = 0; i < sparseLen; ++i )
      UnsafeUtils.set4(_mem, CStrChunk._OFF + 4*i, strIdx[i]);
    for( int i = sparseLen; i < idxLen; ++i )  // set NAs
      UnsafeUtils.set4(_mem, CStrChunk._OFF + 4*i, -1);
    for( int i = 0; i < sslen; ++i )
      _mem[CStrChunk._OFF + idxLen*4 + i] = ss[i];
  }

  @Override public boolean setNA_impl(int idx) { return false; }
  @Override public boolean set_impl(int idx, float f) { if (Float.isNaN(f)) return false; else throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, double d) { if (Double.isNaN(d)) return false; else throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, long l) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, String str) { return false; }

  @Override public boolean isNA_impl(int idx) {
    int off = UnsafeUtils.get4(_mem,(idx<<2)+_OFF);
    return off == NA;
  }

  @Override public long at8_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public double atd_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public BufferedString atStr_impl(BufferedString bStr, int idx) {
    int off = UnsafeUtils.get4(_mem,(idx<<2)+_OFF);
    if( off == NA ) return null;
    int len = 0;
    while( _mem[_valstart+off+len] != 0 ) len++;
    return bStr.set(_mem,_valstart+off,len);
  }

  @Override protected final void initFromBytes () {
    _start = -1;  _cidx = -1;
    _valstart = UnsafeUtils.get4(_mem,0);
    byte b = UnsafeUtils.get1(_mem,4);
    _isAllASCII = b != 0;
    set_len((_valstart-_OFF)>>2);
  }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_sparseLen(nc.set_len(_len));
    nc._isAllASCII = _isAllASCII;
    nc._is = MemoryManager.malloc4(_len);
    for( int i = 0; i < _len; i++ )
      nc._is[i] = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
    nc._sslen = _mem.length - _valstart;
    nc._ss = MemoryManager.malloc1(nc._sslen);
    System.arraycopy(_mem,_valstart,nc._ss,0,nc._sslen);
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
    nc = this.inflate_impl(nc);
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
    nc = this.inflate_impl(nc);
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
    nc = this.inflate_impl(nc);
    //update offsets and byte array
    for(int i=0; i < _len; i++) {
      int j = 0;
      int off = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
      if (off != NA) {
        //UTF chars will appear as negative values. In Java spec, space is any char 0x20 and lower
        while( _mem[_valstart+off+j] > 0 && _mem[_valstart+off+j] < 0x21) j++;
        if (j > 0) nc._is[i] = off + j;
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
    nc = this.inflate_impl(nc);
    
    //update offsets and byte array
    for (int i = 0; i < _len; i++) {
      int off = UnsafeUtils.get4(_mem, (i << 2) + _OFF);
      if (off != NA) {
        int len = 0;
        while (_mem[_valstart + off + len] != 0) len++; //Find length
        nc._is[i] = startIndex < len ? off + startIndex : off + len;
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
      int off = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
      int len = 0;
      if (off != NA) {
        while (_mem[_valstart + off + len] != 0) len++;
        nc.addNum(len, 0);
      } else nc.addNA();
    }
    return nc;
  }
  
  public NewChunk asciiEntropy(NewChunk nc) {
    nc._ds = MemoryManager.malloc8d(_len);
    
    for (int i = 0; i < _len; i++) {
      int off = UnsafeUtils.get4(_mem, (i << 2) + _OFF);
      if (off != NA) {
        HashMap<Byte, Integer> freq = new HashMap<>();
        int j = 0;
        while (_mem[_valstart + off + j] != 0)  {
          Integer count = freq.get(_mem[_valstart + off + j]);
          if (count == null) freq.put(_mem[_valstart + off + j], 1);
          else freq.put(_mem[_valstart + off + j], count+1);
          j++;
        }
        double sume = 0;
        int N = j;
        double n;
        for (Byte b : freq.keySet()) {
          n = freq.get(b);
          sume += -n/N * Math.log(n/N) / Math.log(2);
        }
        nc.addNum(sume);
      } else nc.addNA();
    }
    return nc;
  }

  /**
   * Optimized lstrip() & rstrip() methods to operate across the entire CStrChunk buffer in one pass.
   *
   * NewChunk is the same size as the original, despite trimming.
   *
   * @param nc NewChunk to be filled with strip version of strings in this chunk
   * @param set chars to strip, treated as ASCII
   * @return Filled NewChunk
   */
  public NewChunk asciiLStrip(NewChunk nc, String set) {
    // copy existing data
    nc = this.inflate_impl(nc);
    //update offsets and byte array
    for(int i=0; i < _len; i++) {
      int j = 0;
      int off = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
      if (off != NA) {
        while( intersects(_mem[_valstart + off + j], set) ) j++;
        if (j > 0) nc._is[i] = off + j;
      }
    }
    return nc;
  }

  public NewChunk asciiRStrip(NewChunk nc, String set) {
    // copy existing data
    nc = this.inflate_impl(nc);
    //update offsets and byte array
    for(int i=0; i < _len; i++) {
      int j = 0;
      int off = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
      if (off != NA) {
        while( _mem[_valstart+off+j] != 0 ) j++; //Find end
        j--;
        while( intersects(_mem[_valstart + off + j], set) ) { // March back while char in set
          nc._ss[off+j] = 0; //Set new end
          j--;
        }
      }
    }
    return nc;
  }

  /**
   * Does c intersect w/ set?
   * @param c char to look for
   * @param set set to look in
   * @return true if c is in set
   */
  private boolean intersects(byte c, String set) {
    for (int i=0; i < set.length(); i++)
      if (c == set.charAt(i))
        return true;
    return false;
  }
}

