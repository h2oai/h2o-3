package hex.ensemble;

import water.api.Schema;

public interface MetalearnerProvider<M extends Metalearner> {

    String getName();

    M newInstance();

    Schema newParametersSchemaInstance();
}
