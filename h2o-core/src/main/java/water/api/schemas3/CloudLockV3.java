package water.api.schemas3;

import water.Iced;
import water.api.API;

public class CloudLockV3 extends RequestSchemaV3<Iced, CloudLockV3> {
  public CloudLockV3() {
  }

  @API(help="reason", direction=API.Direction.INPUT)
  public String reason;
}
