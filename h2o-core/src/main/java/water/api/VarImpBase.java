package water.api;

import hex.VarImp;

public class VarImpBase<I extends VarImp, S extends VarImpBase<I, S>> extends SchemaV3<I, S> {
  @API(help="Variable importance of individual variables", direction=API.Direction.OUTPUT)
  public float[] varimp;

  @API(help="Names of variables", direction=API.Direction.OUTPUT)
  protected String[] names;
}
