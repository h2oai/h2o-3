package ai.h2o.automl;

import ai.h2o.automl.AutoMLBuildSpec.AutoMLCustomParameters;
import hex.KeyValue;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import hex.tree.xgboost.XGBoostModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import water.Key;
import water.exceptions.H2OIllegalValueException;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

import java.util.Arrays;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class AutoMLBuildSpecTest {

    @Rule
    public ExpectedException thrown= ExpectedException.none();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    private void enableAnyCustomParam() {
        System.setProperty(AutoMLCustomParameters.ALGO_PARAMS_ALL_ENABLED, "true");
    }

    @Test
    public void expect_algo_parameters_is_empty_by_default() {
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        AutoMLCustomParameters algoParameters = buildSpec.build_models.algo_parameters;
        assert algoParameters != null;
        for (Algo algo : Algo.values()) assert !algoParameters.hasCustomParams(algo);
    }

    @Test
    public void expect_custom_param_is_set_to_all_algos_supporting_it_by_default() {
        final String paramName = "monotone_constraints";
        final KeyValue[] paramValue = new KeyValue[] {new KeyValue("AGE", 1)};
        AutoMLCustomParameters algoParameters = AutoMLCustomParameters.create()
                .add(paramName, paramValue)
                .build();

        for (Algo algo : Algo.values()) {
            boolean supportsParam = Arrays.asList(Algo.XGBoost, Algo.GBM).contains(algo);
            assert algoParameters.hasCustomParams(algo) ^ !supportsParam;
            assert algoParameters.hasCustomParam(algo, paramName) ^ !supportsParam;
            assert algoParameters.getCustomizedDefaults(algo) != null;
            switch (algo) {
                case XGBoost:
                    assert paramValue == ((XGBoostModel.XGBoostParameters)algoParameters.getCustomizedDefaults(algo))._monotone_constraints;
                    break;
                case GBM:
                    assert paramValue == ((GBMModel.GBMParameters)algoParameters.getCustomizedDefaults(algo))._monotone_constraints;
                    break;
            }
        }
    }


    @Test
    public void expect_custom_param_can_be_set_to_a_specific_algo_only() {
        final String paramName = "monotone_constraints";
        final KeyValue[] paramValue = new KeyValue[] {new KeyValue("AGE", 1)};
        AutoMLCustomParameters algoParameters = AutoMLCustomParameters.create()
                .add(Algo.GBM, paramName, paramValue)
                .build();

        for (Algo algo : Algo.values()) {
            boolean assignedAlgo = Algo.GBM == algo;
            assert algoParameters.hasCustomParams(algo) ^ !assignedAlgo;
            assert algoParameters.hasCustomParam(algo, paramName) ^ !assignedAlgo;
            assert algoParameters.getCustomizedDefaults(algo) != null;
            switch (algo) {
                case XGBoost:
                    assert null == ((XGBoostModel.XGBoostParameters)algoParameters.getCustomizedDefaults(algo))._monotone_constraints;
                    break;
                case GBM:
                    assert paramValue == ((GBMModel.GBMParameters)algoParameters.getCustomizedDefaults(algo))._monotone_constraints;
                    break;
            }
        }
    }

    @Test
    public void expect_multiple_params_can_be_chained() {
        enableAnyCustomParam();
        KeyValue[] monotone_constraints = new KeyValue[] {new KeyValue("AGE", 1)};
        int ntrees = 111;
        AutoMLCustomParameters algoParameters = AutoMLCustomParameters.create()
                .add("monotone_constraints", monotone_constraints)
                .add("ntrees", ntrees)
                .build();

        for (Algo algo : Algo.values()) {
            boolean supportsNtrees = Arrays.asList(Algo.DRF, Algo.GBM, Algo.XGBoost).contains(algo);
            assert algoParameters.hasCustomParam(algo, "ntrees") ^ !supportsNtrees;
            switch (algo) {
                case DRF:
                    assert Arrays.equals(algoParameters.getCustomParameterNames(algo), new String[]{"ntrees"});
                    assert ntrees == ((DRFModel.DRFParameters)algoParameters.getCustomizedDefaults(algo))._ntrees;
                    break;
                case GBM:
                    assert Arrays.equals(algoParameters.getCustomParameterNames(algo), new String[]{"monotone_constraints", "ntrees"});
                    assert ntrees == ((GBMModel.GBMParameters)algoParameters.getCustomizedDefaults(algo))._ntrees;
                    assert monotone_constraints == ((GBMModel.GBMParameters)algoParameters.getCustomizedDefaults(algo))._monotone_constraints;
                    break;
                case XGBoost:
                    assert Arrays.equals(algoParameters.getCustomParameterNames(algo), new String[]{"monotone_constraints", "ntrees"});
                    assert ntrees == ((XGBoostModel.XGBoostParameters)algoParameters.getCustomizedDefaults(algo))._ntrees;
                    assert monotone_constraints == ((XGBoostModel.XGBoostParameters)algoParameters.getCustomizedDefaults(algo))._monotone_constraints;
                    break;
                default:
                    assert algoParameters.getCustomParameterNames(algo) == null;
            }
        }
    }

    @Test
    public void expect_exception_when_setting_a_wrong_value_to_a_custom_param() {
        thrown.expect(H2OIllegalValueException.class);
        final String paramName = "monotone_constraints";
        final String paramValue = "wrong";
        AutoMLCustomParameters.create().add(Algo.GBM, paramName, paramValue).build();
    }


    @Test
    public void expect_exception_when_setting_a_custom_param_that_is_not_allowed() {
        thrown.expect(H2OIllegalValueException.class);
        final String paramName = "auto_rebalance";
        final boolean paramValue = false;
        AutoMLCustomParameters.create()
                .add(paramName, paramValue)
                .build();
    }

    @Test
    public void expect_applyCustomParameters_overrides_dest_parameters_with_only_custom_ones() {
        enableAnyCustomParam();
        KeyValue[] monotone_constraints = new KeyValue[] {new KeyValue("AGE", 1)};
        int ntrees = 111;
        AutoMLCustomParameters algoParameters = AutoMLCustomParameters.create()
                .add("monotone_constraints", monotone_constraints)
                .add("ntrees", ntrees)
                .build();

        GBMModel.GBMParameters customParameters = (GBMModel.GBMParameters) algoParameters.getCustomizedDefaults(Algo.GBM);
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

    @Test
    public void expect_specific_params_take_precedence_over_global_params() {
        enableAnyCustomParam();
        AutoMLCustomParameters algoParameters = AutoMLCustomParameters.create()
                .add(Algo.GBM, "ntrees", 555)
                .add("ntrees", 111)
                .build();

        GBMModel.GBMParameters destGBMParameters = new GBMModel.GBMParameters();
        algoParameters.applyCustomParameters(Algo.GBM, destGBMParameters);
        assert destGBMParameters._ntrees == 555;

        DRFModel.DRFParameters destDRFParameters = new DRFModel.DRFParameters();
        algoParameters.applyCustomParameters(Algo.DRF, destDRFParameters);
        assert destDRFParameters._ntrees == 111;
    }

    @Test
    public void expect_automl_key_is_sanitized() {
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec() {
            @Override
            public String project() {
                return "test_project";
            }
        };
        buildSpec.input_spec.response_column = "is%this good?";
        Key<AutoML> key = buildSpec.makeKey();
        assertEquals("test_project@@is_this_good_", key.toString());
    }

}
