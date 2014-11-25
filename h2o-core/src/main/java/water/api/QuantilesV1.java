package water.api;

import water.Quantiles;
import water.fvec.Frame;
import water.fvec.Vec;

public class QuantilesV1 extends Schema<Quantiles,QuantilesV1> {

  // IN
  @API(help="An existing H2O Frame key.")                                                      public Frame source_key;
  @API(help="Column to calculate quantile for")                                                public String column;      // was a VecSelect in H2O1
  @API(help = "Quantile desired (0.0-1.0). Median is 0.5. 0 and 1 are min/max")                public double quantile;
  @API(help = "Number of bins used (1-1000000). 1000 recommended")                             public int max_qbins;
  @API(help = "1: Exact result (iterate max 16). 0: One pass approx. 2: Provide both results") public int multiple_pass;
  @API(help = "Interpolation between rows. Type 2 (mean) or 7 (linear).")                      public int interpolation_type;
  @API(help = "Maximum number of columns to show quantile")                                    public int max_ncols;

  // this isn't used yet. column_name is
  // class colsFilter1 extends MultiVecSelect { public colsFilter1() { super("source_key");} }
  // @API(help = "Not supported yet (Select columns)", filter=colsFilter1.class)
  // int[] cols;

  // OUT
  @API(help = "Column name.", direction=API.Direction.OUTPUT)                                                                  String column_name;
  @API(help = "Quantile requested.", direction=API.Direction.OUTPUT)                                                           double quantile_requested;
  @API(help = "Interpolation type used.", direction=API.Direction.OUTPUT)                                                      int interpolation_type_used;
  @API(help = "False if an exact result is provided, True if the answer is interpolated.", direction=API.Direction.OUTPUT)     boolean interpolated;
  @API(help = "Number of iterations actually performed.", direction=API.Direction.OUTPUT)                                      int iterations;
  @API(help = "Result.", direction=API.Direction.OUTPUT)                                                                       public double result;
  @API(help = "Single pass Result.", direction=API.Direction.OUTPUT)                                                           double result_single;

  // TODO: MOVE TO Quantile.init()!
  protected void sanityCheck() throws IllegalArgumentException {
    if (column.equals("") || column == null) throw new IllegalArgumentException("Column is missing.");
    Vec _column = source_key.vecs()[source_key.find(column)];
    if (source_key == null) throw new IllegalArgumentException("Source key is missing");
    if (_column == null) throw new IllegalArgumentException("Column is missing");
    if (_column.isEnum()) throw new IllegalArgumentException("Column is an enum");
    if (!((interpolation_type == 2) || (interpolation_type == 7))) {
      throw new IllegalArgumentException("Unsupported interpolation type. Currently only allow 2 or 7");
    }
  }

  @Override public Quantiles fillImpl(Quantiles q) {
    sanityCheck();
    q.setAllFields(source_key.vecs()[source_key.find(column)], source_key, quantile, max_qbins, multiple_pass,
                         interpolation_type, max_ncols, column_name, quantile_requested, interpolation_type_used,
                         interpolated, iterations, result, result_single);
    return q;
  }

  @Override public QuantilesV1 fillFromImpl(Quantiles q) {
    sanityCheck();
    source_key = q._source_key;
    column = q._source_key.names()[q._source_key.find(q._column)];
    quantile = q._quantile;
    max_qbins = q._max_qbins;
    multiple_pass = q._multiple_pass;
    interpolation_type = q._interpolation_type;
    max_ncols = q._max_ncols;
    column_name = q._column_name;
    quantile_requested = q._quantile_requested;
    interpolation_type_used = q._interpolation_type_used;
    interpolated = q._interpolated;
    iterations = q._iterations;
    result = q._result;
    result_single = q._result_single;
    return this;
  }
}
