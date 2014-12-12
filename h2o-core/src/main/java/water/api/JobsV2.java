package water.api;

import water.Iced;

public class JobsV2 extends Schema<Iced,JobsV2> {
  // Input fields
  @API(help="Optional Job key")
  public JobV2 key;

  // Output fields
  @API(help="jobs", direction=API.Direction.OUTPUT)
  public JobV2[] jobs;
}
