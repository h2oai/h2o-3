from __future__ import print_function
import sys, os
import numpy as np
import pandas as pd
import h2o

sys.path.insert(1, os.path.join("..", "..", ".."))
from h2o.automl import *
from tests import pyunit_utils as pu


def test_automl_distributions():
    scenarios = [
        dict(response="binomial", distribution="binomial",
             algos=['DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], max_models=12, fail=True),
        dict(response="binomial", distribution="bernoulli",
             algos=['DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], max_models=12),
        dict(response="quasibinomial", distribution="quasibinomial", algos=['GBM', 'GLM', 'StackedEnsemble'],
              max_models=17, fail=True),  # needed to be able to build SE
        dict(response="quasibinomial", distribution="fractionalbinomial", algos=['GLM'], fail=True),
        dict(response="multinomial", distribution="multinomial",
             algos=['DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], max_models=12),
        dict(response="gaussian", distribution="gaussian",
             algos=['DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], max_models=12),
        dict(response="ordinal", distribution="poisson",
             algos=['DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], nrows=400),
        dict(response="gaussian", distribution="gamma",
             algos=['DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], nrows=400),
        dict(response="gaussian", distribution="laplace", algos=['DeepLearning', 'GBM']),
        dict(response="gaussian", distribution=dict(distribution="quantile", quantile_alpha=0.25), algos=['DeepLearning', 'GBM']),
        dict(response="gaussian", distribution=dict(distribution="huber", huber_alpha=.3),
             algos=['DeepLearning', 'GBM'], max_models=12),
        dict(response="gaussian", distribution=dict(distribution="tweedie", tweedie_power=1.5),
             algos=['DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost']),
        dict(response="ordinal_factors", distribution="ordinal", algos=[], fail=True),
        dict(response="gaussian", distribution=dict(distribution="custom", custom_distribution_func="FILLED_LATER_IN_THE_TEST"), algos=["GBM"]),
    ]
    seed = 9803190

    def make_data(size):
        np.random.seed(seed=seed)
        a = np.tile([0., 0.7333], int(size / 2))
        a = a[np.argsort(np.random.uniform(size=size))]
        b = 2. * a ** 0.1 + 3. + 8 * np.random.uniform(size=size)
        d = np.tile([1, 2, 3, 4, 5, 6, 7, 8], int(size / 8))
        c = 3 * d - 2 + np.random.uniform(size=size)
        e = np.tile(["class 0", "class A"], int(size / 2))
        f = np.tile(["class 0", "class A", "class alpha", "class aleph"], int(size / 4))

        df = h2o.H2OFrame(pd.DataFrame(dict(quasibinomial=a, gaussian=b, noise=c, ordinal=d, binomial=e, multinomial=f)))
        df["ordinal_factors"] = df["ordinal"].asfactor()
        return df

    def get_distribution(model_id):
        ap = h2o.get_model(model_id).actual_params
        if "metalearner_params" in ap.keys():
            ap = h2o.get_model(model_id).metalearner().actual_params
        distribution = ap.get("distribution", ap.get("family"))
        if distribution == "binomial":
            distribution = "bernoulli"
        return distribution

    tests = []
    for scenario in scenarios:
        def _(scenario):
            distribution_name = scenario["distribution"]
            if isinstance(distribution_name, dict):
                distribution_name = distribution_name["distribution"]
            def test_scenario():
                expected_dist = distribution_name
                df = make_data(scenario.get("nrows", 264))

                # Hack so we don't remove the custom distribution function
                if expected_dist == "custom":
                    from h2o.utils.distributions import CustomDistributionGaussian
                    custom_dist = h2o.upload_custom_distribution(CustomDistributionGaussian)
                    scenario["distribution"]["custom_distribution_func"] = custom_dist


                aml = H2OAutoML(max_models=scenario.get("max_models", 12), distribution=scenario["distribution"], seed=seed,
                                max_runtime_secs_per_model=1, verbosity=None)
                try:
                    aml.train(y=scenario["response"], training_frame=df)
                except Exception:
                    assert scenario.get('fail', False), "This distribution should not have failed."
                    return
                assert not scenario.get('fail', False), "This distribution should have failed."
                if aml.leaderboard.nrow == 0:
                    algos = []
                else:
                    algos = list(set(get_leaderboard(aml, "algo").as_data_frame()["algo"].unique()))

                for expected in ['DeepLearning', "DRF", 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost']:
                    assert expected in algos, "Expected {expected} but no found.".format(expected=expected)

                for model_id in aml.leaderboard.as_data_frame()["model_id"]:
                    distribution = get_distribution(model_id)

                    assert distribution == expected_dist or \
                    h2o.get_model(model_id).algo not in [a.lower() for a in scenario["algos"]], (
                       "{model}: Expected distribution {s_dist} but {distribution} found!".format(
                            model=model_id,
                            s_dist=expected_dist,
                            distribution=distribution
                        ))
            test_scenario.__name__ = "test_{}_distribution".format(distribution_name)
            return test_scenario
        tests.append(_(scenario))
    return tests


def test_python_api():
    def test_parameterized_distribution_without_param():
        try:
            aml = H2OAutoML(distribution=dict(distribution="huber"))
            aml = H2OAutoML(distribution="tweedie")
            aml = H2OAutoML(distribution="quantile")
        except AssertionError:
            assert False, "should not have failed"

    def test_parameterized_distribution_without_param2():
        try:
            aml = H2OAutoML(distribution="custom")
            assert False, "should have failed"
        except ValueError:
            pass
        try:
            aml = H2OAutoML(distribution=dict(distribution="custom"))
            assert False, "should have failed"
        except ValueError:
            pass

    return [
        test_parameterized_distribution_without_param,
        test_parameterized_distribution_without_param2,
    ]


pu.run_tests(
    test_automl_distributions() +
    test_python_api()
)
