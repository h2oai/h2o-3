package water.fvec;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OIllegalArgumentException;
import water.parser.BufferedString;
import water.util.*;
import water.util.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;

/** A collection of named {@link Vec}s, essentially an R-like Distributed Data Frame.
 *
 *  <p>Frames represent a large distributed 2-D table with named columns
 *  ({@link Vec}s) and numbered rows.  A reasonable <em>column</em> limit is
 *  100K columns, but there's no hard-coded limit.  There's no real <em>row</em>
 *  limit except memory; Frames (and Vecs) with many billions of rows are used
 *  routinely.
 *
 *  <p>A Frame is a collection of named Vecs; a Vec is a collection of numbered
 *  {@link Chunk}s.  A Frame is small, cheaply and easily manipulated, it is
 *  commonly passed-by-Value.  It exists on one node, and <em>may</em> be
 *  stored in the {@link DKV}.  Vecs, on the other hand, <em>must</em> be stored in the
 *  {@link DKV}, as they represent the shared common management state for a collection
 *  of distributed Chunks.
 *
 *  <p>Multiple Frames can reference the same Vecs, although this sharing can
 *  make Vec lifetime management complex.  Commonly temporary Frames are used
 *  to work with a subset of some other Frame (often during algorithm
 *  execution, when some columns are dropped from the modeling process).  The
 *  temporary Frame can simply be ignored, allowing the normal GC process to
 *  reclaim it.  Such temp Frames usually have a {@code null} key.
 *
 *  <p>All the Vecs in a Frame belong to the same {@link Vec.VectorGroup} which
 *  then enforces {@link Chunk} row alignment across Vecs (or at least enforces
 *  a low-cost access model).  Parallel and distributed execution touching all
 *  the data in a Frame relies on this alignment to get good performance.
 *  
 *  <p>Example: Make a Frame from a CSV file:<pre>
 *  File file = ...
 *  NFSFileVec nfs = NFSFileVec.make(file); // NFS-backed Vec, lazily read on demand
 *  Frame fr = water.parser.ParseDataset.parse(Key.make("myKey"),nfs._key);
 *  </pre>
 * 
 *  <p>Example: Find and remove the Vec called "unique_id" from the Frame,
 *  since modeling with a unique_id can lead to overfitting:
 *  <pre>
 *  Vec uid = fr.remove("unique_id");
 *  </pre>
 *
 *  <p>Example: Move the response column to the last position:
 *  <pre>
 *  fr.add("response",fr.remove("response"));
 *  </pre>
 *
 */
public class Frame extends Lockable<Frame> {
  public void reloadVecs() {
    _vecs.reload();
  }


  public void restructure(Names names, VecAry vvecs) {
    _names = names;
    _vecs = vvecs;
  }

  public Frame deepCopy() {return new Frame(_key,new Names(_names),_vecs.deepCopy());}

  public boolean hasNAs() {
    for(int i = 0; i < _vecs.len(); ++i)
      if(_vecs.naCnt(i) > 0)
        return true;
    return false;
  }

  public void insertVec(int i, String name, VecAry vec) {
    throw H2O.unimpl();
  }

  public VecAry vecRange(int from, int ncolA) {
    throw H2O.unimpl();
  }

  public VecAry lastVec() {return _vecs.getVecs(_vecs.len()-1);}

  public String[][] domains() {return _vecs.domains();}

  public long byteSize() {return _vecs.byteSize();}

  public String lastVecName() {return _names.getName(numCols()-1);}

  public byte[] types() {return _vecs.types();}

  public VecAry replace(VecAry vecAry, int... ids) {
    if(vecAry.len() != ids.length) throw new IllegalArgumentException("");
    throw H2O.unimpl();
  }
  public VecAry replace(VecAry vecAry, String... nms) {
    if(vecAry.len() != nms.length) throw new IllegalArgumentException("");
    throw H2O.unimpl();
  }

  /** Pair of (column name, Frame key). */
  public static class VecSpecifier extends Iced {
    public Key<Frame> _frame;
    String _column_name;

    public VecAry vec() {
      Value v = DKV.get(_frame);
      if (null == v) return null;
      Frame f = v.get();
      if (null == f) return null;
      return f.vecs(_column_name);
    }
  }


  public static final class Names extends Iced {
    private String [] _names;
    private transient HashMap<String,Integer> _map;
    int _n;

    public Names(){this(0);}
    public Names(int n) {_n = n;}
    public Names(String... names) {
      _n = names.length;
      _names = names;
    }
    public Names(int n, String [] names) {
      _n = n;
      _names = names;
    }
    public Names(Names ns) {
      _names = ns._names;
      _n = ns._n;
    }

    public int maxNameLen() {
      if(_names == null) return getName(_n-1).length();
      int len = 0;
      for(String s:_names)
        len = Math.max(len,s.length());
      return len;
    }

    public String getName(int i) {return _names == null?defaultColName(i):_names[i];}
    public int getId(String name) {
      if(_names == null)
        return defaultColId(name);
      if(_map == null) {
        _map = new HashMap<>();
        for(int i = 0; i < _names.length; ++i)
          _map.put(_names[i],i);
      }
      Integer res = _map.get(name);
      return res == null?-1:res.intValue();
    }

    public int hashCode(){
      return _names == null?_n:Arrays.deepHashCode(_names);
    }

    public boolean defaultNames(){
      return _names == null && _n > 0;
    }

    public static String[] makeDefaultNames(int n){
      String [] res = new String[n];
      for(int i = 0; i < n; ++i)
        res[i] = defaultColName(i);
      return res;
    }
    public Names add(String[] names) {
      if(_names == null && _n > 0)_names = makeDefaultNames(_n);
      _names = _names == null?names.clone():Arrays.copyOf(_names,_names.length + names.length);
      if(_map == null)_map = new HashMap<>();
      for(int i = 0; i < names.length; ++i){
        String ns = names[i];
        int cnt = 0;
        while(_map.containsKey(ns))
          ns = names[i] + ++cnt;
        _map.put(_names[_n+i] = ns,i);
      }
      _n += names.length;
      return this;
    }
    public Names add(Names names) {
      return add(names.getNames());
    }

    public String [] getNames(){
      if(_n > 0 && _names == null)
        return makeDefaultNames(_n);
      return _names;
    }

    public void swap(int lo, int hi) {
      String n = _names[lo];
      _names[lo] = _names[hi];
      _names[hi] =  n;
      if(_map != null) {
        _map.put(n, hi);
        _map.put(_names[lo],lo);
      }
    }

    public int[] remove(String... names) {
      int [] res = new int[names.length];
      for(int i =0; i < names.length; ++i)
        res[i] = getId(names[i]);
      remove(res);
      return res;
    }

    public void remove(int... idxs) {
      _names = getNames();
      int k = 0;
      int l = 0;
      for(int i = 0; i < _names.length; ++i) {
        if (i == idxs[l]) {
          if(_map != null)
            _map.remove(_names[i]);
          l++;
        }
        else _names[k++] = _names[i];
      }
      _names = Arrays.copyOf(_names,k);
    }

    public void removeRange(int startIdx, int endIdx) {
      if(_map != null) {
        for(int i = startIdx; i < endIdx; ++i)
          _map.remove(endIdx);

        String [] names = new String[_n + startIdx - endIdx];
        if(_names != null) {
          System.arraycopy(_names,0,names,0,startIdx);
          System.arraycopy(_names,endIdx,names,startIdx,_names.length-endIdx);
        } else {
          int x = startIdx-endIdx;
          for(int i = 0; i < startIdx; ++i)
            names[i] = defaultColName(i);
          for(int i = endIdx; i < _n; ++i)
            names[i+x] = defaultColName(i);
        }
        _names = names;
      }
    }

    public String[] getNames(int[] c2) {return ArrayUtils.select(getNames(),c2);}

    public int len() {return _n;}

    public Names intersect(Names names) {
      throw H2O.unimpl();
    }

    public void setName(int i, String str) {
      throw H2O.unimpl();
    }
  }
  /** Vec names */
  public Names _names;
  public Names names(){return _names;}

  private boolean _lastNameBig; // Last name is "Cxxx" and has largest number
  private VecAry _vecs;

  public VecAry vecs(){return _vecs;}


  public VecAry vecs(int... ids) {return _vecs.getVecs(ids);}
  public VecAry vecs(String... names) {
    int [] ids = new int[names.length];
    for(int i = 0; i < names.length; ++i)
      ids[i] = _names.getId(names[i]);
    return _vecs.getVecs(ids);
  }

  public VecAry vecs(Names names) {
    throw H2O.unimpl();
  }
  /**
   * Constructor for data with unnamed (default names) columns.
   * @param key
   * @param vecs
   */
  public Frame(VecAry vecs) { this((Key)null,vecs);}
  public Frame( Key key, VecAry vecs) {
    super(key);
    _vecs = vecs;
    _names = new Names();
  }

  public Frame( Vec... vecs ){ this((Key)null, new VecAry(vecs));}
  public Frame( Key key, Names names, VecAry vecs) {
    super(key);
    _vecs = vecs;
    _names = new Names(names);
  }
  public Frame( String names[], VecAry vecs) {this(null,names,vecs);}
  /** Creates a frame with given key, names and vectors. */
  public Frame( Key key, String names[], VecAry vecs) {
    super(key);
    assert names == null || names.length == vecs.len();
    _vecs  = vecs;
    // Require all Vecs already be installed in the K/V store
    // Always require names
    if( names==null ) {         // Make default names, all known to be unique
      _names = new Names();
      _lastNameBig = true;
    } else {
      // Make empty to dodge asserts, then "add()" them all which will check
      // for compatible Vecs & names.
      _names = new Names().add(names);
    }
  }

  public void setNames(String[] columns) {
    if (columns.length != _vecs.len())
      throw new IllegalArgumentException("Size of array containing column names does not correspond to the number of vecs!");
    _names.add(columns);
  }
  /** Deep copy of Vecs and Keys and Names (but not data!) to a new random Key.
   *  The resulting Frame does not share with the original, so the set of Vecs
   *  can be freely hacked without disturbing the original Frame. */
  public Frame( Frame fr ) {this(Key.make(),fr);}

  public Frame(Key k, Frame fr ) {
    super( k );
    _names=  new Names(fr._names);
    _vecs = new VecAry(fr.vecs());
    _lastNameBig = fr._lastNameBig;
  }

  /** Default column name maker */
  public static String defaultColName( int col ) { return "C"+(1+col); }
  public static int defaultColId( String name ) {
    if(name.charAt(0) == 'C') {
      try {
        return Integer.valueOf(name.substring(1));
      } catch(Throwable t) { /*  fall through */}
    }
    return -1;
  }

  // Make unique names.  Efficient for the special case of appending endless
  // versions of "C123" style names where the next name is +1 over the prior
  // name.  All other names take the O(n^2) lookup.
  private int pint( String name ) {
    try { return Integer.valueOf(name.substring(1)); } 
    catch( NumberFormatException fe ) { }
    return 0;
  }

  /** Quick compatibility check between Frames.  Used by some tests for efficient equality checks. */
  public boolean isCompatible( Frame fr ) {
    if( numCols() != fr.numCols() ) return false;
    if( numRows() != fr.numRows() ) return false;
    return _vecs.isCompatible(fr.vecs());
  }

  /** Number of columns
   *  @return Number of columns */
  public int  numCols() { return _vecs.len(); }
  /** Number of rows
   *  @return Number of rows */
  public long numRows() { return _vecs.numRows(); }

  /** A single column name.
   *  @return the column name */
  public String name(int i) { return _names.getName(i); }
  public int find(String s) { return _names.getId(s); }


  // Add a bunch of vecs
  public VecAry add( String name, VecAry vecs) {
    assert vecs.len() == 1;
    return add(new String[]{name},vecs);
  }
  // Add a bunch of vecs
  public VecAry add( String[] names, VecAry vecs) {
    _names.add(names);
    _vecs.addVecs(vecs);
    return vecs;
  }

  /** Append a Frame onto this Frame.  Names are forced unique, by appending
   *  unique numbers if needed.
   *  @return the expanded Frame, for flow-coding */
  public Frame add( Frame fr ) {
    _names.add(fr._names);
    _vecs.addVecs(fr._vecs);
    return this;
  }

  /** Swap two Vecs in-place; useful for sorting columns by some criteria */
  public void swap( int lo, int hi ) {
    _vecs.swap(lo,hi);
    _names.swap(lo,hi);
  }

  /** Returns a subframe of this frame containing only vectors with desired names.
   *
   *  @param names list of vector names
   *  @return a new frame which collects vectors from this frame with desired names.
   *  @throws IllegalArgumentException if there is no vector with desired name in this frame.
   */
  public Frame subframe(String[] names) {
    return new Frame(null, names,vecs(names));
  }

  public Frame subframe(Names names) {
    names = names.intersect(_names);
    return new Frame(null, names ,vecs(names));
  }


  /** Actually remove/delete all Vecs from memory, not just from the Frame.
   *  @return the original Futures, for flow-coding */
  @Override protected Futures remove_impl(Futures fs) {
    return _vecs.remove(fs);
  }

  /** Create a subframe from given interval of columns.
   *  @param startIdx  index of first column (inclusive)
   *  @param endIdx index of the last column (exclusive)
   *  @return a new Frame containing specified interval of columns  */
  public Frame subframe(int startIdx, int endIdx) {
    return new Frame(null,Arrays.copyOfRange(_names.getNames(),startIdx,endIdx),_vecs.subRange(startIdx,endIdx));
  }

  /** Split this Frame; return a subframe created from the given column interval, and
   *  remove those columns from this Frame. 
   *  @param startIdx index of first column (inclusive)
   *  @param endIdx index of the last column (exclusive)
   *  @return a new Frame containing specified interval of columns */
  public Frame extractFrame(int startIdx, int endIdx) {
    Frame f = subframe(startIdx, endIdx);
    remove(startIdx, endIdx);
    return f;
  }

  /** Removes the column with a matching name.  
   *  @return The removed column */
  public VecAry remove( String... names ) {
    int [] ids = _names.remove(names);
    return _vecs.removeVecs(ids);
  }

  /** Removes a list of columns by index; the index list must be sorted
   *  @return an array of the removed columns */
  public VecAry remove( int... idxs ) {
    _names.remove(idxs);
    return _vecs.removeVecs(idxs);
  }

  /** Remove given interval of columns from frame.  Motivated by R intervals.
   *  @param startIdx - start index of column (inclusive)
   *  @param endIdx - end index of column (exclusive)
   *  @return array of removed columns  */
  VecAry remove(int startIdx, int endIdx) {
    _names.removeRange(startIdx,endIdx);
    return _vecs.removeRange(startIdx,endIdx);

  }


  // --------------------------------------------------------------------------
  static final int MAX_EQ2_COLS = 100000; // Limit of columns user is allowed to request

  private int [] getColAry(Object ocols) {
    int [] cols;
    long [] lcols;
    if (ocols instanceof Frame) {
      VecAry.Reader v = ((Frame) ocols).vecs().reader(true);
      int n = (int) v.length();
      lcols = new long[n];
      for (int i = 0; i < n; i++)
        lcols[i] = v.at8(i,0);
    }
    else if (ocols instanceof long[])
      lcols = (long[]) ocols;
    else throw new IllegalArgumentException("Columns is specified by an unsupported data type (" + ocols.getClass().getName() + ")");
    if(lcols[0] >= 0) {
      cols = new int[lcols.length];
      for (int i = 0; i < cols.length; ++i)
        cols[i] = (int) lcols[i];
    } else {
      Arrays.sort(lcols);
      if(lcols[lcols.length] >= 0)
        throw new IllegalArgumentException("columns must be either all positive or all negative");
      cols = new int[numCols() - lcols.length];
      int j = 0; int k = 0;
      for(int i = 0; i < numCols(); ++i)
        if(j < lcols.length && -lcols[lcols.length-1-j] == i) j++;
        else cols[k++] = (int)lcols[i];
      assert k == cols.length;
    }
    return cols;
  }

  /** In support of R, a generic Deep Copy and Slice.
   *
   *  <p>Semantics are a little odd, to match R's.  Each dimension spec can be:<ul>
   *  <li><em>null</em> - all of them
   *  <li><em>a sorted list of negative numbers (no dups)</em> - all BUT these
   *  <li><em>an unordered list of positive</em> - just these, allowing dups
   *  </ul>
   *
   *  <p>The numbering is 1-based; zero's are not allowed in the lists, nor are out-of-range values.
   *  @return the sliced Frame
   */
  public Frame deepSlice( Object orows, Object ocols ) {
    // ocols is either a long[] or a Frame-of-1-Vec
    int[] cols = ocols == null?null:getColAry(ocols);
    if (orows == null)
      if(ocols == null)
        return new Frame(null,_names._names,_vecs.getVecs(cols).deepCopy());
      else
        return new Frame(null,_names.getNames(cols),_vecs.getVecs(cols).deepCopy());
    if (orows instanceof long[]) {
      final long [] lrows = (long[])orows;
      if(lrows[0] >=  0) {
        throw H2O.unimpl(); // TODO
      } else {
        for(int i = 0; i < lrows.length; ++i)
          lrows[i] *=-1;
        Arrays.sort(lrows);
        return new MRTask(){
          @Override public void map(Chunk [] chks, NewChunk [] ncs) {
            int len = chks[0]._len;
            long start = chks[0].start();
            int j = Arrays.binarySearch(lrows,chks[0].start());
            if(j < 0) j = -j -1;
            int head = (int)(j - start); // 0:head are all in
            // add head
            for(int i = 0; i < chks.length; ++i)
              chks[i].add2NewChunk(ncs[i],0,head);
            int k = Arrays.binarySearch(lrows,chks[0].start()+len);
            if(k < 0) k = -k -1;
            int tail = (int)(k - start); // tail:len are all in
            // head:tail some are out
            if(head < tail) {
              int [] rowIds = new int[tail-head];
              int y = 0;
              for(int x = head; x < tail; ++x) {
                if(x + start == lrows[j]) j++;
                else rowIds[y++] = x;
              }
              rowIds = Arrays.copyOf(rowIds,y);
              for(int i = 0; i < chks.length; ++i)
                chks[i].add2NewChunk(ncs[i],rowIds);
            }
            for(int i = 0; i < chks.length; ++i)
              chks[i].add2NewChunk(ncs[i],tail,len);
          }
        }.doAll(ArrayUtils.select(_vecs.types(),cols),_vecs).outputFrame(new Names(_names.getNames(cols)),ArrayUtils.select(_vecs.domains(),cols));
      }
    }
    Frame frows = (Frame)orows;
    // It's a compatible Vec; use it as boolean selector.
    // Build column names for the result.
    VecAry vecs = (VecAry) _vecs.clone();
    vecs.addVecs(frows.vecs());
    return new DeepSelect().doAll(ArrayUtils.select(_vecs.types(),cols),vecs).outputFrame(new Names(_names.getNames(cols)),ArrayUtils.select(_vecs.domains(),cols));
  }


  // Convert len rows starting at off to a 2-d ascii table
  @Override public String toString( ) { return toString(0,20); }

  public String toString(long off, int len) { return toTwoDimTable(off, len).toString(); }
  public String toString(long off, int len, boolean rollups) { return toTwoDimTable(off, len, rollups).toString(); }
  public TwoDimTable toTwoDimTable(long off, int len ) { return toTwoDimTable(off,len,true); }
  public TwoDimTable toTwoDimTable(long off, int len, boolean rollups ) {
    if( off > numRows() ) off = numRows();
    if( off+len > numRows() ) len = (int)(numRows()-off);

    String[] rowHeaders = new String[len];
    int H=0;
    if( rollups ) {
      H = 5;
      rowHeaders = new String[len+H];
      rowHeaders[0] = "min";
      rowHeaders[1] = "mean";
      rowHeaders[2] = "stddev";
      rowHeaders[3] = "max";
      rowHeaders[4] = "missing";
      for( int i=0; i<len; i++ ) rowHeaders[i+H]=""+(off+i);
    }

    final int ncols = numCols();
    final VecAry vecs = _vecs;
    String[] coltypes = new String[ncols];
    String[][] strCells = new String[len+H][ncols];
    double[][] dblCells = new double[len+H][ncols];
    final BufferedString tmpStr = new BufferedString();

    RollupStats [] rs = rollups?vecs.getRollups():null;
    VecAry.Reader vReader = _vecs.reader(true);
    for( int i=0; i<ncols; i++ ) {
      if( rollups ) {
        dblCells[0][i] = rs[i].min();
        dblCells[1][i] = rs[i].mean();
        dblCells[2][i] = rs[i].sigma();
        dblCells[3][i] = rs[i].max();
        dblCells[4][i] = rs[i].naCnt();
      }
      switch( vecs.types()[i]) {
      case Vec.T_BAD:
        coltypes[i] = "string";
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = null; dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_STR :
        coltypes[i] = "string";
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = vReader.isNA(off+j,i) ? "" : vReader.atStr(tmpStr,off+j,i).toString(); dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_CAT:
        coltypes[i] = "string"; 
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = vReader.isNA(off+j,i) ? "" : vReader.factor(off+j,i);  dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_TIME:
        coltypes[i] = "string";
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = vReader.isNA(off+j,i) ? "" : fmt.print(vReader.at8(off+j,i)); dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_NUM:
        coltypes[i] = _vecs.isInt(i) ? "long" : "double";
        for( int j=0; j<len; j++ ) { dblCells[j+H][i] = vReader.isNA(off+j,i) ? TwoDimTable.emptyDouble : vReader.at(off + j,i); strCells[j+H][i] = null; }
        break;
      case Vec.T_UUID:
        throw H2O.unimpl();
      default:
        System.err.println("bad vector type during debug print: "+_vecs.types()[i]);
        throw H2O.fail();
      }
    }
    return new TwoDimTable("Frame "+_key,numRows()+" rows and "+numCols()+" cols",rowHeaders,/* clone the names, the TwoDimTable will replace nulls with ""*/_names.getNames().clone(),coltypes,null, "", strCells, dblCells);
  }
  /**
   *  Last column is a bit vec indicating whether or not to take the row.
   */
  public static class DeepSelect extends MRTask<DeepSelect> {
    @Override public void map( Chunk chks[], NewChunk [] ncs) {
      Chunk pred = chks[chks.length - 1];
      int[] ids = new int[pred._len];
      int selected = pred.nonzeros(ids);
      ids = Arrays.copyOf(ids,selected);
      for(int i = 0; i < chks.length; ++i)
        chks[i].add2NewChunk(ncs[i],ids);
    }
  }

  public static Job export(Frame fr, String path, String frameName, boolean overwrite) {
    // Validate input
    boolean fileExists = H2O.getPM().exists(path);
    if (overwrite && fileExists) {
      Log.warn("File " + path + " exists, but will be overwritten!");
    } else if (!overwrite && fileExists) {
      throw new H2OIllegalArgumentException(path, "exportFrame", "File " + path + " already exists!");
    }
    InputStream is = (fr).toCSV(true, false);
    Job job =  new Job(fr._key,"water.fvec.Frame","Export dataset");
    FrameUtils.ExportTask t = new FrameUtils.ExportTask(is, path, frameName, overwrite, job);
    return job.start(t, fr.vecs().nChunks());
  }

  /** Convert this Frame to a CSV (in an {@link InputStream}), that optionally
   *  is compatible with R 3.1's recent change to read.csv()'s behavior.
   *  @return An InputStream containing this Frame as a CSV */
  public InputStream toCSV(boolean headers, boolean hex_string) {
    return new CSVStream(headers, hex_string);
  }

  public class CSVStream extends InputStream {
    private final boolean _hex_string;
    byte[] _line;
    int _position;
    public volatile int _curChkIdx;
    long _row;

    CSVStream(boolean headers, boolean hex_string) {
      _curChkIdx=0;
      _hex_string = hex_string;
      StringBuilder sb = new StringBuilder();

      if( headers ) {
        sb.append('"').append(_names.getName(0)).append('"');
        for(int i = 1; i < numCols(); i++)
          sb.append(',').append('"').append(_names.getName(i)).append('"');
        sb.append('\n');
      }
      _line = sb.toString().getBytes();
    }

    byte[] getBytesForRow() {
      StringBuilder sb = new StringBuilder();
      VecAry vecs = _vecs;
      BufferedString tmpStr = new BufferedString();
      VecAry.Reader vReader = vecs.reader(false);
      for( int i = 0; i < vecs.len(); i++ ) {
        if(i > 0) sb.append(',');
        if(!vReader.isNA(_row,i)) {
          if( vecs.isCategorical(i) ) sb.append('"').append(vReader.factor(_row,i)).append('"');
          else if( vecs.isUUID(i) ) sb.append(PrettyPrint.UUID(vReader.at16l(_row,i), vReader.at16h(_row,i)));
          else if( vecs.isInt(i) ) sb.append(vReader.at8(_row,i));
          else if (vecs.isString(i)) sb.append('"').append(vReader.atStr(tmpStr, _row,i)).append('"');
          else {
            double d = vReader.at(_row,i);
            // R 3.1 unfortunately changed the behavior of read.csv().
            // (Really type.convert()).
            //
            // Numeric values with too much precision now trigger a type conversion in R 3.1 into a factor.
            //
            // See these discussions:
            //   https://bugs.r-project.org/bugzilla/show_bug.cgi?id=15751
            //   https://stat.ethz.ch/pipermail/r-devel/2014-April/068778.html
            //   http://stackoverflow.com/questions/23072988/preserve-old-pre-3-1-0-type-convert-behavior
            String s = _hex_string ? Double.toHexString(d) : Double.toString(d);
            sb.append(s);
          }
        }
      }
      sb.append('\n');
      return sb.toString().getBytes();
    }

    @Override public int available() throws IOException {
      // Case 1:  There is more data left to read from the current line.
      if (_position != _line.length) {
        return _line.length - _position;
      }

      // Case 2:  Out of data.
      if (_row == numRows()) {
        return 0;
      }

      // Case 3:  Return data for the current row.
      //          Note this will fast-forward past empty chunks.
      _curChkIdx = vecs().elem2ChunkIdx(_row);
      _line = getBytesForRow();
      _position = 0;
      _row++;

      return _line.length;
    }

    @Override public void close() throws IOException {
      super.close();
      _line = null;
    }

    @Override public int read() throws IOException {
      return available() == 0 ? -1 : _line[_position++];
    }

    @Override public int read(byte[] b, int off, int len) throws IOException {
      int n = available();
      if(n > 0) {
        n = Math.min(n, len);
        System.arraycopy(_line, _position, b, off, n);
        _position += n;
      }
      return n;
    }
  }

  @Override public Class<KeyV3.FrameKeyV3> makeSchema() { return KeyV3.FrameKeyV3.class; }
}
