package water.automl.api.schemas3;

import ai.h2o.automl.Leaderboard;
import water.api.API;
import water.api.Schema;
import water.api.schemas3.KeyV3;

public class LeaderboardV99 extends Schema<Leaderboard, LeaderboardV99> {
  /**
   * Identifier for models that should be grouped together in the leaderboard
   * (e.g., "airlines" and "iris").
   */
  @API(help="Identifier for models that should be grouped together in the leaderboard", direction=API.Direction.INOUT)
  public final String project = "<default>";

  /**
   * List of models for this leaderboard, sorted by metric so that the best is first
   * according to the standard metric for the given model type.
   */
  @API(help="List of models for this leaderboard, sorted by metric so that the best is first", direction=API.Direction.OUTPUT)
  public KeyV3.ModelKeyV3[] models;

  /**
   * Metric used to sort this leaderboard.
   */
  @API(help="Metric used to sort this leaderboard", direction=API.Direction.INOUT)
  public String metric;

  /**
   * Metric direction used in the sort.
   */
  @API(help="Metric direction used in the sort", direction=API.Direction.INOUT)
  public boolean sort_decreasing;
}

