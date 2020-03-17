# -*- encoding: utf-8 -*-
"""
Regression model.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import imp

from h2o.model.confusion_matrix import ConfusionMatrix
from h2o.utils.metaclass import BackwardsCompatible, Deprecated as deprecated, h2o_meta
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type, assert_satisfies, is_type, numeric


@BackwardsCompatible(
    instance_attrs=dict(
        giniCoef=lambda self, *args, **kwargs: self.gini(*args, **kwargs)
    )
)
class MetricsBase(h2o_meta()):
    """
    A parent class to house common metrics available for the various Metrics types.

    The methods here are available across different model categories.
    """

    def __init__(self, metric_json, on=None, algo=""):
        # Yep, it's messed up...
        if isinstance(metric_json, MetricsBase): metric_json = metric_json._metric_json
        self._metric_json = metric_json
        # train and valid and xval are not mutually exclusive -- could have a test. train and
        # valid only make sense at model build time.
        self._on_train = False
        self._on_valid = False
        self._on_xval = False
        self._algo = algo
        if on == "training_metrics":
            self._on_train = True
        elif on == "validation_metrics":
            self._on_valid = True
        elif on == "cross_validation_metrics":
            self._on_xval = True
        elif on is None:
            pass
        else:
            raise ValueError("on expected to be train,valid,or xval. Got: " + str(on))

    @classmethod
    def make(cls, kvs):
        """Factory method to instantiate a MetricsBase object from the list of key-value pairs."""
        return cls(metric_json=dict(kvs))

    def __repr__(self):
        # FIXME !!!  __repr__ should never print anything, but return a string
        self.show()
        return ""

    # TODO: convert to actual fields list
    def __getitem__(self, key):
        return self._metric_json.get(key)

    @staticmethod
    def _has(dictionary, key):
        return key in dictionary and dictionary[key] is not None

    def show(self):
        """Display a short summary of the metrics.

        :examples:

        >>> from from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234) 
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.show()
        """
        if self._metric_json==None:
            print("WARNING: Model metrics cannot be calculated and metric_json is empty due to the absence of the response column in your dataset.")
            return
        metric_type = self._metric_json['__meta']['schema_type']
        types_w_glm = ['ModelMetricsRegressionGLM', 'ModelMetricsRegressionGLMGeneric', 'ModelMetricsBinomialGLM',
                       'ModelMetricsBinomialGLMGeneric', 'ModelMetricsHGLMGaussianGaussian', 
                       'ModelMetricsHGLMGaussianGaussianGeneric']
        types_w_clustering = ['ModelMetricsClustering']
        types_w_mult = ['ModelMetricsMultinomial', 'ModelMetricsMultinomialGeneric']
        types_w_ord = ['ModelMetricsOrdinal', 'ModelMetricsOrdinalGeneric']
        types_w_bin = ['ModelMetricsBinomial', 'ModelMetricsBinomialGeneric', 'ModelMetricsBinomialGLM', 'ModelMetricsBinomialGLMGeneric']
        types_w_r2 = ['ModelMetricsRegressionGLM', 'ModelMetricsRegressionGLMGeneric']
        types_w_mean_residual_deviance = ['ModelMetricsRegressionGLM', 'ModelMetricsRegressionGLMGeneric',
                                          'ModelMetricsRegression', 'ModelMetricsRegressionGeneric']
        types_w_mean_absolute_error = ['ModelMetricsRegressionGLM', 'ModelMetricsRegressionGLMGeneric',
                                       'ModelMetricsRegression', 'ModelMetricsRegressionGeneric']
        types_w_logloss = types_w_bin + types_w_mult+types_w_ord
        types_w_dim = ["ModelMetricsGLRM"]
        types_w_anomaly = ['ModelMetricsAnomaly']

        print()
        print(metric_type + ": " + self._algo)
        reported_on = "** Reported on {} data. **"
        if self._on_train:
            print(reported_on.format("train"))
        elif self._on_valid:
            print(reported_on.format("validation"))
        elif self._on_xval:
            print(reported_on.format("cross-validation"))
        else:
            print(reported_on.format("test"))
        print()
        if metric_type not in types_w_anomaly:
            print("MSE: " + str(self.mse()))
            print("RMSE: " + str(self.rmse()))
        if metric_type in types_w_mean_absolute_error:
            print("MAE: " + str(self.mae()))
            print("RMSLE: " + str(self.rmsle()))
        if metric_type in types_w_r2:
            print("R^2: " + str(self.r2()))
        if metric_type in types_w_mean_residual_deviance:
            print("Mean Residual Deviance: " + str(self.mean_residual_deviance()))
        if metric_type in types_w_logloss:
            print("LogLoss: " + str(self.logloss()))
        if metric_type in ['ModelMetricsBinomial', 'ModelMetricsBinomialGeneric']:
            # second element for first threshold is the actual mean per class error
            print("Mean Per-Class Error: %s" % self.mean_per_class_error()[0][1])
        if metric_type in types_w_mult or metric_type in ['ModelMetricsOrdinal', 'ModelMetricsOrdinalGeneric']:
            print("Mean Per-Class Error: " + str(self.mean_per_class_error()))
        if metric_type in types_w_glm:
            if metric_type == 'ModelMetricsHGLMGaussianGaussian': # print something for HGLM
                print("Standard error of fixed columns: "+str(self.hglm_metric("sefe")))
                print("Standard error of random columns: "+str(self.hglm_metric("sere")))
                print("Coefficients for fixed columns: "+str(self.hglm_metric("fixedf")))
                print("Coefficients for random columns: "+str(self.hglm_metric("ranef")))
                print("Random column indices: "+str(self.hglm_metric("randc")))
                print("Dispersion parameter of the mean model (residual variance for LMM): "+str(self.hglm_metric("varfix")))
                print("Dispersion parameter of the random columns (variance of random columns): "+str(self.hglm_metric("varranef")))
                print("Convergence reached for algorithm: "+str(self.hglm_metric("converge")))
                print("Deviance degrees of freedom for mean part of the model: "+str(self.hglm_metric("dfrefe")))
                print("Estimates and standard errors of the linear prediction in the dispersion model: "+str(self.hglm_metric("summvc1")))
                print("Estimates and standard errors of the linear predictor for the dispersion parameter of the random columns: "+str(self.hglm_metric("summvc2")))
                print("Index of most influential observation (-1 if none): "+str(self.hglm_metric("bad")))
                print("H-likelihood: "+str(self.hglm_metric("hlik")))
                print("Profile log-likelihood profiled over random columns: "+str(self.hglm_metric("pvh")))
                print("Adjusted profile log-likelihood profiled over fixed and random effects: "+str(self.hglm_metric("pbvh")))
                print("Conditional AIC: "+str(self.hglm_metric("caic")))
            else:
                print("Null degrees of freedom: " + str(self.null_degrees_of_freedom()))
                print("Residual degrees of freedom: " + str(self.residual_degrees_of_freedom()))
                print("Null deviance: " + str(self.null_deviance()))
                print("Residual deviance: " + str(self.residual_deviance()))
                print("AIC: " + str(self.aic()))
        if metric_type in types_w_bin:
            print("AUC: " + str(self.auc()))
            print("AUCPR: " + str(self.aucpr()))
            print("Gini: " + str(self.gini()))
            self.confusion_matrix().show()
            self._metric_json["max_criteria_and_metric_scores"].show()
            if self.gains_lift():
                print(self.gains_lift())
        if metric_type in types_w_anomaly:
            print("Anomaly Score: " + str(self.mean_score()))
            print("Normalized Anomaly Score: " + str(self.mean_normalized_score()))
        if (metric_type in types_w_mult) or (metric_type in types_w_ord):
            self.confusion_matrix().show()
            self.hit_ratio_table().show()
        if metric_type in types_w_clustering:
            print("Total Within Cluster Sum of Square Error: " + str(self.tot_withinss()))
            print("Total Sum of Square Error to Grand Mean: " + str(self.totss()))
            print("Between Cluster Sum of Square Error: " + str(self.betweenss()))
            self._metric_json['centroid_stats'].show()

        if metric_type in types_w_dim:
            print("Sum of Squared Error (Numeric): " + str(self.num_err()))
            print("Misclassification Error (Categorical): " + str(self.cat_err()))
        if self.custom_metric_name():
            print("{}: {}".format(self.custom_metric_name(), self.custom_metric_value()))


    def r2(self):
        """The R squared coefficient.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234) 
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.r2()
        """
        return self._metric_json["r2"]


    def logloss(self):
        """Log loss.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234) 
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.logloss()
        """
        return self._metric_json["logloss"]


    def nobs(self):
        """
        The number of observations.

        :examples:
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234) 
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> perf = cars_gbm.model_performance()
        >>> perf.nobs()
        """
        return self._metric_json["nobs"]


    def mean_residual_deviance(self):
        """The mean residual deviance for this set of metrics.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip")
        >>> air_gbm = H2OGradientBoostingEstimator()
        >>> air_gbm.train(x=list(range(9)),
        ...               y=9,
        ...               training_frame=airlines,
        ...               validation_frame=airlines)
        >>> air_gbm.mean_residual_deviance(train=True,valid=False,xval=False)
        """
        return self._metric_json["mean_residual_deviance"]


    def auc(self):
        """The AUC for this set of metrics.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234) 
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.auc()
        """
        return self._metric_json['AUC']


    def aucpr(self):
        """The area under the precision recall curve.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234) 
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.aucpr()
        """
        return self._metric_json['pr_auc']


    @deprecated(replaced_by=aucpr)
    def pr_auc(self):
        pass


    def aic(self):
        """The AIC for this set of metrics.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
        >>> prostate[2] = prostate[2].asfactor()
        >>> prostate[4] = prostate[4].asfactor()
        >>> prostate[5] = prostate[5].asfactor()
        >>> prostate[8] = prostate[8].asfactor()
        >>> predictors = ["AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
        >>> response = "CAPSULE"
        >>> train, valid = prostate.split_frame(ratios=[.8],seed=1234)
        >>> pros_glm = H2OGeneralizedLinearEstimator(family="binomial")
        >>> pros_glm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> pros_glm.aic()
        """
        return self._metric_json['AIC']


    def gini(self):
        """Gini coefficient.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234) 
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.gini()
        """
        return self._metric_json['Gini']


    def mse(self):
        """The MSE for this set of metrics.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234) 
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.mse()
        """
        return self._metric_json['MSE']


    def rmse(self):
        """The RMSE for this set of metrics.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234) 
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.rmse()
        """
        return self._metric_json['RMSE']


    def mae(self):
        """The MAE for this set of metrics.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "cylinders"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(distribution = "poisson",
        ...                                         seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.mae()
        """
        return self._metric_json['mae']


    def rmsle(self):
        """The RMSLE for this set of metrics.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "cylinders"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(distribution = "poisson",
        ...                                         seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.rmsle()
        """
        return self._metric_json['rmsle']


    def residual_deviance(self):
        """The residual deviance if the model has it, otherwise None.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
        >>> prostate[2] = prostate[2].asfactor()
        >>> prostate[4] = prostate[4].asfactor()
        >>> prostate[5] = prostate[5].asfactor()
        >>> prostate[8] = prostate[8].asfactor()
        >>> predictors = ["AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
        >>> response = "CAPSULE"
        >>> train, valid = prostate.split_frame(ratios=[.8],seed=1234)
        >>> pros_glm = H2OGeneralizedLinearEstimator(family="binomial")
        >>> pros_glm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> pros_glm.residual_deviance()
        """
        if MetricsBase._has(self._metric_json, "residual_deviance"):
            return self._metric_json["residual_deviance"]
        return None
    
    def hglm_metric(self, metric_string):
        if MetricsBase._has(self._metric_json, metric_string):
            return self._metric_json[metric_string]
        return None
    
    def residual_degrees_of_freedom(self):
        """The residual DoF if the model has residual deviance, otherwise None.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
        >>> prostate[2] = prostate[2].asfactor()
        >>> prostate[4] = prostate[4].asfactor()
        >>> prostate[5] = prostate[5].asfactor()
        >>> prostate[8] = prostate[8].asfactor()
        >>> predictors = ["AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
        >>> response = "CAPSULE"
        >>> train, valid = prostate.split_frame(ratios=[.8],seed=1234)
        >>> pros_glm = H2OGeneralizedLinearEstimator(family="binomial")
        >>> pros_glm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> pros_glm.residual_degrees_of_freedom()
        """
        if MetricsBase._has(self._metric_json, "residual_degrees_of_freedom"):
            return self._metric_json["residual_degrees_of_freedom"]
        return None


    def null_deviance(self):
        """The null deviance if the model has residual deviance, otherwise None.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
        >>> prostate[2] = prostate[2].asfactor()
        >>> prostate[4] = prostate[4].asfactor()
        >>> prostate[5] = prostate[5].asfactor()
        >>> prostate[8] = prostate[8].asfactor()
        >>> predictors = ["AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
        >>> response = "CAPSULE"
        >>> train, valid = prostate.split_frame(ratios=[.8],seed=1234)
        >>> pros_glm = H2OGeneralizedLinearEstimator(family="binomial")
        >>> pros_glm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> pros_glm.null_deviance()
        """
        if MetricsBase._has(self._metric_json, "null_deviance"):
            return self._metric_json["null_deviance"]
        return None


    def null_degrees_of_freedom(self):
        """The null DoF if the model has residual deviance, otherwise None.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
        >>> prostate[2] = prostate[2].asfactor()
        >>> prostate[4] = prostate[4].asfactor()
        >>> prostate[5] = prostate[5].asfactor()
        >>> prostate[8] = prostate[8].asfactor()
        >>> predictors = ["AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
        >>> response = "CAPSULE"
        >>> train, valid = prostate.split_frame(ratios=[.8],seed=1234)
        >>> pros_glm = H2OGeneralizedLinearEstimator(family="binomial")
        >>> pros_glm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> pros_glm.null_degrees_of_freedom()
        """
        if MetricsBase._has(self._metric_json, "null_degrees_of_freedom"):
            return self._metric_json["null_degrees_of_freedom"]
        return None


    def mean_per_class_error(self):
        """The mean per class error.

        :examples:

        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
        >>> prostate[2] = prostate[2].asfactor()
        >>> prostate[4] = prostate[4].asfactor()
        >>> prostate[5] = prostate[5].asfactor()
        >>> prostate[8] = prostate[8].asfactor()
        >>> predictors = ["AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
        >>> response = "CAPSULE"
        >>> train, valid = prostate.split_frame(ratios=[.8],seed=1234)
        >>> pros_glm = H2OGeneralizedLinearEstimator(family="binomial")
        >>> pros_glm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> pros_glm.mean_per_class_error()
        """
        return self._metric_json['mean_per_class_error']

    def custom_metric_name(self):
        """Name of custom metric or None."""
        if MetricsBase._has(self._metric_json, "custom_metric_name"):
            return self._metric_json['custom_metric_name']
        else:
            return None

    def custom_metric_value(self):
        """Value of custom metric or None."""
        if MetricsBase._has(self._metric_json, "custom_metric_value"):
            return self._metric_json['custom_metric_value']
        else:
            return None


class H2ORegressionModelMetrics(MetricsBase):
    """
    This class provides an API for inspecting the metrics returned by a regression model.

    It is possible to retrieve the R^2 (1 - MSE/variance) and MSE.

    :examples:

    >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
    >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
    >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    >>> predictors = ["displacement","power","weight","acceleration","year"]
    >>> response = "cylinders"
    >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
    >>> cars_glm = H2OGeneralizedLinearEstimator()
    >>> cars_glm.train(x = predictors,
    ...                y = response,
    ...                training_frame = train,
    ...                validation_frame = valid)
    >>> cars_glm.mse()
    """

    def __init__(self, metric_json, on=None, algo=""):
        super(H2ORegressionModelMetrics, self).__init__(metric_json, on, algo)




class H2OClusteringModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OClusteringModelMetrics, self).__init__(metric_json, on, algo)


    def tot_withinss(self):
        """The Total Within Cluster Sum-of-Square Error, or None if not present.

        :examples:

        >>> from h2o.estimators.kmeans import H2OKMeansEstimator
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> km = H2OKMeansEstimator(k=3, nfolds=3)
        >>> km.train(x=list(range(4)), training_frame=iris)
        >>> km.tot_withinss()
        """
        if MetricsBase._has(self._metric_json, "tot_withinss"):
            return self._metric_json["tot_withinss"]
        return None


    def totss(self):
        """The Total Sum-of-Square Error to Grand Mean, or None if not present.

        :examples:

        >>> from h2o.estimators.kmeans import H2OKMeansEstimator
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> km = H2OKMeansEstimator(k=3, nfolds=3)
        >>> km.train(x=list(range(4)), training_frame=iris)
        >>> km.totss()
        """
        if MetricsBase._has(self._metric_json, "totss"):
            return self._metric_json["totss"]
        return None

    def betweenss(self):
        """The Between Cluster Sum-of-Square Error, or None if not present.

        :examples:

        >>> from h2o.estimators.kmeans import H2OKMeansEstimator
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> km = H2OKMeansEstimator(k=3, nfolds=3)
        >>> km.train(x=list(range(4)), training_frame=iris)
        >>> km.betweenss()
        """
        if MetricsBase._has(self._metric_json, "betweenss"):
            return self._metric_json["betweenss"]
        return None




class H2OMultinomialModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OMultinomialModelMetrics, self).__init__(metric_json, on, algo)

    def confusion_matrix(self):
        """Returns a confusion matrix based of H2O's default prediction threshold for a dataset.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution = distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.confusion_matrix(train)
        """
        return self._metric_json['cm']['table']


    def hit_ratio_table(self):
        """Retrieve the Hit Ratios.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution = distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.hit_ratio_table()
        """
        return self._metric_json['hit_ratio_table']



class H2OOrdinalModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OOrdinalModelMetrics, self).__init__(metric_json, on, algo)

    def confusion_matrix(self):
        """Returns a confusion matrix based of H2O's default prediction threshold for a dataset.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution = distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.confusion_matrix(train)
        """
        return self._metric_json['cm']['table']


    def hit_ratio_table(self):
        """Retrieve the Hit Ratios.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution = distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.hit_ratio_table()
        """
        return self._metric_json['hit_ratio_table']


class H2OHGLMModelMetrics(MetricsBase):
    def __init__(self, metric_json, on=None, algo="HGLM Gaussian Gaussian"):
        super(H2OHGLMModelMetrics, self).__init__(metric_json, on, algo)


class H2OBinomialModelMetrics(MetricsBase):
    """
    This class is essentially an API for the AUC object.
    This class contains methods for inspecting the AUC for different criteria.
    To input the different criteria, use the static variable `criteria`.
    """

    def __init__(self, metric_json, on=None, algo=""):
        """
          Create a new Binomial Metrics object (essentially a wrapper around some json)

          :param metric_json: A blob of json holding all of the needed information
          :param on_train: Metrics built on training data (default is False)
          :param on_valid: Metrics built on validation data (default is False)
          :param on_xval: Metrics built on cross validation data (default is False)
          :param algo: The algorithm the metrics are based off of (e.g. deeplearning, gbm, etc.)
          :returns: A new H2OBinomialModelMetrics object.

          :examples:

          >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
          >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
          >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
          >>> predictors = ["displacement","power","weight","acceleration","year"]
          >>> response = "economy_20mpg"
          >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
          >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
          >>> cars_gbm.train(x = predictors,
          ...                y = response,
          ...                training_frame = train,
          ...                validation_frame = valid)
          >>> cars_gbm.auc(valid=True)
          """
        super(H2OBinomialModelMetrics, self).__init__(metric_json, on, algo)


    def F1(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The F1 for the given set of thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.F1()
        """
        return self.metric("f1", thresholds=thresholds)


    def F2(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The F2 for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.F2()
        """
        return self.metric("f2", thresholds=thresholds)


    def F0point5(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The F0.5 for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.F0point5()
        """
        return self.metric("f0point5", thresholds=thresholds)


    def accuracy(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The accuracy for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.accuracy()
        """
        return self.metric("accuracy", thresholds=thresholds)


    def error(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold minimizing the error will be used.
        :returns: The error for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.error()
        """
        return H2OBinomialModelMetrics._accuracy_to_error(self.metric("accuracy", thresholds=thresholds))


    def precision(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The precision for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.precision()
        """
        return self.metric("precision", thresholds=thresholds)


    def tpr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The True Postive Rate.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.tpr()
        """
        return self.metric("tpr", thresholds=thresholds)


    def tnr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The True Negative Rate.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.tnr()
        """
        return self.metric("tnr", thresholds=thresholds)


    def fnr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The False Negative Rate.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.fnr()
        """
        return self.metric("fnr", thresholds=thresholds)


    def fpr(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The False Positive Rate.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.fpr()
        """
        return self.metric("fpr", thresholds=thresholds)


    def recall(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: Recall for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.recall()
        """
        return self.metric("recall", thresholds=thresholds)


    def sensitivity(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: Sensitivity or True Positive Rate for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.sensitivity()
        """
        return self.metric("sensitivity", thresholds=thresholds)


    def fallout(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The fallout (same as False Positive Rate) for this set of metrics and thresholds.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.fallout()

        """
        return self.metric("fallout", thresholds=thresholds)


    def missrate(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The miss rate (same as False Negative Rate).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.missrate()
        """
        return self.metric("missrate", thresholds=thresholds)


    def specificity(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The specificity (same as True Negative Rate).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.specificity()
        """
        return self.metric("specificity", thresholds=thresholds)


    def mcc(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
        :returns: The absolute MCC (a value between 0 and 1, 0 being totally dissimilar, 1 being identical).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.mcc()
        """
        return self.metric("absolute_mcc", thresholds=thresholds)


    def max_per_class_error(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold minimizing the error will be used.
        :returns: Return 1 - min(per class accuracy).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.max_per_class_error()
        """
        return H2OBinomialModelMetrics._accuracy_to_error(self.metric("min_per_class_accuracy", thresholds=thresholds))


    def mean_per_class_error(self, thresholds=None):
        """
        :param thresholds: thresholds parameter must be a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold minimizing the error will be used.
        :returns: mean per class error.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.mean_per_class_error()
        """
        return H2OBinomialModelMetrics._accuracy_to_error(self.metric("mean_per_class_accuracy", thresholds=thresholds))


    @staticmethod
    def _accuracy_to_error(accuracies):
        errors = List()
        errors.extend([acc[0], 1 - acc[1]] for acc in accuracies)
        setattr(errors, 'value',
                [1 - v for v in accuracies.value] if isinstance(accuracies.value, list)
                else 1 - accuracies.value
                )
        return errors


    def metric(self, metric, thresholds=None):
        """
        :param str metric: A metric among :const:`maximizing_metrics`.
        :param thresholds: thresholds parameter must be a number or a list (i.e. [0.01, 0.5, 0.99]).
            If None, then the threshold maximizing the metric will be used.
            If 'all', then all stored thresholds are used and returned with the matching metric.
        :returns: The set of metrics for the list of thresholds.
            The returned list has a 'value' property holding only
            the metric value (if no threshold provided or if provided as a number),
            or all the metric values (if thresholds provided as a list)

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> local_data = [[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b']]
        >>> h2o_data = h2o.H2OFrame(local_data)
        >>> h2o_data.set_names(['response', 'predictor'])
        >>> h2o_data["response"] = h2o_data["response"].asfactor()
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1,
        ...                                    distribution="bernoulli")
        >>> gbm.train(x=list(range(1,h2o_data.ncol)),
        ...           y="response",
        ...           training_frame=h2o_data)
        >>> perf = gbm.model_performance()
        >>> perf.metric("tps", [perf.find_threshold_by_max_metric("f1")])[0][1]
        """
        assert_is_type(thresholds, None, 'all', numeric, [numeric])
        if metric not in H2OBinomialModelMetrics.maximizing_metrics:
            raise ValueError("The only allowable metrics are {}".format(', '.join(H2OBinomialModelMetrics.maximizing_metrics)))

        h2o_metric = (H2OBinomialModelMetrics.metrics_aliases[metric] if metric in H2OBinomialModelMetrics.metrics_aliases
                      else metric)
        value_is_scalar = is_type(metric, str) and (thresholds is None or is_type(thresholds, numeric))
        if thresholds is None:
            thresholds = [self.find_threshold_by_max_metric(h2o_metric)]
        elif thresholds == 'all':
            thresholds = None
        elif is_type(thresholds, numeric):
            thresholds = [thresholds]

        metrics = List()
        thresh2d = self._metric_json['thresholds_and_metric_scores']
        if thresholds is None:  # fast path to return all thresholds: skipping find_idx logic
            metrics.extend(list(t) for t in zip(thresh2d['threshold'], thresh2d[h2o_metric]))
        else:
            for t in thresholds:
                idx = self.find_idx_by_threshold(t)
                metrics.append([t, thresh2d[h2o_metric][idx]])

        setattr(metrics, 'value',
                metrics[0][1] if value_is_scalar
                else list(r[1] for r in metrics)
                )
        return metrics


    def plot(self, type="roc", server=False):
        """
        Produce the desired metric plot.

        :param type: the type of metric plot (currently, only ROC supported).
        :param server: if True, generate plot inline using matplotlib's "Agg" backend.
        :returns: None

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> response = "economy_20mpg"
        >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
        >>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
        >>> cars_gbm.train(x = predictors,
        ...                y = response,
        ...                training_frame = train,
        ...                validation_frame = valid)
        >>> cars_gbm.plot()
        """
        # TODO: add more types (i.e. cutoffs)
        assert_is_type(type, "roc")
        # check for matplotlib. exit if absent.
        try:
            imp.find_module('matplotlib')
            import matplotlib
            if server: matplotlib.use('Agg', warn=False)
            import matplotlib.pyplot as plt
        except ImportError:
            print("matplotlib is required for this function!")
            return

        if type == "roc":
            plt.xlabel('False Positive Rate (FPR)')
            plt.ylabel('True Positive Rate (TPR)')
            plt.title('ROC Curve')
            plt.text(0.5, 0.5, r'AUC={0:.4f}'.format(self._metric_json["AUC"]))
            plt.plot(self.fprs, self.tprs, 'b--')
            plt.axis([0, 1, 0, 1])
            if not server: plt.show()


    @property
    def fprs(self):
        """
        Return all false positive rates for all threshold values.

        :returns: a list of false positive rates.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3, distribution=distribution, fold_assignment="Random")
        >>> gbm.train(y=response_col, x=predictors, validation_frame=valid, training_frame=train)
        >>> (fprs, tprs) = gbm.roc(train=True, valid=False, xval=False)
        >>> fprs
        """
        return self._metric_json["thresholds_and_metric_scores"]["fpr"]


    @property
    def tprs(self):
        """
        Return all true positive rates for all threshold values.

        :returns: a list of true positive rates.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3, distribution=distribution, fold_assignment="Random")
        >>> gbm.train(y=response_col, x=predictors, validation_frame=valid, training_frame=train)
        >>> (fprs, tprs) = gbm.roc(train=True, valid=False, xval=False)
        >>> tprs
        """
        return self._metric_json["thresholds_and_metric_scores"]["tpr"]


    def roc(self):
        """
        Return the coordinates of the ROC curve as a tuple containing the false positive rates as a list and true positive rates as a list.
        :returns: The ROC values.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <= .2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(x=predictors,
        ...           y=response_col,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> gbm.roc(train=True,  valid=False, xval=False)
        """
        return self.fprs, self.tprs


    metrics_aliases = dict(
        fallout='fpr',
        missrate='fnr',
        recall='tpr',
        sensitivity='tpr',
        specificity='tnr'
    )

    #: metrics names allowed for confusion matrix
    maximizing_metrics = ('absolute_mcc', 'accuracy', 'precision',
                          'f0point5', 'f1', 'f2',
                          'mean_per_class_accuracy', 'min_per_class_accuracy',
                          'tns', 'fns', 'fps', 'tps',
                          'tnr', 'fnr', 'fpr', 'tpr') + tuple(metrics_aliases.keys())

    def confusion_matrix(self, metrics=None, thresholds=None):
        """
        Get the confusion matrix for the specified metric

        :param metrics: A string (or list of strings) among metrics listed in :const:`maximizing_metrics`. Defaults to 'f1'.
        :param thresholds: A value (or list of values) between 0 and 1.
            If None, then the thresholds maximizing each provided metric will be used.
        :returns: a list of ConfusionMatrix objects (if there are more than one to return), or a single ConfusionMatrix
            (if there is only one).

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.confusion_matrix(train)
        """
        # make lists out of metrics and thresholds arguments
        if metrics is None and thresholds is None:
            metrics = ['f1']

        if isinstance(metrics, list):
            metrics_list = metrics
        elif metrics is None:
            metrics_list = []
        else:
            metrics_list = [metrics]

        if isinstance(thresholds, list):
            thresholds_list = thresholds
        elif thresholds is None:
            thresholds_list = []
        else:
            thresholds_list = [thresholds]

        # error check the metrics_list and thresholds_list
        assert_is_type(thresholds_list, [numeric])
        assert_satisfies(thresholds_list, all(0 <= t <= 1 for t in thresholds_list))

        if not all(m.lower() in H2OBinomialModelMetrics.maximizing_metrics for m in metrics_list):
            raise ValueError("The only allowable metrics are {}".format(', '.join(H2OBinomialModelMetrics.maximizing_metrics)))

        # make one big list that combines the thresholds and metric-thresholds
        metrics_thresholds = [self.find_threshold_by_max_metric(m) for m in metrics_list]
        for mt in metrics_thresholds:
            thresholds_list.append(mt)
        first_metrics_thresholds_offset = len(thresholds_list) - len(metrics_thresholds)

        thresh2d = self._metric_json['thresholds_and_metric_scores']
        actual_thresholds = [float(e[0]) for i, e in enumerate(thresh2d.cell_values)]
        cms = []
        for i, t in enumerate(thresholds_list):
            idx = self.find_idx_by_threshold(t)
            row = thresh2d.cell_values[idx]
            tns = row[11]
            fns = row[12]
            fps = row[13]
            tps = row[14]
            p = tps + fns
            n = tns + fps
            c0 = n - fps
            c1 = p - tps
            if t in metrics_thresholds:
                m = metrics_list[i - first_metrics_thresholds_offset]
                table_header = "Confusion Matrix (Act/Pred) for max {} @ threshold = {}".format(m, actual_thresholds[idx])
            else:
                table_header = "Confusion Matrix (Act/Pred) @ threshold = {}".format(actual_thresholds[idx])
            cms.append(ConfusionMatrix(cm=[[c0, fps], [c1, tps]], domains=self._metric_json['domain'],
                                       table_header=table_header))

        if len(cms) == 1:
            return cms[0]
        else:
            return cms


    def find_threshold_by_max_metric(self, metric):
        """
        :param metrics: A string among the metrics listed in :const:`maximizing_metrics`.
        :returns: the threshold at which the given metric is maximal.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> local_data = [[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b']]
        >>> h2o_data = h2o.H2OFrame(local_data)
        >>> h2o_data.set_names(['response', 'predictor'])
        >>> h2o_data["response"] = h2o_data["response"].asfactor()
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1,
        ...                                    distribution="bernoulli")
        >>> gbm.train(x=list(range(1,h2o_data.ncol)),
        ...           y="response",
        ...           training_frame=h2o_data)
        >>> perf = gbm.model_performance()
        >>> perf.find_threshold_by_max_metric("f1")
        """
        crit2d = self._metric_json['max_criteria_and_metric_scores']
        # print(crit2d)
        h2o_metric = (H2OBinomialModelMetrics.metrics_aliases[metric] if metric in H2OBinomialModelMetrics.metrics_aliases
                      else metric)
        for e in crit2d.cell_values:
            if e[0] == "max " + h2o_metric.lower():
                return e[1]
        raise ValueError("No metric " + str(metric.lower()))


    def find_idx_by_threshold(self, threshold):
        """
        Retrieve the index in this metric's threshold list at which the given threshold is located.

        :param threshold: Find the index of this input threshold.
        :returns: the index
        :raises ValueError: if no such index can be found.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> local_data = [[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],[1, 'a'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],
        ...               [0, 'b'],[0, 'b'],[0, 'b'],[0, 'b'],[0, 'b']]
        >>> h2o_data = h2o.H2OFrame(local_data)
        >>> h2o_data.set_names(['response', 'predictor'])
        >>> h2o_data["response"] = h2o_data["response"].asfactor()
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1,
        ...                                    distribution="bernoulli")
        >>> gbm.train(x=list(range(1,h2o_data.ncol)),
        ...           y="response",
        ...           training_frame=h2o_data)
        >>> perf = gbm.model_performance()
        >>> perf.find_idx_by_threshold(0.45)
        """
        assert_is_type(threshold, numeric)
        thresh2d = self._metric_json['thresholds_and_metric_scores']
        # print(thresh2d)
        for i, e in enumerate(thresh2d.cell_values):
            t = float(e[0])
            if abs(t - threshold) < 1e-8 * max(t, threshold):
                return i
        if 0 <= threshold <= 1:
            thresholds = [float(e[0]) for i, e in enumerate(thresh2d.cell_values)]
            threshold_diffs = [abs(t - threshold) for t in thresholds]
            closest_idx = threshold_diffs.index(min(threshold_diffs))
            closest_threshold = thresholds[closest_idx]
            print("Could not find exact threshold {0}; using closest threshold found {1}."
                  .format(threshold, closest_threshold))
            return closest_idx
        raise ValueError("Threshold must be between 0 and 1, but got {0} ".format(threshold))


    def gains_lift(self):
        """Retrieve the Gains/Lift table.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.gains_lift()
        """
        if 'gains_lift_table' in self._metric_json:
            return self._metric_json['gains_lift_table']
        return None




class H2OAutoEncoderModelMetrics(MetricsBase):
    """
    :examples:

    >>> from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
    >>> train_ecg = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_train.csv")
    >>> test_ecg = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_test.csv")
    >>> anomaly_model = H2OAutoEncoderEstimator(activation="Tanh",
    ...                                         hidden=[50,50,50],
    ...                                         sparse=True, l1=1e-4,
    ...                                         epochs=100)
    >>> anomaly_model.train(x=train_ecg.names, training_frame=train_ecg)
    >>> anomaly_model.mse()
    """
    def __init__(self, metric_json, on=None, algo=""):
        super(H2OAutoEncoderModelMetrics, self).__init__(metric_json, on, algo)




class H2ODimReductionModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2ODimReductionModelMetrics, self).__init__(metric_json, on, algo)

    def num_err(self):
        """Sum of Squared Error over non-missing numeric entries, or None if not present."""
        if MetricsBase._has(self._metric_json, "numerr"):
            return self._metric_json["numerr"]
        return None

    def cat_err(self):
        """The Number of Misclassified categories over non-missing categorical entries, or None if not present."""
        if MetricsBase._has(self._metric_json, "caterr"):
            return self._metric_json["caterr"]
        return None




class H2OWordEmbeddingModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OWordEmbeddingModelMetrics, self).__init__(metric_json, on, algo)


class H2OAnomalyDetectionModelMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OAnomalyDetectionModelMetrics, self).__init__(metric_json, on, algo)

    def mean_score(self):
        """
        Mean Anomaly Score. For Isolation Forest represents the average of all tree-path lengths.

        :examples:

        >>> from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
        >>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
        >>> test = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_test.csv")
        >>> isofor_model = H2OIsolationForestEstimator(sample_size=5, ntrees=7)
        >>> isofor_model.train(training_frame = train)
        >>> perf = isofor_model.model_performance()
        >>> perf.mean_score()
        """
        if MetricsBase._has(self._metric_json, "mean_score"):
            return self._metric_json["mean_score"]
        return None

    def mean_normalized_score(self):
        """
        Mean Normalized Anomaly Score. For Isolation Forest - normalized average path length.

        :examples:

        >>> from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
        >>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
        >>> test = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_test.csv")
        >>> isofor_model = H2OIsolationForestEstimator(sample_size=5, ntrees=7)
        >>> isofor_model.train(training_frame = train)
        >>> perf = isofor_model.model_performance()
        >>> perf.mean_normalized_score()

        """
        if MetricsBase._has(self._metric_json, "mean_normalized_score"):
            return self._metric_json["mean_normalized_score"]
        return None


class H2OCoxPHModelMetrics(MetricsBase):
    """
    :examples:

    >>> from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
    >>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
    >>> coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
    ...                                            stop_column="stop",
    ...                                            ties="breslow")
    >>> coxph.train(x="age", y="event", training_frame=heart)
    >>> coxph
    """

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OCoxPHModelMetrics, self).__init__(metric_json, on, algo)


class H2OTargetEncoderMetrics(MetricsBase):

    def __init__(self, metric_json, on=None, algo=""):
        super(H2OTargetEncoderMetrics, self).__init__(metric_json, on, algo)


class List(list):
    pass
