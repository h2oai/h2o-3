package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import hex.Model;
import hex.ModelBuilder;
import water.util.Log;


/**
 * This class is decoupled from the XGBoostSteps implementation to avoid having to load XGBoost classes
 * when the extension is not available.
 */
public class XGBoostStepsProvider implements ModelingStepsProvider<XGBoostSteps>, ModelParametersProvider<Model.Parameters> {

    @Override
    public String getName() {
        return XGBoostSteps.NAME;
    }

    @Override
    public XGBoostSteps newInstance(AutoML aml) {
        return Algo.XGBoost.enabled() ? new XGBoostSteps(aml) : null;
    }

    @Override
    public Model.Parameters newDefaultParameters() {
        return Algo.XGBoost.enabled() ? ModelBuilder.make(getName(), null, null)._parms : null;
    }
}

