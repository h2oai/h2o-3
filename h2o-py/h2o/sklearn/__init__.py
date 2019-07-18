from sklearn.base import ClassifierMixin, RegressorMixin

from .. import automl
from .. import estimators
from .pipeline import H2OPipeline, H2OClassifierPipeline, H2ORegressorPipeline
from .wrapper import estimator, expect_h2o_frames

module = __name__
H2OAutoMLEstimator = estimator(automl.H2OAutoML, is_generic=True)
H2OAutoMLClassifier = estimator(automl.H2OAutoML, name=module+".H2OAutoMLClassifier", mixins=(ClassifierMixin,))
H2OAutoMLRegressor = estimator(automl.H2OAutoML, name=module+".H2OAutoMLRegressor", mixins=(RegressorMixin,))

# H2OGradientBoostingEstimator = estimator(estimators.H2OGradientBoostingEstimator)
# H2OXGBoostEstimator = estimator(estimators.H2OXGBoostEstimator)

__all__ = (
    expect_h2o_frames,
    H2OAutoMLEstimator,
    H2OAutoMLClassifier,
    H2OAutoMLRegressor,
    # H2OPipeline,
    # H2OClassifierPipeline,
    # H2ORegressorPipeline,
)

# pipeline = H2OPipeline(steps=[
#     ('automl', H2OAutoMLEstimator(nfolds=2))
# ])
