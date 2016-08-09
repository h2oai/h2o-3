package water.api.schemas3;

import water.Futures;
import water.Key;
import water.fvec.VecAry;
import water.api.*;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.fvec.*;
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

  public static class ColSpecifierV3 extends SchemaV3<Frame.VecSpecifier, ColSpecifierV3> {
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

    ColV3(String name, RollupStats rs, double [] dvals, String [] sdata, String [] domain, long off, int len) {
      label=name;
      missing_count = rs.naCnt();
      zero_count = rs.rowCnt() - rs.nzCnt();
      positive_infinity_count = rs.posInfCnt();
      negative_infinity_count = rs.negInfCnt();
      mins = rs.mins();
      maxs = rs.maxs();
      mean = rs.mean();
      sigma = rs.sigma();
      // Histogram data is only computed on-demand.  By default here we do NOT
      // compute it, but will return any prior computed & cached histogram.
      histogram_bins  = rs.lazy_bins();
      histogram_base  = histogram_bins ==null ? 0 : rs.h_base();
      histogram_stride= histogram_bins ==null ? 0 : rs.h_stride();
      percentiles     = histogram_bins ==null ? null : rs.pctiles();
      type  = rs.typeStr(); // vec.isCategorical() ? "enum" : vec.isUUID() ? "uuid" : vec.isString() ? "string" : (vec.isInt() ? (vec.isTime() ? "time" : "int") : "real");
      this.domain = domain;
      domain_cardinality = domain == null?0:domain.length;
      data = dvals;
      string_data = sdata;
    }

    public void clearBinsField() {
      this.histogram_bins = null;
    }
  }

  public FrameV3() { super(); }

  /* Key-only constructor, for the times we only want to return the key. */
  public FrameV3(Key<Frame> frame_id) { this.frame_id = new FrameKeyV3(frame_id); }

  public FrameV3(Frame fr) {
    this(fr, 1, (int) fr.numRows(), 0, 0); // NOTE: possible row len truncation
  }

  public FrameV3(Frame f, long row_offset, int row_count) {
    this(f, row_offset, row_count, 0, 0);
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
    this.byte_size = f.vecs().byteSize();

    this.row_offset = row_offset;
    this.rows = f.numRows();
    this.num_columns = f.numCols();
    this.row_count = row_count;

    this.total_column_count = f.numCols();
    this.column_offset = column_offset;
    this.column_count = column_count;

    this.columns = new ColV3[column_count];
    VecAry vecs = f.vecs();
    VecAry.Reader vReader = vecs.reader(true);
    Futures fs = new Futures();
    double[][] dvals = new double[column_count][];
    String[][] svals = new String[column_count][];
    byte [] types = f.types();
    for(int i =0 ; i < types.length; ++i) {
      if(types[i] == Vec.T_UUID || types[i] == Vec.T_STR)
        svals[i] = new String[row_count];
      else
        dvals[i] = new double[row_count];
    }
    BufferedString tmp = new BufferedString();
    for(int i = 0; i < row_count; i++) {
      for (int j = 0; j < column_count; j++) {
        if(types[j] == Vec.T_UUID || types[j] == Vec.T_STR)
          svals[j][i] = vReader.atStr(tmp,row_offset+i,j).toString();
        else
          dvals[j][i] = vReader.at(row_offset+i,j);
      }
    }
    for(int i =0 ; i < types.length; ++i)
      columns[i] = new ColV3(f.name(column_offset + i), vecs.getRollups(i), dvals[i], svals[i], vecs.domain(i), this.row_offset, this.row_count);
    fs.blockForPending();
    this.is_text = vecs.isRawBytes();
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
}
