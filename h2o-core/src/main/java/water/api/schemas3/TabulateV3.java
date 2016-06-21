package water.api.schemas3;

import water.api.API;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.util.Tabulate;

public class TabulateV3 extends SchemaV3<Tabulate, TabulateV3> {
  // INPUT
  @API(help="Dataset", required = true)
  public FrameKeyV3 dataset;

  @API(help="Predictor", required = true, level = API.Level.critical, is_member_of_frames = {"dataset"}, is_mutually_exclusive_with = {"col_y"}, direction = API.Direction.INOUT)
  public FrameV3.ColSpecifierV3 predictor;

  @API(help="Response", required = true, level = API.Level.critical, is_member_of_frames = {"dataset"}, is_mutually_exclusive_with = {"col_x"}, direction = API.Direction.INOUT)
  public FrameV3.ColSpecifierV3 response;

  @API(help="Observation weights (optional)", required = false, level = API.Level.critical, is_member_of_frames = {"dataset"}, is_mutually_exclusive_with = {"col_x"}, direction = API.Direction.INOUT)
  public FrameV3.ColSpecifierV3 weight;

  @API(help="Number of bins for predictor column")
  public int nbins_predictor;

  @API(help="Number of bins for response column")
  public int nbins_response;

  // OUTPUT
  @API(help="Counts table", direction = API.Direction.OUTPUT)
  public TwoDimTableV3 count_table;

  @API(help="Response table", direction = API.Direction.OUTPUT)
  public TwoDimTableV3 response_table;

  public TabulateV3() {}
  public TabulateV3(Tabulate impl) { super(impl); }

  @Override
  public TabulateV3 fillFromImpl(Tabulate impl) {
    super.fillFromImpl(impl);
    return this;
  }

  @Override
  public Tabulate createImpl() {
    return new Tabulate();
  }
}
