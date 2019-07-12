package water.api.schemas3;

import water.Iced;
import water.api.API;

public class RemoveV3 extends RequestSchemaV3<Iced, RemoveV3> {

  @API(help="Object to be removed.")
  public KeyV3 key;

  @API(help="If true, removal operation will cascade down the object tree.", direction = API.Direction.INPUT)
  public boolean cascade;

}
