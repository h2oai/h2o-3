package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import hex.Model;
import water.util.Log;


/**
 * This class is decoupled from the XGBoostSteps implementation to avoid having to load XGBoost classes
 * when the extension is not available.
 */
public class XGBoostStepsProvider implements ModelingStepsProvider<XGBoostSteps>, ModelParametersProvider<Model.Parameters> {

    @Override
    public String getName() {
        return Algo.XGBoost.name();
    }

    @Override
    public XGBoostSteps newInstance(AutoML aml) {
        return Algo.XGBoost.enabled() ? new XGBoostSteps(aml) : null;
    }

    @Override
    public Model.Parameters newDefaultParameters() {
        if (Algo.XGBoost.enabled()) {
            try {
                return (Model.Parameters)Class.forName("hex.tree.xgboost.XGBoostModel.XGBoostParameters").getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw Log.throwErr(e);
            }
        }
        return null;
    }
}

