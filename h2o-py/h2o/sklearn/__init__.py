"""
The `h2o.sklearn` module exposes H2O estimators that try to be fully-compliant with sklearn estimators.
Those are just wrappers on top of the original H2O estimators and transformers available in the following modules:
 - `h2o.automl`.
 - `h2o.estimators`.
 - `h2o.transforms`.
On top of their original counterparts, the `sklearn` estimators add the following features:
 - `sklearn` class name semantic: estimators used for classification are named `___Classifier`,
 those for regression are named `___Regressor`, and generic estimators are named `___Estimator`.
 - full support of the `sklearn` API: `fit`, `transform`, `fit_transform`, `inverse_transform`,
 `predict`, `predict_proba`, `predict_log_proba`, `score`.
 Some of those methods are of course enabled only when it makes sense.
 - support for various input data types: in addition to the `h2o.H2OFrame` required by original H2O estimators,
  those wrappers accept lists, numpy arrays, pandas.Dataframe.
 - automatic conversion of return data types for methods like `predict` based on the input type:
    - `H2OFrame` -> `H2OFrame`
    - `numpy` -> `numpy`
    - `pandas` -> `numpy` (same behaviour) as `sklearn` estimators.
 - minimize data conversions in `sklearn` Pipeline contexts.
 - automatic handling of the connection with the H2O backend.
 - full support for `get_params`, `set_params`: all params are exposed (not only the ones already set).
 - feature discovery for ALL estimators thanks to auto-completion for constructor params in Jupyter/iPython context.

 Please note that for advanced usage, although those estimators work in `sklearn` pipelines and search algorithms,
 they may create a memory overhead due to the duplication of data when using `sklearn` cross-validation for example.
 Therefore this API is mainly recommended for exploration or as a quick introduction to H2O-3.
"""

import inspect
from operator import attrgetter
import sys

from sklearn.base import ClassifierMixin, RegressorMixin

from .. import automl
from .. import estimators
from .. import transforms
from .wrapper import estimator, params_as_h2o_frames, register_module, transformer, H2OConnectionMonitorMixin, \
    H2OEstimatorPredictProbabilitiesSupport, H2OEstimatorScoreSupport, H2OEstimatorTransformSupport

module = sys.modules[__name__]

def _noop(*args, **kwargs):
    pass

def _register_submodule(name=None):
    """
    If name is provided, this registers and attaches a new submodule to the current module.
    :param name: the name of the submodule or None if no submodule is used
    :return: the module name for the (newly) registered submodule
    """
    mod_name = __name__
    if name:
        mod_name = '.'.join([mod_name, name])
        mod = register_module(mod_name)
        setattr(module, name, mod)
    return mod_name


def _make_default_params(cls):
    """
    :param cls: the original h2o estimator class.
    :return: a dictionary representing the default estimator params
    that will be used to generate the constructor for the sklearn wrapper.
    """
    if hasattr(cls, 'param_names'):  # for subclasses of H2OEstimator
        return {k: None for k in cls.param_names}
    return None


def _get_custom_params(cls):
    """
    :param cls: the original h2o estimator class.
    :return: a dictionary of params used to customize the behaviour of the sklearn wrapper.
    """
    custom = {}
    # if cls.__name__ in ['H2OGeneralizedLowRankEstimator']:
    #     custom.update(predictions_col=-1)
    if cls.__name__ in ['H2OAutoEncoderEstimator',
                        'H2OGeneralizedLowRankEstimator',
                        'H2OPrincipalComponentAnalysisEstimator',
                        'H2OSingularValueDecompositionEstimator',
                        'H2OTargetEncoderEstimator']:
        custom.update(predictions_col='all')  # `predict` will return all columns (instead of a vector by default).
    if cls.__name__ in ['H2OGeneralizedLinearEstimator', 'H2OGeneralizedAdditiveEstimator']:
        custom.update(distribution_param='family')  # use algo `family` param to identify distribution (default is `distribution`).
    if cls.__name__ in ['H2ONaiveBayesEstimator',
                        'H2OSupportVectorMachineEstimator']:
        custom.update(default_estimator_type='classifier')  #  makes the generic sklearn estimator a `classifier` by default.

    if cls.__name__ in ['H2OAggregatorEstimator']:
        def _transform(self, X):
            self._estimator.train(training_frame=X)
            return self._estimator.aggregated_frame
        custom.update(
            _fit=_noop,
            # _transform=attrgetter('aggregated_frame'),
            _transform=_transform
        )
    return custom or None


def _estimator_supports_predict_proba(cls):
    return cls.__name__ not in ['H2OAutoEncoderEstimator',
                                'H2OGeneralizedLowRankEstimator',
                                'H2OIsolationForestEstimator',
                                'H2OKMeansEstimator',
                                'H2OPrincipalComponentAnalysisEstimator',
                                'H2OSingularValueDecompositionEstimator',
                                'H2OTargetEncoderEstimator']


def _estimator_supports_score(cls):
    return cls.__name__ not in ['H2OAutoEncoderEstimator',
                                'H2OGeneralizedLowRankEstimator',
                                'H2OIsolationForestEstimator',
                                'H2OKMeansEstimator',
                                'H2OPrincipalComponentAnalysisEstimator',
                                'H2OSingularValueDecompositionEstimator',
                                'H2OTargetEncoderEstimator']


def _estimator_supports_transform(cls):
    return cls.__name__ in ['H2OAggregatorEstimator',
                            'H2OGeneralizedLowRankEstimator',
                            'H2OPrincipalComponentAnalysisEstimator',
                            'H2OSingularValueDecompositionEstimator',
                            'H2OTargetEncoderEstimator']


def _order_estimator_mixins(cls, base=None, extra=None, type='estimator'):
    mixins = base or ()
    if type in ['estimator', 'classifier'] and _estimator_supports_predict_proba(cls):
        mixins += (H2OEstimatorPredictProbabilitiesSupport,)
    if _estimator_supports_score(cls):
        mixins += (H2OEstimatorScoreSupport,)
    if type in ['estimator', 'transformer'] and _estimator_supports_transform(cls):
        mixins += (H2OEstimatorTransformSupport,)
    if extra:
        mixins += extra
    return mixins


def make_estimator(cls, name=None, submodule=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Estimator'
    return estimator(cls, name=name, module=_register_submodule(submodule),
                     default_params=_make_default_params(cls),
                     mixins=_order_estimator_mixins(cls, type='estimator'),
                     is_generic=True,
                     custom_params=_get_custom_params(cls),
                     )


def make_classifier(cls, name=None, submodule=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Classifier'
    return estimator(cls, name=name, module=_register_submodule(submodule),
                     default_params=_make_default_params(cls),
                     mixins=_order_estimator_mixins(cls, extra=(ClassifierMixin,), type='classifier'),
                     is_generic=False,
                     custom_params=_get_custom_params(cls),
                     )


def make_regressor(cls, name=None, submodule=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Regressor'
    return estimator(cls, name=name, module=_register_submodule(submodule),
                     default_params=_make_default_params(cls),
                     mixins=_order_estimator_mixins(cls, extra=(RegressorMixin,), type='regressor'),
                     is_generic=False,
                     custom_params=_get_custom_params(cls),
                     )


def make_transformer(cls, name=None, submodule=None):
    if name is None:
        name = cls.__name__
    return transformer(cls, name=name, module=_register_submodule(submodule),
                       default_params=_make_default_params(cls),
                       custom_params=_get_custom_params(cls),
                       )


def h2o_connection(**init_args):
    """
    A context manager that can be used to create and close a connection to the H2O backend when required.

    :param init_args: custom arguments used to create the H2O connection if necessary.
        See :func:`h2o.init` for the list of available arguments.
    :return: a context manager providing a connection to the H2O backend.
    :example:

    >>> pipeline = Pipeline(...)  # pipeline using h2o.sklearn estimators
    ... with h2o_connection():
    ...     pipeline.fit(...)
    """
    conn_monitor = H2OConnectionMonitorMixin()
    conn_monitor._init_connection_args = init_args
    return conn_monitor


# Exceptions and generation of wrappers for standard H2O estimators #

_excluded_estimators = (  # e.g. abstract classes
    'H2OEstimator',
    'H2OTransformer',
)
_generic_only_estimators = (  # e.g. unsupervised and misc estimators
    'H2OAggregatorEstimator',
    'H2OAutoEncoderEstimator',
    'H2OGeneralizedLowRankEstimator',
    'H2OGenericEstimator',
    'H2OIsolationForestEstimator',
    'H2OKMeansEstimator',
    'H2OPrincipalComponentAnalysisEstimator',
    'H2OSingularValueDecompositionEstimator',
    'H2OTargetEncoderEstimator',
    'H2OWord2vecEstimator',
)
_classifier_only_estimators = ('H2ONaiveBayesEstimator',
                               'H2OSupportVectorMachineEstimator',)
_regressor_only_estimators = ('H2OCoxProportionalHazardsEstimator',)

gen_estimators = []
for mod in [automl, estimators]:
    submodule = mod.__name__.split('.')[-1]
    for name, cls in inspect.getmembers(mod, inspect.isclass):
        if name in _excluded_estimators:
            continue
        gen_estimators.append(make_estimator(cls, submodule=submodule))
        if name not in _generic_only_estimators:
            if name not in _regressor_only_estimators:
                gen_estimators.append(make_classifier(cls, submodule=submodule))
            if name not in _classifier_only_estimators:
                gen_estimators.append(make_regressor(cls, submodule=submodule))

for mod in [transforms]:
    submodule = mod.__name__.split('.')[-1]
    for name, cls in inspect.getmembers(mod, inspect.isclass):
        if name in _excluded_estimators:
            continue
        gen_estimators.append(make_transformer(cls, submodule=submodule))

__all__ = set('h2o_connection')
# exposes all the generated estimators in current module for ease of use.
# for clarity, it is still possible to import them from submodules
for e in gen_estimators:
    setattr(module, e.__name__, e)
    __all__.add(e.__name__)
__all__ = sorted(list(__all__))
