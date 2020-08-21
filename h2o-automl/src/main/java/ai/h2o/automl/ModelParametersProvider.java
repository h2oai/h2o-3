package ai.h2o.automl;

import hex.Model.Parameters;

public interface ModelParametersProvider<P extends Parameters> {

    P newDefaultParameters();

}
