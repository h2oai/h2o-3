package water.api.schemas3;

import water.Iced;
import water.api.API;

public class JobsV3 extends SchemaV3<Iced,JobsV3> {
  // Input fields
  @API(help="Optional Job identifier")
  public KeyV3.JobKeyV3 job_id;

  // Output fields
  @API(help="jobs", direction=API.Direction.OUTPUT)
  public JobV3[] jobs;
}
