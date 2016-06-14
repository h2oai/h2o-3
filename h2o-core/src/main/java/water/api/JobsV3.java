package water.api;

import water.Iced;

public class JobsV3 extends SchemaV3<Iced,JobsV3> {
  // Input fields
  @API(help="Optional Job identifier")
  public KeyV3.JobKeyV3 job_id;

  // Output fields
  @API(help="jobs", direction=API.Direction.OUTPUT)
  public JobV3[] jobs;
}
