package water.api;

import hex.VarImp;
import hex.VarImp.VarImpMethod;

public class VarImpBase<I extends VarImp, S extends VarImpBase<I, S>> extends Schema<I, S> {
    @API(help="Variable importance of individual variables", direction=API.Direction.OUTPUT)
    public float[] varimp;

    @API(help="Names of variables", direction=API.Direction.OUTPUT)
    protected String[] variables;

    @API(help="Variable importance measurement method", direction=API.Direction.OUTPUT)
    public VarImpMethod method;
}
