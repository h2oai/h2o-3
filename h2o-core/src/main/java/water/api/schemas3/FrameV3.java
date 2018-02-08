package water.api.schemas3;

import water.DKV;
import water.Futures;
import water.Key;
import water.MemoryManager;
import water.api.API;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.fvec.ByteVec;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Frame.VecSpecifier;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.ChunkSummary;
import water.util.FrameUtils;
import water.util.Log;
import water.util.PrettyPrint;

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

  @API(help="Number of full columns to return. The columns between full_column_count and column_count will be returned without the data", direction=API.Direction.INOUT)
  public int full_column_count;

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

    transient Vec _vec;

    ColV3(String name, Vec vec, long off, int len, boolean is_full_column) {
      label = name;

      missing_count = vec.naCnt();
      zero_count = vec.length() - vec.nzCnt() - missing_count;
      positive_infinity_count = vec.pinfs();
      negative_infinity_count = vec.ninfs();
      mins = vec.mins();
      maxs = vec.maxs();
      mean = vec.mean();
      sigma = vec.sigma();
      // Histogram data is only computed on-demand.  By default here we do NOT
      // compute it, but will return any prior computed & cached histogram.
      histogram_bins = vec.lazy_bins();
      histogram_base = histogram_bins == null ? 0 : vec.base();
      histogram_stride = histogram_bins == null ? 0 : vec.stride();
      percentiles = histogram_bins == null ? null : vec.pctiles();

      type = vec.isUUID() ? "uuid" :
              vec.isString() ? "string" :
                      vec.isCategorical() ? "enum" :
                              vec.isTime() ? "time" :
                                      vec.isInt() ? "int" : "real";
      domain = vec.domain();
      if (vec.isCategorical()) {
        domain_cardinality = domain.length;
      } else {
        domain_cardinality = 0;
      }

      if (is_full_column) {
        len = (int) Math.min(len, vec.length() - off);
        if (vec.isUUID()) {
          string_data = new String[len];
          for (int i = 0; i < len; i++)
            string_data[i] = vec.isNA(off + i) ? null : PrettyPrint.UUID(vec.at16l(off + i), vec.at16h(off + i));
          data = null;
        } else if (vec.isString()) {
          string_data = new String[len];
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < len; i++)
            string_data[i] = vec.isNA(off + i) ? null : vec.atStr(tmpStr, off + i).toString();
          data = null;
        } else {
          data = MemoryManager.malloc8d(len);
          for (int i = 0; i < len; i++)
            data[i] = vec.at(off + i);
          string_data = null;
        }
        _vec = vec;               // Better HTML display, not in the JSON
      }
      if (len > 0)  // len == 0 is presumed to be a header file
        precision = vec.chunkForRow(0).precision();

    }

    ColV3(String name, Vec vec, long off, int len) {
      this(name, vec, off, len, true);
    }

    public void clearBinsField() {
      this.histogram_bins = null;
    }
  }

  public FrameV3() { super(); }

  /* Key-only constructor, for the times we only want to return the key. */
  public FrameV3(Key<Frame> frame_id) { this.frame_id = new FrameKeyV3(frame_id); }

  public FrameV3(Frame fr) {
    this(fr, 1, (int) fr.numRows(), 0, -1, -1, true); // NOTE: possible row len truncation
  }

  public FrameV3(Frame f, long row_offset, int row_count) {
    this(f, row_offset, row_count, 0, -1, -1, true);
  }

  public FrameV3(Frame f, long row_offset, int row_count, int column_offset, int column_count) {
    this.fillFromImpl(f, row_offset, row_count, column_offset, column_count, -1, true);
  }

  public FrameV3(Frame f, long row_offset, int row_count, int column_offset, int column_count, int full_column_count, boolean expensive) {
    this.fillFromImpl(f, row_offset, row_count, column_offset, column_count, full_column_count, expensive);
  }

  @Override public FrameV3 fillFromImpl(Frame f) {
    return fillFromImpl(f, 1, (int)f.numRows(), 0, -1, -1, false);
  }

  /**
   *
   * @param f frame from which to obtain the data
   * @param row_offset starting position for rows
   * @param row_count number or rows to obtain
   * @param column_offset starting position for columns
   * @param column_count number of columns to obtain
   * @param full_column_count number of columns starting at column_offset which will be obtained together with data.
   *                          The columns on positions between column_offset+full_column_count and column_offset+column_count
   *                          won't be backed by vectors so we won't transfer the data to the client side. By default, all columns
   *                          we ask for are full columns.
   * @param expensive whether to execute long-lasting but not exactly strictly required computations
   * @return desired frame slice
   */
  private FrameV3 fillFromImpl(Frame f, long row_offset, int row_count,
                              int column_offset, int column_count, int full_column_count,
                              boolean expensive) {
    if( row_count < 0 ) row_count = 100;                                 // 100 rows by default
    if( column_count < 0 ) column_count = f.numCols() - column_offset; // full width by default
    if( full_column_count < 0 || full_column_count > column_count) full_column_count=column_count; // light_column_count can't be larger then number of columns we asked for

    row_count    = (int) Math.min(row_count, row_offset + f.numRows());
    column_count = Math.min(column_count, column_offset + f.numCols());

    this.frame_id = new FrameKeyV3(f._key);
    if (expensive) {
      this.checksum = f.checksum();
      this.byte_size = f.byteSize();
    }

    this.row_offset = row_offset;
    this.rows = f.numRows();
    this.num_columns = f.numCols();
    this.row_count = row_count;

    this.total_column_count = f.numCols();
    this.column_offset = column_offset;
    this.column_count = column_count;
    this.full_column_count = full_column_count;
    this.columns = new ColV3[column_count];
    Vec[] vecs = f.vecs();
    Futures fs = new Futures();
    // Compute rollups in parallel as needed, by starting all of them and using
    // them when filling in the ColV3 Schemas.
    // NOTE: SKIP deleted Vecs!  The columns entry will be null for deleted Vecs.
    for( int i = 0; i < column_count; i++ )
      if (null == DKV.get(vecs[column_offset + i]._key))
        Log.warn("For Frame: " + f._key + ", Vec number: " + (column_offset + i) + " (" + f.name((column_offset + i))+ ") is missing; not returning it.");
      else
        vecs[column_offset + i].startRollupStats(fs);


    for( int i = 0; i < column_count; i++ )
      if (null == DKV.get(vecs[column_offset + i]._key))
        Log.warn("For Frame: " + f._key + ", Vec number: " + (column_offset + i) + " (" + f.name((column_offset + i))+ ") is missing; not returning it.");
      else
        columns[i] = new ColV3(f._names[column_offset + i], vecs[column_offset + i], this.row_offset, this.row_count, i < full_column_count);

    fs.blockForPending();
    this.is_text = f.numCols()==1 && vecs[0] instanceof ByteVec;
    this.default_percentiles = Vec.PERCENTILES;

    if (expensive) {
      ChunkSummary cs = FrameUtils.chunkSummary(f);

      this.chunk_summary = new TwoDimTableV3(cs.toTwoDimTableChunkTypes());
      this.distribution_summary = new TwoDimTableV3(cs.toTwoDimTableDistribution());
    }

    this._fr = f;

    return this;
  }



  public void clearBinsField() {
    for (ColV3 col: columns)
      if (col != null)
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
      Chunk chk = c._vec.chunkForRow(row_offset);
      return PrettyPrint.number(chk, d, precision);
    }
  }
}
