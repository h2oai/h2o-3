package hex.ensemble;

import hex.Model.Parameters;
import water.api.schemas3.ModelParametersSchemaV3;

public interface MetalearnerProvider<M extends Metalearner, MPS extends ModelParametersSchemaV3> {

    String getName();

    M newInstance();

    MPS newParametersSchemaInstance();
}
