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
        dict(response="binomial", distribution=Distribution.binomial,
             algos=['DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], max_models=12),
        dict(response="quasibinomial", distribution="quasibinomial", algos=['GBM', 'GLM', 'StackedEnsemble'],
              max_models=17, fail=True),  # needed to be able to build SE
        dict(response="quasibinomial", distribution="fractionalbinomial", algos=['GLM'], fail=True),
        dict(response="multinomial", distribution=Distribution.multinomial,
             algos=['DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], max_models=12),
        dict(response="gaussian", distribution=Distribution.gaussian,
             algos=['DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], max_models=12),
        dict(response="ordinal", distribution=Distribution.poisson,
             algos=['DeepLearning', 'DRF', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], nrows=400),
        dict(response="gaussian", distribution=Distribution.gamma,
             algos=['DeepLearning', "DRF", 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'], nrows=400),
        dict(response="gaussian", distribution=Distribution.laplace, algos=['DeepLearning', 'GBM', 'StackedEnsemble']),
        dict(response="gaussian", distribution=Distribution.quantile(0.25), algos=['DeepLearning', 'GBM', 'StackedEnsemble']),
        dict(response="gaussian", distribution=Distribution.huber(.3),
             algos=['DeepLearning', 'GBM', 'StackedEnsemble'], max_models=12),
        dict(response="gaussian", distribution=Distribution.tweedie(1.5),
             algos=['DeepLearning', 'DRF', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost']),
        dict(response="ordinal_factors", distribution=Distribution.ordinal, algos=[], fail=True),
        dict(response="gaussian", distribution="custom", algos=["GBM"]),
        dict(response="gaussian", distribution="custom2", algos=["GBM"]),
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

    failed = False
    for scenario in scenarios:
        try:
            distribution = scenario["distribution"]
            if isinstance(distribution, dict):
                distribution = distribution["distribution"]
            print("\n" + distribution + "\n" + "=" * len(distribution))
            h2o.remove_all()
            df = make_data(scenario.get("nrows", 264))

            # Hack so we don't remove the custom distribution function
            if distribution == "custom":
                from h2o.utils.distributions import CustomDistributionGaussian
                custom_dist = h2o.upload_custom_distribution(CustomDistributionGaussian)
                scenario["distribution"] = Distribution.custom(custom_dist)
            if distribution == "custom2":
                from h2o.utils.distributions import CustomDistributionGaussian
                scenario["distribution"] = Distribution.custom(CustomDistributionGaussian)

            aml = H2OAutoML(max_models=scenario.get("max_models", 12), distribution=scenario["distribution"], seed=seed,
                            max_runtime_secs_per_model=1)
            aml.train(y=scenario["response"], training_frame=df)
            if scenario.get('fail', False):
                failed = True
            if aml.leaderboard.nrow == 0:
                algos = []
            else:
                algos = list(set(get_leaderboard(aml, "algo").as_data_frame()["algo"].unique()))

            for expected in ['DeepLearning', "DRF", 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost']:
                if expected not in algos:
                    failed = True
                    print("Expected {expected} but no found.".format(expected=expected))

            for model_id in aml.leaderboard.as_data_frame()["model_id"]:
                distribution = get_distribution(model_id)
                expected_dist = scenario["distribution"]
                if isinstance(expected_dist, dict):
                    expected_dist = expected_dist["distribution"]
                if distribution != expected_dist and h2o.get_model(model_id).algo in scenario["algos"]:
                    failed = True
                    print("{model}: Expected distribution {s_dist} but {distribution} found!".format(
                        model=model_id,
                        s_dist=expected_dist,
                        distribution=distribution
                    ))
                h2o.remove(model_id)
        except Exception as e:
            if not scenario.get('fail', False):
                raise e
    assert not failed


pu.run_tests([
    test_automl_distributions
])
