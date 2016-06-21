package water.api.schemas3;

import hex.VarImp;
import water.api.API;

public class VarImpV3 extends SchemaV3<VarImp,VarImpV3> {

  @API(help="Variable importance of individual variables", direction=API.Direction.OUTPUT)
  public float[] varimp;

  @API(help="Names of variables", direction=API.Direction.OUTPUT)
  protected String[] names;

}
