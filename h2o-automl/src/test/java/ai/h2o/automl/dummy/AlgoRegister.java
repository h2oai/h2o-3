package ai.h2o.automl.dummy;

import water.api.AlgoAbstractRegister;
import water.api.RestApiContext;
import water.api.SchemaServer;

public class AlgoRegister extends AlgoAbstractRegister {

    @Override
    public void registerEndPoints(RestApiContext context) {
        registerModelBuilder(context, new DummyBuilder(true), SchemaServer.getStableVersion());
    }
}
