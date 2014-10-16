package water.api;

import water.fvec.Frame;
import water.fvec.Vec;
import water.api.QuantilesHandler.*;

public class QuantilesV1 extends Schema<Quantiles,QuantilesV1> {

  // IN
  @API(help="An existing H2O Frame key.")                                                      public Frame source_key;
  @API(help="Column to calculate quantile for")                                                public String column;      // was a VecSelect in H2O1
  @API(help = "Quantile desired (0.0-1.0). Median is 0.5. 0 and 1 are min/max")                public double quantile = 0.5;
  @API(help = "Number of bins used (1-1000000). 1000 recommended")                             public int max_qbins = 1000;
  @API(help = "1: Exact result (iterate max 16). 0: One pass approx. 2: Provide both results") public int multiple_pass  = 1;
  @API(help = "Interpolation between rows. Type 2 (mean) or 7 (linear).")                      public int interpolation_type = 7;
  @API(help = "Maximum number of columns to show quantile")                                    public int max_ncols = 1000;

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

  protected void init() throws IllegalArgumentException {
    if (column.equals("") || column == null) throw new IllegalArgumentException("Column is missing.");
    Vec _column = source_key.vecs()[source_key.find(column)];
    if (source_key == null) throw new IllegalArgumentException("Source key is missing");
    if (_column == null) throw new IllegalArgumentException("Column is missing");
    if (_column.isEnum()) throw new IllegalArgumentException("Column is an enum");
    if (!((interpolation_type == 2) || (interpolation_type == 7))) {
      throw new IllegalArgumentException("Unsupported interpolation type. Currently only allow 2 or 7");
    }

  }

  @Override public Quantiles createImpl() {
    init();
    return new Quantiles(source_key.vecs()[source_key.find(column)], source_key, quantile, max_qbins, multiple_pass,
                         interpolation_type, max_ncols, column_name, quantile_requested, interpolation_type_used,
                         interpolated, iterations, result, result_single);
  }
  @Override public QuantilesV1 fillFromImpl(Quantiles q) {
    source_key = q.source_key;
    column = q.source_key.names()[q.source_key.find(q.column)];
    quantile = q.quantile;
    max_qbins = q.max_qbins;
    multiple_pass = q.multiple_pass;
    interpolation_type = q.interpolation_type;
    max_ncols = q.max_ncols;
    column_name = q.column_name;
    quantile_requested = q.quantile_requested;
    interpolation_type_used = q.interpolation_type_used;
    interpolated = q.interpolated;
    iterations = q.iterations;
    result = q.result;
    result_single = q.result_single;
    return this;
  }
}
