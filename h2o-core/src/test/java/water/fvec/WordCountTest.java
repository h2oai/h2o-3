package water.fvec;

import org.junit.*;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import water.*;
import water.nbhm.NonBlockingHashMap;

public class WordCountTest extends TestUtil {
  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }

  // ==========================================================================
  @Test public void testWordCount() throws IOException {
    File file = find_test_file("./smalldata/junit/cars.csv");
    doWordCount(file);
  }

  protected void doWordCount(File file) throws IOException {
    NFSFileVec nfs=NFSFileVec.make(file);
  
    System.out.printf("\nProgress: 00%%");
    final long start = System.currentTimeMillis();
    NonBlockingHashMap<VStr,VStr> words = new WordCount().doAll(nfs)._words;
    final long time_wc = System.currentTimeMillis();
    VStr[] vss = new VStr[words.size()];
    System.out.println("\nWC takes "+(time_wc-start)+"msec for "+vss.length+" words");
  
    // Faster version of toArray - because calling toArray on a 16M entry array
    // is slow.
    // Start the walk at slot 2, because slots 0,1 hold meta-data
    // In the raw backing array, Keys and Values alternate in slots
    int cnt=0;
    Object[] kvs = WordCount.WORDS.raw_array();
    for( int i=2; i<kvs.length; i += 2 ) {
      Object ok = kvs[i], ov = kvs[i+1];
      if( ok != null && ok instanceof VStr && ok == ov )
        vss[cnt++] = (VStr)ov;
    }
    final long time_ary = System.currentTimeMillis();
    System.out.println("WC toArray "+(time_ary-time_wc)+"msec for "+cnt+" words");
  
    Arrays.sort(vss,0,cnt,null);
    final long time_sort = System.currentTimeMillis();
    System.out.println("WC sort "+(time_sort-time_ary)+"msec for "+cnt+" words");
  
    System.out.println("Found "+cnt+" unique words.");
    System.out.println(Arrays.toString(vss));
    nfs.remove(new Futures()).blockForPending();
  }
  
  private static class WordCount extends MRTask<WordCount> {
    static NonBlockingHashMap<VStr,VStr> WORDS;
    static AtomicLong PROGRESS;
    transient NonBlockingHashMap<VStr,VStr> _words;

    @Override public void setupLocal() { WORDS = new NonBlockingHashMap<>(); PROGRESS = new AtomicLong(0); }
    private static int isChar( int b ) {
      if( 'A'<=b && b<='Z' ) return b-'A'+'a';
      if( 'a'<=b && b<='z' ) return b;
      return -1;
    }
  
    @Override public void map( Chunk bv ) {
      _words = WORDS;
      final int len = bv._len;
      int i=0;                  // Parse point
      // Skip partial words at the start of chunks, assuming they belong to the
      // trailing end of the prior chunk.
      if( bv._start > 0 )       // Not on the 1st chunk...
        while( i < len && isChar((int)bv.atd(i)) >= 0 ) i++; // skip any partial word from prior
      VStr vs = new VStr(new byte[512],(short)0);
      // Loop over the chunk, picking out words
      while( i<len )                      // Till we run dry
        vs = doChar(vs,(int)bv.atd(i++)); // Load a char & make words
      // Finish up partial word at Chunk end by flowing into the next Chunk
      i = 0;
      Chunk nv = bv.nextChunk();
      if( nv == null ) vs = doChar(vs,' '); // No next Chunk, end partial word
      while( vs._len > 0 )                // Till word breaks
        vs = doChar(vs,(int)nv.atd(i++)); // Load a char & make words
      // Show some progress
      long progress = PROGRESS.addAndGet(len);
      long pre = progress - len;
      final long total = bv._vec.length();
      int perc0 = (int)(100*pre     /total);
      int perc1 = (int)(100*progress/total);
      if( perc0 != perc1 ) System.out.printf("\b\b\b%2d%%",perc1);
    }

    private VStr doChar( VStr vs, int raw ) {
      int c = isChar(raw);      // Check for letter & lowercase it
      if( c >= 0 && vs._len < 32700/*break silly long words*/ ) // In a word?
        return vs.append(c);     // Append char
      if( vs._len == 0 ) return vs; // Not a letter and not in a word?
      // None-letter ends word; count word
      VStr vs2 = WORDS.putIfAbsent(vs,vs);
      if( vs2 == null ) {     // If actually inserted, need new VStr
        //if( vs._len>256 ) System.out.println("Too long: "+vs);
        return new VStr(vs._cs,(short)(vs._off+vs._len)); // New VStr reuses extra space from old
      }
      vs2.inc(1);               // Inc count on added word, and
      vs._len = 0;              // re-use VStr (since not added to NBHM)
      return vs;
    }
  
    @Override public void reduce( WordCount wc ) {
      if( _words != wc._words )
        throw H2O.unimpl();
    }
  
    public final AutoBuffer write_impl(AutoBuffer ab) {
      if( _words != null ) 
        for( VStr key : WORDS.keySet() )
          ab.put2((char)key._len).putA1(key._cs,key._off,key._off+key._len).put4(key._cnt);
      return ab.put2((char)65535); // End of map marker
    }
    public final WordCount read_impl(AutoBuffer ab) {
      final long start = System.currentTimeMillis();
      int cnt=0;
      _words = WORDS;
      int len;
      while( (len = ab.get2()) != 65535 ) { // Read until end-of-map marker
        VStr vs = new VStr(ab.getA1(len),(short)0);
        vs._len = (short)len;
        vs._cnt = ab.get4();
        VStr vs2 = WORDS.putIfAbsent(vs,vs);
        if( vs2 != null ) vs2.inc(vs._cnt); // Inc count on added word
        cnt++;
      }
      final long t = System.currentTimeMillis() - start;
      System.out.println("WC Read takes "+t+"msec for "+cnt+" words");
      return this;
    }
    @Override protected void copyOver(WordCount wc) { _words = wc._words; }
  }
  
  
  // A word, and a count of occurences.  Typically the '_cs' buf is shared
  // amongst many VStr's, all using different off/len pairs.
  private static class VStr implements Comparable<VStr> {
    byte[] _cs;                 // shared array of chars holding words
    short _off,_len;            // offset & len of this word
    VStr(byte[]cs, short off) { assert off>=0:off; _cs=cs; _off=off; _len=0; _cnt=1; }
    // append a char; return wasted pad space
    public VStr append( int c ) {
      if( _off+_len >= _cs.length ) { // no room for word?
        int newlen = Math.min(32767,_cs.length<<1);
        if( _off > 0 && _len < 512 ) newlen = Math.max(1024,newlen);
        byte[] cs = new byte[newlen];
        System.arraycopy(_cs,_off,cs,0,_len);
        _off=0;
        _cs = cs;
      }
      _cs[_off+_len++] = (byte)c;
      return this;
    }
    volatile int _cnt;          // Atomically update
    private static final AtomicIntegerFieldUpdater<VStr> _cntUpdater =
      AtomicIntegerFieldUpdater.newUpdater(VStr.class, "_cnt");
    void inc(int d) {
      int r = _cnt;
      while( !_cntUpdater.compareAndSet(this,r,r+d) )
        r = _cnt;
    }
  
    public String toString() { return new String(_cs,_off,_len)+"="+_cnt; }
  
    @Override public int compareTo(VStr vs) {
      int f = vs._cnt - _cnt; // sort by freq
      if( f != 0 ) return f;
      // alpha-sort, after tied on freq
      int len = Math.min(_len,vs._len);
      for(int i = 0; i < len; ++i)
        if(_cs[_off+i] != vs._cs[vs._off+i])
          return _cs[_off+i]-vs._cs[vs._off+i];
      return _len - vs._len;
    }
    @Override public boolean equals(Object o){
      if(!(o instanceof VStr)) return false;
      VStr vs = (VStr)o;
      if( vs._len != _len)return false;
      for(int i = 0; i < _len; ++i)
        if(_cs[_off+i] != vs._cs[vs._off+i]) return false;
      return true;
    }
    @Override public int hashCode() {
     int hash = 0;
     for(int i = 0; i < _len; ++i)
       hash = 31 * hash + _cs[_off+i];
     return hash;
    }
  }

  @Test public void dummy_test() {
    /* this is just a dummy test to avoid JUnit complains about missing test */
  }
}
