#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import sys
import warnings

sys.path.insert(1, "../../../")  # allow us to run this standalone

import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils


def infer_distribution_helper(dist, expected_dist, kwargs1={}, kwargs2={}):
    train = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_test.csv"))
    if dist == "multinomial":
        y = "species"
    elif dist == "bernoulli":
        train["response"] = (train["species"] == "Iris-versicolor").asfactor()
        test["response"] = (test["species"] == "Iris-versicolor").asfactor()
        y = "response"
    elif dist == "quasibinomial":
        train["response"] = (train["species"] == "Iris-versicolor")
        test["response"] = (test["species"] == "Iris-versicolor")
        y = "response"
    else:
        y = "petal_wid"

    x = train.columns
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True,
                                       distribution=dist, **kwargs1)
    gbm.train(x=x, y=y, training_frame=train)

    gbm2 = H2OGradientBoostingEstimator(nfolds=nfolds,
                                        fold_assignment="Modulo",
                                        keep_cross_validation_predictions=True,
                                        distribution=dist, **kwargs2)
    gbm2.train(x=x, y=y, training_frame=train)

    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     validation_frame=test,
                                     base_models=[gbm, gbm2],
                                     metalearner_algorithm="gbm")
    se.train(x, y, train)
    assert se.metalearner().actual_params.get("distribution") == expected_dist, \
        "Expected distribution {} but got {}".format(expected_dist, se.metalearner().actual_params.get("distribution"))


def infer_distribution_test():
    from h2o.utils import CustomDistributionGeneric, CustomDistributionGaussian

    class CustomDistributionGaussian2(CustomDistributionGeneric):
        def link(self):
            return "identity"

        def init(self, w, o, y):
            return [w * (y - o), w]

        def gradient(self, y, f):
            return y - f

        def gamma(self, w, y, z, f):
            return [w * z, w]

    custom_dist1 = h2o.upload_custom_distribution(CustomDistributionGaussian)
    custom_dist2 = h2o.upload_custom_distribution(CustomDistributionGaussian2)

    for dist in ["poisson", "laplace", "tweedie", "gaussian", "huber", "gamma",
                 "quantile", "bernoulli", "quasibinomial", "multinomial"]:
        infer_distribution_helper(dist, dist)

    # custom distribution
    infer_distribution_helper("custom", "custom",
                              dict(custom_distribution_func=custom_dist1),
                              dict(custom_distribution_func=custom_dist1))

    # revert to default
    infer_distribution_helper("tweedie", "gaussian", dict(tweedie_power=1.2))
    infer_distribution_helper("huber", "gaussian", dict(huber_alpha=0.2))
    infer_distribution_helper("quantile", "gaussian", dict(quantile_alpha=0.2))
    infer_distribution_helper("custom", "gaussian",
                              dict(custom_distribution_func=custom_dist1),
                              dict(custom_distribution_func=custom_dist2))

    # unaffected by param for different distribution
    infer_distribution_helper("quantile", "quantile", dict(tweedie_power=1.2))
    infer_distribution_helper("tweedie", "tweedie", dict(huber_alpha=0.2))
    infer_distribution_helper("huber", "huber", dict(quantile_alpha=0.2))
    infer_distribution_helper("custom", "custom",
                              dict(custom_distribution_func=custom_dist1),
                              dict(custom_distribution_func=custom_dist1,
                                   tweedie_power=1.2))


def infer_family_helper(family, expected_family, link, expected_link, kwargs1=None, kwargs2=None):
    kwargs1 = dict() if kwargs1 is None else kwargs1
    kwargs2 = dict() if kwargs2 is None else kwargs2
    train = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_test.csv"))
    if family == "multinomial":
        y = "species"
    elif family == "binomial":
        train["response"] = (train["species"] == "Iris-versicolor").asfactor()
        test["response"] = (test["species"] == "Iris-versicolor").asfactor()
        y = "response"
    elif family == "quasibinomial" or family == "fractionalbinomial":
        train["response"] = (train["species"] == "Iris-versicolor") / 2
        test["response"] = (test["species"] == "Iris-versicolor") / 2
        y = "response"
    elif family == "ordinal":
        y = "response"
        train[y] = (train["species"] == "Iris-versicolor")
        test[y] = (test["species"] == "Iris-versicolor")
        train[(train["species"] == "Iris-setosa"), y] = 2
        test[(test["species"] == "Iris-setosa"), y] = 2
        train[y] = train[y].asfactor()
        test[y] = test[y].asfactor()
    else:
        y = "petal_wid"

    x = train.columns
    x.remove(y)

    if "link" not in kwargs1 and link:
        kwargs1["link"] = link
    if "family" not in kwargs1:
        kwargs1["family"] = family

    if "link" not in kwargs2 and link:
        kwargs2["link"] = link
    if "family" not in kwargs2:
        kwargs2["family"] = family

    nfolds = 2
    glm = H2OGeneralizedLinearEstimator(nfolds=nfolds,
                                        fold_assignment="Modulo",
                                        keep_cross_validation_predictions=True,
                                        **kwargs1)
    glm.train(x=x, y=y, training_frame=train)

    glm2 = H2OGeneralizedLinearEstimator(nfolds=nfolds,
                                         fold_assignment="Modulo",
                                         keep_cross_validation_predictions=True,
                                         **kwargs2)
    glm2.train(x=x, y=y, training_frame=train)

    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     validation_frame=test,
                                     base_models=[glm, glm2],
                                     metalearner_algorithm="glm")
    se.train(x, y, train)
    assert se.metalearner().actual_params.get("family") == expected_family, \
        "Expected family {} but got {}".format(expected_family, se.metalearner().actual_params.get("family"))
    if link:
        assert se.metalearner().actual_params.get("link") == expected_link, \
            "Expected link {} but got {}".format(expected_link, se.metalearner().actual_params.get("link"))

    se_auto = H2OStackedEnsembleEstimator(training_frame=train,
                                          validation_frame=test,
                                          base_models=[glm, glm2],
                                          metalearner_algorithm="auto")
    se_auto.train(x, y, train)
    assert se_auto.metalearner().actual_params.get("family") == expected_family, \
        "Expected family {} but got {}".format(expected_family, se_auto.metalearner().actual_params.get("family"))
    if link:
        assert se_auto.metalearner().actual_params.get("link") == expected_link, \
            "Expected link {} but got {}".format(expected_link, se_auto.metalearner().actual_params.get("link"))


def infer_family_test():
    families = dict(
        # family = list of links
        gaussian=["identity", "log", "inverse"],
        binomial=["logit"],
        # fractionalbinomial=["logit"], # FIXME: fractional binomial distribution does not exists
        multinomial=[None],
        # ordinal=["ologit"], # FIXME: ordinal distribution does not exists
        quasibinomial=["logit"],
        poisson=["identity", "log"],
        # negativebinomial=["identity", "log"], # FIXME: negative binomial distribution is not implemented
        gamma=["identity", "log", "inverse"],
        tweedie=["tweedie"]
    )

    for family, links in families.items():
        for link in links:
            infer_family_helper(family, family, link, link)

    # revert to default
    infer_family_helper("gamma", "gaussian", "log", "identity", kwargs2=dict(link="inverse"))
    infer_family_helper("gamma", "gaussian", "log", "identity", kwargs2=dict(family="tweedie", link="tweedie"))


def infer_mixed_family_and_dist_helper(family, expected_family, first_glm, expected_link=None, kwargs_glm=None, kwargs_gbm=None, metalearner_params=None):
    kwargs_glm = dict() if kwargs_glm is None else kwargs_glm
    kwargs_gbm = dict() if kwargs_gbm is None else kwargs_gbm
    metalearner_params = dict() if metalearner_params is None else metalearner_params

    distribution = family if not family == "binomial" else "bernoulli"
    expected_distribution = expected_family if not expected_family == "binomial" else "bernoulli"

    train = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_test.csv"))
    if family == "multinomial":
        y = "species"
    elif family == "binomial":
        train["response"] = (train["species"] == "Iris-versicolor").asfactor()
        test["response"] = (test["species"] == "Iris-versicolor").asfactor()
        y = "response"
    elif family == "quasibinomial" or family == "fractionalbinomial":
        train["response"] = (train["species"] == "Iris-versicolor") / 2
        test["response"] = (test["species"] == "Iris-versicolor") / 2
        y = "response"
    elif family == "ordinal":
        y = "response"
        train[y] = (train["species"] == "Iris-versicolor")
        test[y] = (test["species"] == "Iris-versicolor")
        train[(train["species"] == "Iris-setosa"), y] = 2
        test[(test["species"] == "Iris-setosa"), y] = 2
        train[y] = train[y].asfactor()
        test[y] = test[y].asfactor()
    else:
        y = "petal_wid"

    x = train.columns
    x.remove(y)

    if "family" not in kwargs_glm:
        kwargs_glm["family"] = family

    if "distribution" not in kwargs_gbm:
        kwargs_gbm["distribution"] = distribution

    nfolds = 2
    glm = H2OGeneralizedLinearEstimator(nfolds=nfolds,
                                        fold_assignment="Modulo",
                                        keep_cross_validation_predictions=True,
                                        **kwargs_glm)
    glm.train(x=x, y=y, training_frame=train)

    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True,
                                       **kwargs_gbm)
    gbm.train(x=x, y=y, training_frame=train)

    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     validation_frame=test,
                                     base_models=[glm, gbm] if first_glm else [gbm, glm],
                                     metalearner_algorithm="glm",
                                     metalearner_params={k:v for k, v in metalearner_params.items() if k != "distribution"})
    se.train(x, y, train)
    assert se.metalearner().actual_params.get("family") == expected_family, \
        "Expected family {} but got {}".format(expected_family, se.metalearner().actual_params.get("family"))
    if expected_link:
        assert se.metalearner().actual_params.get("link") == expected_link, \
            "Expected link {} but got {}".format(expected_link, se.metalearner().actual_params.get("link"))

    se_auto = H2OStackedEnsembleEstimator(training_frame=train,
                                          validation_frame=test,
                                          base_models=[glm, gbm] if first_glm else [gbm, glm],
                                          metalearner_algorithm="auto",
                                          metalearner_params={k:v for k, v in metalearner_params.items() if k != "distribution"})
    se_auto.train(x, y, train)
    assert se_auto.metalearner().actual_params.get("family") == expected_family, \
        "Expected family {} but got {}".format(expected_family, se_auto.metalearner().actual_params.get("family"))
    if expected_link:
        assert se_auto.metalearner().actual_params.get("link") == expected_link, \
            "Expected link {} but got {}".format(expected_link, se_auto.metalearner().actual_params.get("link"))
    se_gbm = H2OStackedEnsembleEstimator(training_frame=train,
                                         validation_frame=test,
                                         base_models=[glm, gbm] if first_glm else [gbm, glm],
                                         metalearner_algorithm="gbm",
                                         metalearner_params={k:v for k, v in metalearner_params.items() if k != "family" and k != "link"})
    se_gbm.train(x, y, train)
    assert se_gbm.metalearner().actual_params.get("distribution") == expected_distribution, \
        "Expected distribution {} but got {}".format(expected_distribution,
                                                     se_gbm.metalearner().actual_params.get("distribution"))


def infer_mixed_family_and_dist_test():
    families = dict(
        # family = list of links
        gaussian=["identity", "log", "inverse"],
        binomial=["logit"],
        # fractionalbinomial=["logit"], # FIXME: fractional binomial distribution does not exists
        multinomial=[None],
        # ordinal=["ologit"], # FIXME: ordinal distribution does not exists
        quasibinomial=["logit"],
        poisson=["identity", "log"],
        # negativebinomial=["identity", "log"], # FIXME: negative binomial distribution is not implemented
        gamma=["identity", "log", "inverse"],
        tweedie=["tweedie"]
    )

    for family in families.keys():
        infer_mixed_family_and_dist_helper(family, family, False)
        infer_mixed_family_and_dist_helper(family, family, True)

    # revert to default
    infer_mixed_family_and_dist_helper("gamma", "gaussian", False, kwargs_glm=dict(family="tweedie"))
    infer_mixed_family_and_dist_helper("gamma", "gaussian", True, kwargs_glm=dict(family="tweedie"))
    infer_mixed_family_and_dist_helper("gamma", "gaussian", False, kwargs_gbm=dict(distribution="tweedie"))
    infer_mixed_family_and_dist_helper("gamma", "gaussian", True, kwargs_gbm=dict(distribution="tweedie"))

    # should inherit the link if all GLMs share the same link
    infer_mixed_family_and_dist_helper("gamma", "gamma", True, expected_link="log", kwargs_glm=dict(link="log"))
    # TODO: is this behaviour OK? What to do differently? (we are looking on first GLM base model not just first base model for link inference)
    infer_mixed_family_and_dist_helper("gamma", "gamma", False, expected_link="log", kwargs_glm=dict(link="log"))

    # should not change when we specify the default link
    infer_mixed_family_and_dist_helper("tweedie", "tweedie", False, kwargs_glm=dict(link="tweedie"))
    infer_mixed_family_and_dist_helper("tweedie", "tweedie", True, kwargs_glm=dict(link="tweedie"))


def metalearner_obeys_metalearner_params_test():
    metalearner_params = dict(distribution="poisson", family="poisson")
    for family in ["gaussian", "tweedie"]:
        infer_mixed_family_and_dist_helper(family, "poisson", False, metalearner_params=metalearner_params)
        infer_mixed_family_and_dist_helper(family, "poisson", True, metalearner_params=metalearner_params)

    # without metalearner_params it would revert to default
    infer_mixed_family_and_dist_helper("gamma", "poisson", False, kwargs_glm=dict(family="tweedie"), metalearner_params=metalearner_params)
    infer_mixed_family_and_dist_helper("gamma", "poisson", True, kwargs_glm=dict(family="tweedie"), metalearner_params=metalearner_params)
    infer_mixed_family_and_dist_helper("gamma", "poisson", False, kwargs_gbm=dict(distribution="tweedie"), metalearner_params=metalearner_params)
    infer_mixed_family_and_dist_helper("gamma", "poisson", True, kwargs_gbm=dict(distribution="tweedie"), metalearner_params=metalearner_params)

    # without metalerarner_params could inherit the link if all GLMs share the same link
    infer_mixed_family_and_dist_helper("gamma", "gamma", False, expected_link="identity", kwargs_glm=dict(link="log"), metalearner_params=dict(family="gamma", link="identity", distribution="gamma"))
    infer_mixed_family_and_dist_helper("gamma", "gamma", True, expected_link="identity", kwargs_glm=dict(link="log"), metalearner_params=dict(family="gamma", link="identity", distribution="gamma"))

    # don't propagate link from different family
    infer_mixed_family_and_dist_helper("gamma", "gaussian", False, expected_link="identity", kwargs_glm=dict(link="log"),
                                       metalearner_params=dict(family="gaussian", distribution="gaussian"))
    infer_mixed_family_and_dist_helper("gamma", "gaussian", True, expected_link="identity", kwargs_glm=dict(link="log"),
                                       metalearner_params=dict(family="gaussian", distribution="gaussian"))

    infer_mixed_family_and_dist_helper("gamma", "gaussian", False, expected_link="identity", kwargs_glm=dict(link="log"),
                                       metalearner_params=dict(family="gaussian", link="identity", distribution="gaussian"))
    infer_mixed_family_and_dist_helper("gamma", "gaussian", True, expected_link="identity", kwargs_glm=dict(link="log"),
                                       metalearner_params=dict(family="gaussian", link="identity", distribution="gaussian"))

if __name__ == "__main__":
    pyunit_utils.run_tests([
        infer_distribution_test,
        infer_family_test,
        infer_mixed_family_and_dist_test,
        metalearner_obeys_metalearner_params_test,
    ])
