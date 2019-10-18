package ai.h2o.automl;

import ai.h2o.automl.AutoMLBuildSpec.AutoMLCustomParameters;
import hex.KeyValue;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import hex.tree.xgboost.XGBoostModel;
import org.junit.Test;
import water.exceptions.H2OIllegalValueException;

import java.util.Arrays;

public class AutoMLBuildSpecTest {

    @Test
    public void expect_custom_param_is_set_to_all_algos_supporting_it_by_default() {
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        AutoMLCustomParameters algoParameters = buildSpec.build_models.algo_parameters;
        assert algoParameters != null;
        for (Algo algo : Algo.values()) assert !algoParameters.hasCustomParams(algo);

        final String paramName = "monotone_constraints";
        final KeyValue[] paramValue = new KeyValue[] {new KeyValue("AGE", 1)};
        algoParameters.add(paramName, paramValue);

        for (Algo algo : Algo.values()) {
            boolean supportsParam = Arrays.asList(Algo.XGBoost, Algo.GBM).contains(algo);
            assert algoParameters.hasCustomParams(algo) ^ !supportsParam;
            assert algoParameters.hasCustomParam(algo, paramName) ^ !supportsParam;
            assert algoParameters.getCustomParameters(algo) != null;
            switch (algo) {
                case XGBoost:
                    assert paramValue == ((XGBoostModel.XGBoostParameters)algoParameters.getCustomParameters(algo))._monotone_constraints;
                    break;
                case GBM:
                    assert paramValue == ((GBMModel.GBMParameters)algoParameters.getCustomParameters(algo))._monotone_constraints;
                    break;
            }
        }
    }


    @Test
    public void expect_custom_param_can_be_set_to_a_specific_algo_only() {
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        AutoMLCustomParameters algoParameters = buildSpec.build_models.algo_parameters;
        assert algoParameters != null;
        for (Algo algo : Algo.values()) assert !algoParameters.hasCustomParams(algo);

        final String paramName = "monotone_constraints";
        final KeyValue[] paramValue = new KeyValue[] {new KeyValue("AGE", 1)};
        algoParameters.add(Algo.GBM, paramName, paramValue);

        for (Algo algo : Algo.values()) {
            boolean assignedAlgo = Algo.GBM == algo;
            assert algoParameters.hasCustomParams(algo) ^ !assignedAlgo;
            assert algoParameters.hasCustomParam(algo, paramName) ^ !assignedAlgo;
            assert algoParameters.getCustomParameters(algo) != null;
            switch (algo) {
                case XGBoost:
                    assert null == ((XGBoostModel.XGBoostParameters)algoParameters.getCustomParameters(algo))._monotone_constraints;
                    break;
                case GBM:
                    assert paramValue == ((GBMModel.GBMParameters)algoParameters.getCustomParameters(algo))._monotone_constraints;
                    break;
            }
        }
    }

    @Test
    public void expect_multiple_params_can_be_chained() {
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        AutoMLCustomParameters algoParameters = buildSpec.build_models.algo_parameters;

        KeyValue[] monotone_constraints = new KeyValue[] {new KeyValue("AGE", 1)};
        int ntrees = 100;
        algoParameters.add("monotone_constraints", monotone_constraints)
                      .add("ntrees", ntrees);

        for (Algo algo : Algo.values()) {
            boolean supportsNtrees = Arrays.asList(Algo.DRF, Algo.GBM, Algo.XGBoost).contains(algo);
            assert algoParameters.hasCustomParam(algo, "ntrees") ^ !supportsNtrees;
            switch (algo) {
                case DRF:
                    assert Arrays.equals(algoParameters.getCustomParameterNames(algo), new String[]{"ntrees"});
                    assert ntrees == ((DRFModel.DRFParameters)algoParameters.getCustomParameters(algo))._ntrees;
                    break;
                case GBM:
                    assert Arrays.equals(algoParameters.getCustomParameterNames(algo), new String[]{"monotone_constraints", "ntrees"});
                    assert ntrees == ((GBMModel.GBMParameters)algoParameters.getCustomParameters(algo))._ntrees;
                    assert monotone_constraints == ((GBMModel.GBMParameters)algoParameters.getCustomParameters(algo))._monotone_constraints;
                    break;
                case XGBoost:
                    assert Arrays.equals(algoParameters.getCustomParameterNames(algo), new String[]{"monotone_constraints", "ntrees"});
                    assert ntrees == ((XGBoostModel.XGBoostParameters)algoParameters.getCustomParameters(algo))._ntrees;
                    assert monotone_constraints == ((XGBoostModel.XGBoostParameters)algoParameters.getCustomParameters(algo))._monotone_constraints;
                    break;
                default:
                    assert algoParameters.getCustomParameterNames(algo) == null;
            }
        }
    }

    @Test(expected = H2OIllegalValueException.class)
    public void expect_exception_when_setting_a_wrong_value_to_a_custom_param() {
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        AutoMLCustomParameters algoParameters = buildSpec.build_models.algo_parameters;

        final String paramName = "monotone_constraints";
        final String paramValue = "wrong";
        algoParameters.add(Algo.GBM, paramName, paramValue);
    }


    @Test(expected = H2OIllegalValueException.class)
    public void expect_exception_when_setting_a_custom_param_that_is_not_allowed() {
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        AutoMLCustomParameters algoParameters = buildSpec.build_models.algo_parameters;

        final String paramName = "auto_rebalance";
        final boolean paramValue = false;
        algoParameters.add(paramName, paramValue);
    }

    @Test
    public void expect_applyCustomParameters_overrides_dest_parameters_with_only_custom_ones() {
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        AutoMLCustomParameters algoParameters = buildSpec.build_models.algo_parameters;

        KeyValue[] monotone_constraints = new KeyValue[] {new KeyValue("AGE", 1)};
        int ntrees = 100;
        algoParameters.add("monotone_constraints", monotone_constraints)
                .add("ntrees", ntrees);

        GBMModel.GBMParameters customParameters = (GBMModel.GBMParameters) algoParameters.getCustomParameters(Algo.GBM);
        assert customParameters._monotone_constraints == monotone_constraints;
        assert customParameters._ntrees == ntrees;
        assert customParameters._seed == -1; //default

        GBMModel.GBMParameters destParameters = new GBMModel.GBMParameters();
        destParameters._monotone_constraints = new KeyValue[0];
        destParameters._ntrees = 6666;
        destParameters._seed = 12345;

        algoParameters.applyCustomParameters(Algo.GBM, destParameters);
        assert destParameters._monotone_constraints == monotone_constraints;
        assert destParameters._ntrees == ntrees;
        assert destParameters._seed == 12345;
    }
}
