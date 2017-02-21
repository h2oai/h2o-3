package water.api.schemas3;

import jsr166y.RecursiveAction;
import water.Futures;
import water.Key;
import water.MemoryManager;
import water.api.*;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.fvec.*;
import water.fvec.Frame.VecSpecifier;
import water.parser.BufferedString;
import water.util.*;

/**
 * All the details on a Frame.  Note that inside ColV3 there are fields which won't be
 * populated if we don't compute rollups, e.g. via
 * the REST API endpoint /Frames/<frameid>/columns/<colname>/summary.
 */
public class FrameV3 extends FrameBaseV3<Frame, FrameV3> {

  // Input fields
  @API(help="Row offset to display",direction=API.Direction.INPUT)
  public long row_offset;

  @API(help="Number of rows to display",direction=API.Direction.INOUT)
  public int row_count;

  @API(help="Column offset to return", direction=API.Direction.INOUT)
  public int column_offset;

  @API(help="Number of columns to return", direction=API.Direction.INOUT)
  public int column_count;

  @API(help="Total number of columns in the Frame", direction=API.Direction.INOUT)
  public int total_column_count;

  // Output fields
  @API(help="checksum", direction=API.Direction.OUTPUT)
  public long checksum;

  @API(help="Number of rows in the Frame", direction=API.Direction.OUTPUT)
  public long rows;

  @API(help="Number of columns in the Frame", direction=API.Direction.OUTPUT)
  public long num_columns;

  @API(help="Default percentiles, from 0 to 1", direction=API.Direction.OUTPUT)
  public double[] default_percentiles;

  @API(help="Columns in the Frame", direction=API.Direction.OUTPUT)
  public ColV3[] columns;

  @API(help="Compatible models, if requested", direction=API.Direction.OUTPUT)
  public String[] compatible_models;

  @API(help="Chunk summary", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 chunk_summary;

  @API(help="Distribution summary", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 distribution_summary;

  public static class ColSpecifierV3 extends SchemaV3<VecSpecifier, ColSpecifierV3> {
    public ColSpecifierV3() { }
    public ColSpecifierV3(String column_name) {
      this.column_name = column_name;
    }

    @API(help="Name of the column", direction= API.Direction.INOUT)
    public String column_name;

    @API(help="List of fields which specify columns that must contain this column", direction= API.Direction.INOUT)
    public String[] is_member_of_frames;
  }

  public static class ColV3 extends SchemaV3<Vec, ColV3> {

    public ColV3() {}

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

    @API(help="domain; not-null for categorical columns only", direction=API.Direction.OUTPUT)
    public String[] domain;

    @API(help="cardinality of this column's domain; not-null for categorical columns only", direction=API.Direction.OUTPUT)
    public int domain_cardinality;

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

    transient VecAry _vec;

    ColV3(String name) {
      label=name;
    }

    public void clearBinsField() {
      this.histogram_bins = null;
    }
  }

  public FrameV3() { super(); }

  /* Key-only constructor, for the times we only want to return the key. */
  public FrameV3(Key<Frame> frame_id) { this.frame_id = new FrameKeyV3(frame_id); }

  public FrameV3(Frame fr) {
    this(fr, 1l, (int) fr.numRows(), 0, fr.numCols()); // NOTE: possible row len truncation
  }



  public FrameV3(Frame f, long row_offset, int row_count, int column_offset, int column_count) {
    this.fillFromImpl(f, row_offset, row_count, column_offset, column_count);
  }

  @Override public FrameV3 fillFromImpl(Frame f) {
    return fillFromImpl(f, 1, (int)f.numRows(), 0, 0);
  }

  public FrameV3 fillFromImpl(Frame f, long row_offset, int row_count, int column_offset, int column_count) {
    if( row_count == 0 ) row_count = 100;                                 // 100 rows by default
    if( column_count == 0 ) column_count = f.numCols() - column_offset; // full width by default

    row_count    = (int) Math.min(row_count, row_offset + f.numRows());
    column_count = Math.min(column_count, column_offset + f.numCols());


    this.frame_id = new FrameKeyV3(f._key);
    this.checksum = f.checksum();
    this.byte_size = f.byteSize();

    this.row_offset = row_offset;
    this.rows = f.numRows();
    this.num_columns = f.numCols();
    this.row_count = row_count;


    this.total_column_count = f.numCols();
    this.column_offset = column_offset;
    this.column_count = column_count;

    this.columns = new ColV3[column_count];
    VecAry vecs = f.vecs();
    Futures fs = new Futures();
    // Compute rollups in parallel as needed, by starting all of them and using
    // them when filling in the ColV3 Schemas
    RollupsAry rsa = vecs.rollupStats();
    final int rcnt = row_count;
    final long roff = row_offset;

    VecAry.Reader rdr = vecs.new Reader();
    for( int i = 0; i < column_count; i++ ) {
      int c = column_offset+i;
      RollupStats rs = rsa.getRollups(c);
      columns[i] = new ColV3(f._names[c]);
      columns[i].missing_count = rs.naCnt();
      columns[i].zero_count = vecs.length() - rs.nzCnt(c) - columns[i].missing_count;
      columns[i].positive_infinity_count = rs.pinfs();
      columns[i].negative_infinity_count = rs.ninfs();
      columns[i].mins = rs.mins();
      columns[i].maxs = rs.maxs();
      columns[i].mean = rs.mean();
      columns[i].sigma = rs.sigma();
      // Histogram data is only computed on-demand.  By default here we do NOT
      // compute it, but will return any prior computed & cached histogram.
      columns[i].histogram_bins  = rs.lazy_bins();
      if(columns[i].histogram_bins != null) {
        columns[i].histogram_base = rs.h_base();
        columns[i].histogram_stride = rs.h_stride();
        columns[i].percentiles = rs.pctiles();
      }
      byte t = vecs.getType(c);
      if(t == Vec.T_NUM)
        columns[i].type = rs.isInt()?"int":"real";
      else
        columns[i].type  = vecs.get_type_str(c);

      int len = (int)Math.min(row_count,vecs.length()-row_offset);
      if( vecs.isUUID(c) ) {
        columns[i].string_data = new String[len];
        for (int j = 0; j < len; j++)
          columns[i].string_data[j] = rdr.isNA(row_offset+j,c) ? null : PrettyPrint.UUID(rdr.at16l(row_offset+j,c), rdr.at16h(row_offset+j,c));
      } else if ( vecs.isString(c) ) {
        columns[i].string_data = new String[len];
        BufferedString tmpStr = new BufferedString();
        for (int j = 0; j < len; j++)
          columns[i].string_data[j] = rdr.isNA(row_offset+j,c) ? null : rdr.atStr(tmpStr,row_offset+j,c).toString();

      } else {
        columns[i].data = MemoryManager.malloc8d(len);
        for( int j=0; j<len; j++ )
          columns[i].data[j] = rdr.at(row_offset+j,c);
      }
    }
    this.is_text = f.numCols()==1 && vecs.vecs()[0] instanceof ByteVec;
    this.default_percentiles = Vec.PERCENTILES;
    ChunkSummary cs = FrameUtils.chunkSummary(f);
    this.chunk_summary = new TwoDimTableV3(cs.toTwoDimTableChunkTypes());
    this.distribution_summary = new TwoDimTableV3(cs.toTwoDimTableDistribution());
    this._fr = f;
    return this;
  }



  public void clearBinsField() {
    for (ColV3 col: columns)
      col.clearBinsField();
  }

  private abstract static class ColOp { abstract String op(ColV3 v); }
  private String rollUpStr(ColV3 c, double d) {
    return formatCell(c.domain!=null || "uuid".equals(c.type) || "string".equals(c.type) ? Double.NaN : d,null,c,4);
  }


  private String formatCell( double d, String str, ColV3 c, int precision ) {
    if (Double.isNaN(d)) return "-";
    if (c.domain != null) return c.domain[(int) d];
    if ("uuid".equals(c.type) || "string".equals(c.type)) {
      // UUID and String handling
      if (str == null) return "-";
      return "<b style=\"font-family:monospace;\">" + str + "</b>";
    } else {
      Chunk chk = c._vec.chunkForRow(row_offset).getChunk(0);
      return PrettyPrint.number(chk, d, precision);
    }
  }
}
