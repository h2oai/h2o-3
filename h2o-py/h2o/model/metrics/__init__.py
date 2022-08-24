"""
.. note::
    Classes in this module are used at runtime as mixins: their methods can (and should) be accessed directly 
    from a metrics object, for example as a result of :func:`~h2o.model.ModelBase.model_performance`.
"""
from .anomaly_detection import H2OAnomalyDetectionModelMetrics
from .binomial import H2OBinomialModelMetrics
from .clustering import H2OClusteringModelMetrics
from .coxph import H2ORegressionCoxPHModelMetrics
from .dim_reduction import H2ODimReductionModelMetrics
from .generic import H2ODefaultModelMetrics
from .multinomial import H2OMultinomialModelMetrics
from .ordinal import H2OOrdinalModelMetrics
from .regression import H2ORegressionModelMetrics
from .uplift import H2OBinomialUpliftModelMetrics


def make_metrics(schema, keyvals):
    if schema == "ModelMetricsBinomialV3": return H2OBinomialModelMetrics.make(keyvals)
    if schema == "ModelMetricsBinomialUpliftV3": return H2OBinomialUpliftModelMetrics.make(keyvals)
    if schema == "ModelMetricsClusteringV3": return H2OClusteringModelMetrics.make(keyvals)
    if schema == "ModelMetricsMultinomialV3": return H2OMultinomialModelMetrics.make(keyvals)
    if schema == "ModelMetricsOrdinalV3": return H2OOrdinalModelMetrics.make(keyvals)
    if schema == "ModelMetricsRegressionV3": return H2ORegressionModelMetrics.make(keyvals)
    # print("Schema {} does not have a corresponding metrics class!".format(schema))
    return H2ODefaultModelMetrics.make(keyvals)


__all__ = [s for s in dir() if not s.startswith('_')]
