package hex.ensemble;

import hex.genmodel.utils.DistributionFamily;
import hex.schemas.XGBoostV3;

public class XGBoostMetalearnerProvider implements MetalearnerProvider<XGBoostMetalearnerProvider.XGBoostMetalearner> {

    static class XGBoostMetalearner extends Metalearners.MetalearnerWithDistribution {
        public XGBoostMetalearner() {
            super(Algorithm.xgboost.name());
        }
    }

    @Override
    public String getName() {
        return Metalearner.Algorithm.xgboost.name();
    }

    @Override
    public XGBoostMetalearner newInstance() {
        return new XGBoostMetalearner();
    }

    @Override
    public XGBoostV3.XGBoostParametersV3 newParametersSchemaInstance() {
        return new XGBoostV3.XGBoostParametersV3();
    }
}
