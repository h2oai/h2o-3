package water.api;

import water.*;
import water.api.KeyV1.FrameKeyV1;
import water.api.KeyV1.VecKeyV1;
import water.fvec.*;
import water.fvec.Frame.VecSpecifier;
import water.parser.ValueString;
import water.util.DocGen.HTML;
import water.util.FrameUtils;
import water.util.PrettyPrint;

// TODO: need a base (versionless) class!
public class FrameV2 extends Schema<Frame, FrameV2> {

  // Input fields
  @API(help="Key to inspect",required=true)
  public FrameKeyV1 key;

  @API(help="Row offset to display",direction=API.Direction.INPUT)
  public long row_offset;

  @API(help="Number of rows to display",direction=API.Direction.INOUT)
  public int row_count;

  // Output fields
  @API(help="checksum", direction=API.Direction.OUTPUT)
  public long checksum;

  @API(help="Number of rows", direction=API.Direction.OUTPUT)
  public long rows;

  @API(help="Total data size in bytes", direction=API.Direction.OUTPUT)
  public long byte_size;

  @API(help="Raw unparsed text", direction=API.Direction.OUTPUT)
  public boolean is_text;

  @API(help="Default percentiles, from 0 to 1", direction=API.Direction.OUTPUT)
  public double[] default_percentiles;

  @API(help="Columns", direction=API.Direction.OUTPUT)
  public ColV2[] columns;

  @API(help="Compatible models, if requested", direction=API.Direction.OUTPUT)
  public String[] compatible_models;

  @API(help="The set of vector keys in the Frame", direction=API.Direction.OUTPUT)
  public VecKeyV1[] vec_keys;

  @API(help="Chunk summary", direction=API.Direction.OUTPUT)
  public TwoDimTableV1 chunk_summary;

  public static class ColSpecifierV2 extends Schema<VecSpecifier, ColSpecifierV2> {
    public ColSpecifierV2() { }
    public ColSpecifierV2(String column_name) {
      this.column_name = column_name;
    }

    @API(help="Name of the column", direction= API.Direction.INOUT)
    public String column_name;

    @API(help="List of fields which specify columns that must contain this column", direction= API.Direction.INOUT)
    public String[] is_member_of_frames;
  }

  public static class ColV2 extends Schema<Vec, ColV2> {

    public ColV2() {}

    @API(help="label", direction=API.Direction.OUTPUT)
    public String label;

    @API(help="missing", direction=API.Direction.OUTPUT)
    public long missing_count;

    @API(help="zeros", direction=API.Direction.OUTPUT)
    public long zero_count;

    @API(help="positive infinities", direction=API.Direction.OUTPUT)
    public long positive_infinity_count;

    @API(help="negative infinities", direction=API.Direction.OUTPUT)
    public long negative_infinity_count;

    @API(help="mins", direction=API.Direction.OUTPUT)
    public double[] mins;

    @API(help="maxs", direction=API.Direction.OUTPUT)
    public double[] maxs;

    @API(help="mean", direction=API.Direction.OUTPUT)
    public double mean;

    @API(help="sigma", direction=API.Direction.OUTPUT)
    public double sigma;

    @API(help="datatype: {enum, string, int, real, time, uuid}", direction=API.Direction.OUTPUT)
    public String type;

    @API(help="domain; not-null for enum columns only", direction=API.Direction.OUTPUT)
    public String[] domain;

    @API(help="data", direction=API.Direction.OUTPUT)
    public double[] data;

    @API(help="string data", direction=API.Direction.OUTPUT)
    public String[] string_data;

    @API(help="decimal precision, -1 for all digits", direction=API.Direction.OUTPUT)
    public byte precision;

    @API(help="Histogram bins; null if not computed", direction=API.Direction.OUTPUT)
    public long[] histogram_bins;

    @API(help="Start of histogram bin zero", direction=API.Direction.OUTPUT)
    public double histogram_base;

    @API(help="Stride per bin", direction=API.Direction.OUTPUT)
    public double histogram_stride;

    @API(help="Percentile values, matching the default percentiles", direction=API.Direction.OUTPUT)
    public double[] percentiles;

    transient Vec _vec;

    ColV2(String name, Vec vec, long off, int len) {
      label=name;
      missing_count = vec.naCnt();
      zero_count = vec.length()-vec.nzCnt()- missing_count;
      positive_infinity_count = vec.pinfs();
      negative_infinity_count = vec.ninfs();
      mins  = vec.mins();
      maxs  = vec.maxs();
      mean  = vec.mean();
      sigma = vec.sigma();
      type  = vec.isEnum() ? "enum" : vec.isUUID() ? "uuid" : vec.isString() ? "string" : (vec.isInt() ? (vec.isTime() ? "time" : "int") : "real");
      domain = vec.domain();
      len = (int)Math.min(len,vec.length()-off);
      if( vec.isUUID() ) {
        string_data = new String[len];
        for (int i = 0; i < len; i++)
          string_data[i] = vec.isNA(off + i) ? null : PrettyPrint.UUID(vec.at16l(off + i), vec.at16h(off + i));
        data = null;
      } else if ( vec.isString() ) {
        string_data = new String[len];
        ValueString vstr = new ValueString();
        for (int i = 0; i < len; i++)
          string_data[i] = vec.isNA(off + i) ? null : vec.atStr(vstr,off + i).toString();
        data = null;
      } else {
        data = MemoryManager.malloc8d(len);
        for( int i=0; i<len; i++ )
          data[i] = vec.at(off+i);
        string_data = null;
      }
      _vec = vec;               // Better HTML display, not in the JSON
      if (len > 0)  // len == 0 is presumed to be a header file
        precision = vec.chunkForRow(0).precision();

      // Histogram data is only computed on-demand.  By default here we do NOT
      // compute it, but will return any prior computed & cached histogram.
      histogram_bins = vec.lazy_bins();
      histogram_base = histogram_bins ==null ? 0 : vec.base();
      histogram_stride = histogram_bins ==null ? 0 : vec.stride();
      percentiles = histogram_bins ==null ? null : vec.pctiles();
    }

    public void clearBinsField() {
      this.histogram_bins = null;
    }
  }

  // Constructor for when called from the Inspect handler instead of RequestServer
  transient Frame _fr;         // Avoid an racey update to Key; cached loaded value

  public FrameV2() { super(); }

  /* Key-only constructor, for the times we only want to return the key. */
  FrameV2( Key key ) { this.key = new FrameKeyV1(key); }

  FrameV2( Frame fr ) {
    this(fr, 1, (int)fr.numRows()); // NOTE: possible len truncation
  }

  /** TODO: refactor together with fillFromImpl(). */
  FrameV2( Frame fr, long off2, int len2 ) {
    if( off2==0 ) off2=1;       // 1-based row-numbering; so default offset is 1
    if( len2==0 ) len2=100;     // Default length if zero passed
    key = new FrameKeyV1(fr._key);
    _fr = fr;
    row_offset = off2-1;
    rows = fr.numRows();
    row_count = (int)Math.min(len2,rows);
    byte_size = fr.byteSize();
    columns = new ColV2[fr.numCols()];
    Key[] keys = fr.keys();
    if(keys != null && keys.length > 0) {
      vec_keys = new VecKeyV1[keys.length];
      for (int i = 0; i < keys.length; i++)
        vec_keys[i] = new VecKeyV1(keys[i]);
    }
    Vec[] vecs = fr.vecs();
    for( int i=0; i<columns.length; i++ )
      columns[i] = new ColV2(fr._names[i],vecs[i], row_offset, row_count);
    is_text = fr.numCols()==1 && vecs[0] instanceof ByteVec;
    default_percentiles = Vec.PERCENTILES;
    this.checksum = fr.checksum();
    chunk_summary = new TwoDimTableV1().fillFromImpl(FrameUtils.chunkSummary(fr).toTwoDimTable());
  }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling from the impl
  @Override public FrameV2 fillFromImpl(Frame f) {
    this._fr = f;
    this.key = new FrameKeyV1(f._key);
    this.checksum = _fr.checksum();
    row_offset = 0;
    rows = _fr.numRows();
    // TODO: pass in offset and column from Inspect page
    // if( h instanceof InspectHandler ) { off = ((InspectHandler)h)._off;  len = ((InspectHandler)h)._len; }
    if( row_offset == 0 ) row_offset = 1;     // 1-based row-numbering from REST, so default offset is 1
    if( row_count == 0 ) row_count = 100;
    row_offset = row_offset -1;                // 0-based row-numbering
    row_count = (int)Math.min(row_count,rows);
    byte_size = _fr.byteSize();
    columns = new ColV2[_fr.numCols()];
    Key[] keys = _fr.keys();
    if(keys != null && keys.length > 0) {
      vec_keys = new VecKeyV1[keys.length];
      for (int i = 0; i < keys.length; i++)
        vec_keys[i] = new VecKeyV1(keys[i]);
    }
    Vec[] vecs = _fr.vecs();
    for( int i=0; i<columns.length; i++ )
      columns[i] = new ColV2(_fr._names[i],vecs[i], row_offset, row_count);
    is_text = f.numCols()==1 && vecs[0] instanceof ByteVec;
    default_percentiles = Vec.PERCENTILES;
    chunk_summary = new TwoDimTableV1().fillFromImpl(FrameUtils.chunkSummary(f).toTwoDimTable());
    return this;
  }

  public void clearBinsField() {
    for (ColV2 col: columns)
      col.clearBinsField();
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    String[] urls = RequestServer.frameChoices(getSchemaVersion(),_fr);
    for( String url : urls )
      ab.href("hex",url,url);

    // Main data display
    // Column names
    String titles[] = new String[_fr._names.length+1];
    titles[0]="";
    System.arraycopy(_fr._names,0,titles,1,_fr._names.length);
    ab.arrayHead(titles);

    // Rollup data
    final long nrows = _fr.numRows();
    formatRow(ab,"","type" ,new ColOp() { String op(ColV2 c) { return c.type; } } );
    formatRow(ab,"","min"  ,new ColOp() { String op(ColV2 c) { return rollUpStr(c, c.missing_count ==nrows ? Double.NaN : c.mins[0]); } } );
    formatRow(ab,"","max"  ,new ColOp() { String op(ColV2 c) { return rollUpStr(c, c.missing_count ==nrows ? Double.NaN : c.maxs[0]); } } );
    formatRow(ab,"","mean" ,new ColOp() { String op(ColV2 c) { return rollUpStr(c, c.missing_count ==nrows ? Double.NaN : c.mean   ); } } );
    formatRow(ab,"","sigma",new ColOp() { String op(ColV2 c) { return rollUpStr(c, c.missing_count ==nrows ? Double.NaN : c.sigma  ); } } );

    // Optional rows: missing elements, zeros, positive & negative infinities, levels
    for( ColV2 c : columns ) if( c.missing_count > 0 )
        { formatRow(ab,"class='warning'","missing",new ColOp() { String op(ColV2 c) { return c.missing_count == 0 ?"":Long.toString(c.missing_count);}}); break; }
    for( ColV2 c : columns ) if( c.zero_count > 0 )
        { formatRow(ab,"class='warning'","zeros"  ,new ColOp() { String op(ColV2 c) { return c.zero_count == 0 ?"":Long.toString(c.zero_count);}}); break; }
    for( ColV2 c : columns ) if( c.positive_infinity_count > 0 )
        { formatRow(ab,"class='warning'","+infins",new ColOp() { String op(ColV2 c) { return c.positive_infinity_count == 0 ?"":Long.toString(c.positive_infinity_count);}}); break; }
    for( ColV2 c : columns ) if( c.negative_infinity_count > 0 )
        { formatRow(ab,"class='warning'","-infins",new ColOp() { String op(ColV2 c) { return c.negative_infinity_count == 0 ?"":Long.toString(c.negative_infinity_count);}}); break; }
    for( ColV2 c : columns ) if( c.domain!=null)
        { formatRow(ab,"class='warning'","levels" ,new ColOp() { String op(ColV2 c) { return c.domain==null?"":Long.toString(c.domain.length);}}); break; }

    // Frame data
    final int len = columns.length > 0 ? columns[0].data.length : 0;
    for( int i=0; i<len; i++ ) {
      final int row = i;
      formatRow(ab,"",Long.toString(row_offset +row+1),new ColOp() {
          String op(ColV2 c) {
            return formatCell(c.data==null?0:c.data[row],c.string_data ==null?null:c.string_data[row],c,0); }
        } );
    }

    ab.arrayTail();

    return ab.bodyTail();
  }

  private abstract static class ColOp { abstract String op(ColV2 v); }
  private String rollUpStr(ColV2 c, double d) {
    return formatCell(c.domain!=null || "uuid".equals(c.type) || "string".equals(c.type) ? Double.NaN : d,null,c,4);
  }

  private void formatRow( HTML ab, String color, String msg, ColOp vop ) {
    ab.p("<tr").p(color).p(">");
    ab.cell(msg);
    for( ColV2 c : columns )  ab.cell(vop.op(c));
    ab.p("</tr>");
  }

  private String formatCell( double d, String str, ColV2 c, int precision ) {
    if( Double.isNaN(d) ) return "-";
    if( c.domain!=null ) return c.domain[(int)d];
    if( "uuid".equals(c.type) || "string".equals(c.type)) {
      // UUID and String handling
      if( str==null ) return "-";
      return "<b style=\"font-family:monospace;\">"+str+"</b>";
    }

    long l = (long)d;
    if( (double)l == d ) return Long.toString(l);
    if( precision > 0 ) return x2(d,PrettyPrint.pow10(-precision));
    Chunk chk = c._vec.chunkForRow(row_offset);
    Class Cc = chk.getClass();
    if( Cc == C1SChunk.class ) return x2(d,((C1SChunk)chk).scale());
    if( Cc == C2SChunk.class ) return x2(d,((C2SChunk)chk).scale());
    if( Cc == C4SChunk.class ) return x2(d,((C4SChunk)chk).scale());
    return Double.toString(d);
  }

  private static String x2( double d, double scale ) {
    String s = Double.toString(d);
    // Double math roundoff error means sometimes we get very long trailing
    // strings of junk 0's with 1 digit at the end... when we *know* the data
    // has only "scale" digits.  Chop back to actual digits
    int ex = (int)Math.log10(scale);
    int x = s.indexOf('.');
    int y = x+1+(-ex);
    if( x != -1 && y < s.length() ) s = s.substring(0,x+1+(-ex));
    while( s.charAt(s.length()-1)=='0' )
      s = s.substring(0,s.length()-1);
    return s;
  }
}
