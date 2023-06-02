package water.automl.api.schemas3;

import hex.leaderboard.Leaderboard;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;
import water.api.schemas3.TwoDimTableV3;

import java.util.stream.Stream;

public class LeaderboardV99 extends SchemaV3<Leaderboard, LeaderboardV99> {
  /**
   * Identifier for models that should be grouped together in the leaderboard
   * (e.g., "airlines" and "iris").
   */
  @API(help="Identifier for models that should be grouped together in the leaderboard", direction=API.Direction.INOUT)
  public final String project_name = "<default>";

  /**
   * List of models for this leaderboard, sorted by metric so that the best is first
   * according to the standard metric for the given model type.
   */
  @API(help="List of models for this leaderboard, sorted by metric so that the best is first", direction=API.Direction.OUTPUT)
  public KeyV3.ModelKeyV3[] models;

  /**
   * Frame for which the metrics have been computed for this leaderboard.
   */
  @API(help="Frame for this leaderboard", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 leaderboard_frame;

  /**
   * Checksum for the Frame for which the metrics have been computed for this leaderboard.
   */
  @API(help="Checksum for the Frame for this leaderboard", direction=API.Direction.OUTPUT)
  public long leaderboard_frame_checksum;

  /**
   * Sort metrics for the models in this leaderboard, in the same order as the models.
   */
  @API(help="Sort metrics for the models in this leaderboard, in the same order as the models", direction=API.Direction.OUTPUT)
  public double[] sort_metrics;

  /**
   * Metric used to sort this leaderboard.
   */
  @API(help="Metric used to sort this leaderboard", direction=API.Direction.INOUT)
  public String sort_metric;

  /**
   * Metric direction used in the sort.
   */
  @API(help="Metric direction used in the sort", direction=API.Direction.INOUT)
  public boolean sort_decreasing;


  @API(help="A table representation of this leaderboard, for easy rendering",  direction=API.Direction.OUTPUT)
  public TwoDimTableV3 table;

  @Override
  public LeaderboardV99 fillFromImpl(Leaderboard leaderboard) {
    super.fillFromImpl(leaderboard, new String[] { "models", "leaderboard_frame", "sort_metrics", "sort_decreasing" });
    models = Stream.of(leaderboard.getModelKeys())
            .map(KeyV3.ModelKeyV3::new)
            .toArray(KeyV3.ModelKeyV3[]::new);

    if (leaderboard.leaderboardFrame() != null)
      leaderboard_frame = new KeyV3.FrameKeyV3(leaderboard.leaderboardFrame()._key);

    sort_metrics = leaderboard.getSortMetricValues();
    sort_decreasing = !Leaderboard.isLossFunction(sort_metric);
    table = new TwoDimTableV3().fillFromImpl(leaderboard.toTwoDimTable());
    return this;
  }

}

