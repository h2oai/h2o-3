package ai.h2o.automl;

import water.Iced;
import water.Key;
import water.api.schemas3.JobV3;
import water.fvec.Frame;

/**
 * Parameters which specify the build (or extension) of an AutoML build job.
 */
public class AutoMLBuildSpec extends Iced {
  public String path;
  public String files[];
  public String destination_frames[];
  public String fails[];
  public String dels[];
  public String dataset;
  public String[] datasets_to_join;


  public Key<Frame> training_frame;
  public Key<Frame> validation_frame;

  public String response_column;
  public String[] ignored_columns;

  public String loss = "MSE";
  public long max_time = 3600;
  public boolean ensemble=false;
  public AutoML.algo[] exclude;
  public boolean try_mutations = false;
  public JobV3 job;
}
