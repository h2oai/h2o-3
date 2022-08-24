# -*- encoding: utf-8 -*-
"""

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

from collections import OrderedDict

from h2o.display import H2ODisplay, display, repr_def, format_to_html, format_to_multiline
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.metaclass import backwards_compatibility, deprecated_fn, h2o_meta
from h2o.utils.typechecks import is_type, numeric


@backwards_compatibility(
    instance_attrs=dict(
        giniCoef=lambda self, *args, **kwargs: self.gini(*args, **kwargs)
    )
)
class MetricsBase(h2o_meta(H2ODisplay)):
    """
    A parent class to house common metrics available for the various Metrics types.

    The methods here are available across different model categories.
    
    .. note::
        This class and its subclasses are used at runtime as mixins: their methods can (and should) be accessed directly 
        from a metrics object, for example as a result of :func:`~h2o.model.ModelBase.model_performance`.
    """
    _on_mapping = OrderedDict(
        training_metrics='train',
        validation_metrics='validation',
        cross_validation_metrics='cross-validation',
        _='test'
    )

    def __init__(self, metric_json, on=None, algo=""):
        self._metric_json = metric_json._metric_json if isinstance(metric_json, MetricsBase) else metric_json
        self._on = None
        self._algo = algo
        
        # assert on is None or on in MetricsBase._on_mapping
        self._on = MetricsBase._on_mapping.get(on or '_', None)
        if not self._on:
            raise ValueError("on param expected to be one of {accepted}, but got {on}: ".format(
                accepted=[k for k in MetricsBase._on_mapping if not k.startswith('_')],
                on=on
            ))

    @classmethod
    def make(cls, kvs):
        """Factory method to instantiate a MetricsBase object from the list of key-value pairs."""
        return cls(metric_json=dict(kvs))

    # TODO: convert to actual fields list
    def __getitem__(self, key):
        return self._metric_json.get(key)

    @staticmethod
    def _has(dictionary, key):
        return key in dictionary and dictionary[key] is not None
    
    def _str_items(self, verbosity=None):
        # edge cases
        if self._metric_json is None:
            return "WARNING: Model metrics cannot be calculated, please check that the response column was correctly provided in your dataset."

        metric_type = self._metric_json['__meta']['schema_type']
        
        # metric cond based on data distribution
        m_is_binomial = "Binomial" in metric_type
        m_is_multinomial = "Multinomial" in metric_type
        m_is_ordinal = "Ordinal" in metric_type
        m_is_regression = "Regression" in metric_type
        
        # metric cond based on algo
        m_is_anomaly = "Anomaly" in metric_type
        m_is_clustering = "Clustering" in metric_type
        m_is_generic = "Generic" in metric_type
        m_is_glm = "GLM" in metric_type
        m_is_hglm = "HGLM" in metric_type
        m_is_uplift = "Uplift" in metric_type

        # fixme: can't we rather check if each value is available instead of doing this weird logic?
        #  we could have mixin extensions for algos like (H)GLM instead taking everything from this (not so) "base" class.
        # specific metric cond
        m_supports_logloss = (m_is_binomial or m_is_multinomial or m_is_ordinal) and not m_is_uplift
        m_supports_mpce = (m_is_binomial or m_is_multinomial or m_is_ordinal) and not (m_is_glm or m_is_uplift)  # GLM excluded?
        m_supports_mse = not (m_is_anomaly or m_is_clustering or m_is_uplift)
        m_supports_r2 = m_is_regression and m_is_glm
        
        items = [
            "{mtype}: {algo}".format(mtype=metric_type, algo=self._algo),
            "** Reported on {} data. **".format(self._on),
            "",
        ]
        if self.custom_metric_name():  # adding on top: if users specifies a custom metric, it needs to be highlighted.
            items.append("{name}: {value}".format(name=self.custom_metric_name(),
                                                  value=self.custom_metric_value()))
        if m_supports_mse:
            items.extend([
                "MSE: {}".format(self.mse()),
                "RMSE: {}".format(self.rmse()),
            ])
        if m_is_regression:
            items.extend([
                "MAE: {}".format(self.mae()),
                "RMSLE: {}".format(self.rmsle()),
                "Mean Residual Deviance: {}".format(self.mean_residual_deviance()),
            ])
        if m_supports_r2:
            items.append("R^2: {}".format(self.r2()))
        if m_supports_logloss:
            items.append("LogLoss: {}".format(self.logloss()))
        if m_supports_mpce:
            items.append("Mean Per-Class Error: {}".format(self._mean_per_class_error()))
        if m_is_binomial and not m_is_uplift:  # can be picked from H2OBinomialModelMetrics (refers to method not available in this class!)
            items.extend([
                "AUC: {}".format(self.auc()),
                "AUCPR: {}".format(self.aucpr()),
                "Gini: {}".format(self.gini()),
            ])
        if m_is_multinomial:
            auc, aucpr = self.auc(), self.aucpr()
            if is_type(auc, numeric): items.append("AUC: {}".format(auc))
            if is_type(aucpr, numeric): items.append("AUCPR: {}".format(aucpr))
        if m_is_glm: 
            if m_is_hglm and not m_is_generic:
                items.extend([
                    "Standard error of fixed columns: {}".format(self.hglm_metric("sefe")),
                    "Standard error of random columns: {}".format(self.hglm_metric("sere")),
                    "Coefficients for fixed columns: {}".format(self.hglm_metric("fixedf")),
                    "Coefficients for random columns: {}".format(self.hglm_metric("ranef")),
                    "Random column indices: {}".format(self.hglm_metric("randc")),
                    "Dispersion parameter of the mean model (residual variance for LMM): {}".format(self.hglm_metric("varfix")),
                    "Dispersion parameter of the random columns (variance of random columns): {}".format(self.hglm_metric("varranef")),
                    "Convergence reached for algorithm: {}".format(self.hglm_metric("converge")),
                    "Deviance degrees of freedom for mean part of the model: {}".format(self.hglm_metric("dfrefe")),
                    "Estimates and standard errors of the linear prediction in the dispersion model: {}".format(self.hglm_metric("summvc1")),
                    "Estimates and standard errors of the linear predictor for the dispersion parameter of the random columns: {}".format(self.hglm_metric("summvc2")),
                    "Index of most influential observation (-1 if none): {}".format(self.hglm_metric("bad")),
                    "H-likelihood: {}".format(self.hglm_metric("hlik")),
                    "Profile log-likelihood profiled over random columns: {}".format(self.hglm_metric("pvh")),
                    "Adjusted profile log-likelihood profiled over fixed and random effects: {}".format(self.hglm_metric("pbvh")),
                    "Conditional AIC: {}".format(self.hglm_metric("caic")),
                ])
            else:
                items.extend([
                    "Null degrees of freedom: {}".format(self.null_degrees_of_freedom()),
                    "Residual degrees of freedom: {}".format(self.residual_degrees_of_freedom()),
                    "Null deviance: {}".format(self.null_deviance()),
                    "Residual deviance: {}".format(self.residual_deviance()),
                ])
                if is_type(self.aic(), numeric): items.append("AIC: {}".format(self.aic()))
        items.extend(self._str_items_custom())
        return items
    
    def _str_items_custom(self):
        return []

    def _repr_(self):
        return repr_def(self, attributes='all')

    def _str_(self, verbosity=None):
        items = self._str_items(verbosity)
        if isinstance(items, list):
            return format_to_multiline(items)
        return items

    def _str_html_(self, verbosity=None):
        items = self._str_items(verbosity)
        if isinstance(items, list):
            return format_to_html(items)
        return items
    
    def show(self, verbosity=None, fmt=None):
        return display(self, fmt=fmt, verbosity=verbosity)

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

    @deprecated_fn(replaced_by=aucpr)
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

    # private accessor for mean per-class error - the public version is overridden in H2OBinomialModelMetrics with
    # a method with different return semantics
    def _mean_per_class_error(self):
        return self._metric_json['mean_per_class_error']

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
        return self._mean_per_class_error()

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
