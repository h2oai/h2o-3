package water.api;

import water.Iced;

public class JobsV3 extends Schema<Iced,JobsV3> {
  // Input fields
  @API(help="Optional Job key")
  public KeyV3.JobKeyV3 key;

  // Output fields
  @API(help="jobs", direction=API.Direction.OUTPUT)
  public JobV3[] jobs;
}
