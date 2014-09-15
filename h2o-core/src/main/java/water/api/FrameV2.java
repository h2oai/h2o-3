package water.api;

import water.*;
import water.fvec.*;
import water.parser.ValueString;
import water.util.DocGen.HTML;
import water.util.PrettyPrint;

// Private Schema class for the Inspect handler.  Not a registered Schema.
class FrameV2 extends Schema<Frame, FrameV2> {

  // Input fields
  @API(help="Key to inspect",required=true)
  Key key;

  @API(help="Row offset to display",direction=API.Direction.INPUT)
  long off;

  @API(help="Number of rows to display",direction=API.Direction.INOUT)
  int len;

  // Output fields
  @API(help="Unique id")
  protected UniqueIdBase unique_id;

  @API(help="Number of rows")
  long rows;

  @API(help="Total data size in bytes")
  long byteSize;

  @API(help="Raw unparsed text")
  boolean isText;

  @API(help="Default percentiles, from 0 to 1")
  final double[] default_pctiles;

  @API(help="Columns")
  Col[] columns;

  // Output fields one-per-column
  private static class Col extends Iced {
    @API(help="label")
    final String label;

    @API(help="missing")
    final long missing;

    @API(help="zeros")
    final long zeros;

    @API(help="positive infinities")
    final long pinfs;

    @API(help="negative infinities")
    final long ninfs;

    @API(help="mins")
    final double[] mins;

    @API(help="maxs")
    final double[] maxs;

    @API(help="mean")
    final double mean;

    @API(help="sigma")
    final double sigma;

    @API(help="datatype: {enum, string, int, real, time, uuid}")
    final String type;

    @API(help="domain; not-null for enum columns only")
    final String[] domain;

    @API(help="data")
    final double[] data;

    @API(help="string data")
    final String[] str_data;

    @API(help="decimal precision, -1 for all digits")
    final byte precision;

    @API(help="Histogram bins; null if not computed")
    final long[] bins;

    @API(help="Start of histogram bin zero")
    final double base;

    @API(help="Stride per bin")
    final double stride;

    @API(help="Percentile values, matching the default percentiles")
    final double[] pctiles;

    transient Vec _vec;

    Col( String name, Vec vec, long off, int len ) {
      label=name;
      RollupStats rs = vec.rollupStats();
      missing = rs._naCnt;
      zeros = vec.length()-rs._nzCnt;
      pinfs = rs._pinfs;
      ninfs = rs._ninfs;
      mins  = rs._mins;
      maxs  = rs._maxs;
      mean  = rs._mean;
      sigma = rs._sigma;
      type  = vec.isEnum() ? "enum" : vec.isUUID() ? "uuid" : vec.isString() ? "string" : (vec.isInt() ? (vec.isTime() ? "time" : "int") : "real");
      domain = vec.domain();
      len = (int)Math.min(len,vec.length()-off);
      if( vec.isUUID() ) {
        str_data = new String[len];
        for (int i = 0; i < len; i++)
          str_data[i] = vec.isNA(off + i) ? null : PrettyPrint.UUID(vec.at16l(off + i), vec.at16h(off + i));
        data = null;
      } else if ( vec.isString() ) {
        str_data = new String[len];
        ValueString vstr = new ValueString();
        for (int i = 0; i < len; i++)
          str_data[i] = vec.isNA(off + i) ? null : vec.atStr(vstr,off + i).toString();
        data = null;
      } else {
        data = MemoryManager.malloc8d(len);
        for( int i=0; i<len; i++ )
          data[i] = vec.at(off+i);
        str_data = null;
      }
      _vec = vec;               // Better HTML display, not in the JSON
      precision = vec.chunkForRow(0).precision();

      // Histogram data is only computed on-demand.  By default here we do NOT
      // compute it, but will return any prior computed & cached histogram.
      bins  = rs._bins;
      base  = bins==null ? 0 : rs.h_base();
      stride= bins==null ? 0 : rs.h_stride();
      pctiles=rs._pctiles;
    }
  }

  // Constructor for when called from the Inspect handler instead of RequestServer
  transient Frame _fr;         // Avoid an racey update to Key; cached loaded value

  FrameV2( Frame fr, long off2, int len2 ) {
    if( off2==0 ) off2=1;       // 1-based row-numbering; so default offset is 1
    if( len2==0 ) len2=100;     // Default length if zero passed
    key = fr._key;
    _fr = fr;
    off = off2-1;
    rows = fr.numRows();
    len = (int)Math.min(len2,rows);
    byteSize = fr.byteSize();
    columns = new Col[fr.numCols()];
    Vec[] vecs = fr.vecs();
    for( int i=0; i<columns.length; i++ )
      columns[i] = new Col(fr._names[i],vecs[i],off,len);
    isText = fr.numCols()==1 && vecs[0] instanceof ByteVec;
    default_pctiles = RollupStats.PERCENTILES;
  }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public Frame createImpl( ) {
    if (null == key)
      throw H2O.fail("Cannot create a Frame from a null key.");
    return new Frame(key);
  }

  // Version&Schema-specific filling from the impl
  @Override public FrameV2 fillFromImpl(Frame f) {
    off = 0;
    rows = _fr.numRows();
    // TODO: pass in offset and column from Inspect page
    // if( h instanceof InspectHandler ) { off = ((InspectHandler)h)._off;  len = ((InspectHandler)h)._len; }
    if( off == 0 ) off = 1;     // 1-based row-numbering from REST, so default offset is 1
    if( len == 0 ) len = 100;
    off = off-1;                // 0-based row-numbering
    len = (int)Math.min(len,rows);
    byteSize = _fr.byteSize();
    columns = new Col[_fr.numCols()];
    Vec[] vecs = _fr.vecs();
    for( int i=0; i<columns.length; i++ )
      columns[i] = new Col(_fr._names[i],vecs[i],off,len);
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    String[] urls = RequestServer.frameChoices(getVersion(),_fr);
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
    formatRow(ab,"","type" ,new ColOp() { String op(Col c) { return c.type; } } );
    formatRow(ab,"","min"  ,new ColOp() { String op(Col c) { return rollUpStr(c, c.missing==nrows ? Double.NaN : c.mins[0]); } } );
    formatRow(ab,"","max"  ,new ColOp() { String op(Col c) { return rollUpStr(c, c.missing==nrows ? Double.NaN : c.maxs[0]); } } );
    formatRow(ab,"","mean" ,new ColOp() { String op(Col c) { return rollUpStr(c, c.missing==nrows ? Double.NaN : c.mean   ); } } );
    formatRow(ab,"","sigma",new ColOp() { String op(Col c) { return rollUpStr(c, c.missing==nrows ? Double.NaN : c.sigma  ); } } );

    // Optional rows: missing elements, zeros, positive & negative infinities, levels
    for( Col c : columns ) if( c.missing > 0 )
        { formatRow(ab,"class='warning'","missing",new ColOp() { String op(Col c) { return c.missing== 0 ?"":Long.toString(c.missing      );}}); break; }
    for( Col c : columns ) if( c.zeros   > 0 )
        { formatRow(ab,"class='warning'","zeros"  ,new ColOp() { String op(Col c) { return c.zeros  == 0 ?"":Long.toString(c.zeros        );}}); break; }
    for( Col c : columns ) if( c.pinfs   > 0 )
        { formatRow(ab,"class='warning'","+infins",new ColOp() { String op(Col c) { return c.pinfs  == 0 ?"":Long.toString(c.pinfs        );}}); break; }
    for( Col c : columns ) if( c.ninfs   > 0 )
        { formatRow(ab,"class='warning'","-infins",new ColOp() { String op(Col c) { return c.ninfs  == 0 ?"":Long.toString(c.ninfs        );}}); break; }
    for( Col c : columns ) if( c.domain!=null)
        { formatRow(ab,"class='warning'","levels" ,new ColOp() { String op(Col c) { return c.domain==null?"":Long.toString(c.domain.length);}}); break; }

    // Frame data
    final int len = columns.length > 0 ? columns[0].data.length : 0;
    for( int i=0; i<len; i++ ) {
      final int row = i;
      formatRow(ab,"",Long.toString(off+row+1),new ColOp() {
          String op(Col c) {
            return formatCell(c.data==null?0:c.data[row],c.str_data==null?null:c.str_data[row],c,0); }
        } );
    }

    ab.arrayTail();

    return ab.bodyTail();
  }

  private abstract static class ColOp { abstract String op(Col v); }
  private String rollUpStr(Col c, double d) {
    return formatCell(c.domain!=null || "uuid".equals(c.type) || "string".equals(c.type) ? Double.NaN : d,null,c,4);
  }

  private void formatRow( HTML ab, String color, String msg, ColOp vop ) {
    ab.p("<tr").p(color).p(">");
    ab.cell(msg);
    for( Col c : columns )  ab.cell(vop.op(c));
    ab.p("</tr>");
  }

  private String formatCell( double d, String str, Col c, int precision ) {
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
    Chunk chk = c._vec.chunkForRow(off);
    Class Cc = chk.vec().chunkForRow(off).getClass();
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
