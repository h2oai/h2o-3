from collections import defaultdict, OrderedDict
import copy
from functools import partial, update_wrapper, wraps
import imp
import sys
from weakref import ref

from sklearn.base import is_classifier, is_regressor, BaseEstimator, TransformerMixin, ClassifierMixin, RegressorMixin

from .. import h2o, H2OFrame
from ..utils.shared_utils import can_use_numpy, can_use_pandas

try:
    from inspect import signature
except ImportError:
    from sklearn.utils.fixes import signature

if can_use_numpy():
    import numpy as np


def mixin(obj, *mixins):
    """

    :param obj: the object on which to apply the mixins
    :param mixins: the list of mixin classes to add to the object
    :return: the object with the mixins applied
    """
    obj.__class__ = type(obj.__class__.__name__, (obj.__class__,)+tuple(mixins), dict())
    return obj


class Mixin(object):
    """
    Context manager used to temporarily add mixins to an object.
    """

    def __init__(self, obj, *mixins):
        self._inst = mixin(copy.copy(obj), *mixins)  # no deepcopy necessary, we just want to ensure mixin methods are not added to original instance

    def __enter__(self):
        return self._inst

    def __exit__(self, *args):
        pass


def register_module(module_name):
    if module_name not in sys.modules:
        mod = imp.new_module(module_name)
        sys.modules[module_name] = mod
    return sys.modules[module_name]


def register_class(cls):
    module = register_module(cls.__module__)
    setattr(module, cls.__name__, cls)


def wrap_estimator(cls,
                   bases,
                   name=None,
                   module=None,
                   default_params=None,
                   is_generic=True,
                   custom_params=None):
    assert cls is not None
    assert isinstance(bases, tuple) and len(bases) > 0
    if default_params is None:
        # obtain the default params from signature of the estimator class constructor
        sig = signature(cls.__init__)
        default_params = OrderedDict((p.name, p.default if p.default is not p.empty else None)
                                     for p in sig.parameters.values())
        del default_params['self']

    gen_class_name = name if name else cls.__name__+'Sklearn'
    gen_class_module = module if module else __name__

    # generate the constructor signature for introspection by BaseEstimator (get_params)
    #  and also for auto-completion in some environments.
    def gen_init_code():
        yield "def init(self,"
        for k, v in default_params.items():
            yield "         {k}={v},".format(k=k, v=repr(v))
        yield "         init_connection_args=None,"
        yield "         data_conversion='auto',"
        if is_generic:  # if generic only
            yield "         estimator_type=None,"
        yield "         ):"
        yield "    base_args = locals()"
        yield "    del base_args['self']"
        yield "    base_args.update(estimator_cls=cls)"
        yield "    base_args.update(custom_params=custom_params)"
        yield "    super(self.__class__, self).__init__(**base_args)"
    init_code = '\n'.join(list(gen_init_code()))
    scope = locals()  # we can't create init in default scope, and we need to access some local variables
    exec(init_code, scope)
    init = scope['init']  # not necessary (init was created in local scope), but looks cleaner on editor

    extended = type(gen_class_name, bases, dict(
        __init__=init,
    ))
    extended.__module__ = gen_class_module
    register_class(extended)
    return extended


def estimator(cls, name=None, module=None, default_params=None, mixins=None, is_generic=True, custom_params=None):
    mixins = tuple(mixins) if mixins else ()
    return wrap_estimator(cls=cls,
                          bases=(H2OtoSklearnEstimator,) + mixins,
                          name=name,
                          module=module,
                          default_params=default_params,
                          is_generic=is_generic,
                          custom_params=custom_params,
                          )


def transformer(cls, name=None, module=None, default_params=None, mixins=None, custom_params=None):
    mixins = tuple(mixins) if mixins else ()
    return wrap_estimator(cls=cls,
                          bases=(H2OtoSklearnTransformer,) + mixins,
                          name=name,
                          module=module,
                          default_params=default_params,
                          custom_params=custom_params)


def _to_h2o_frame(X, as_factor=False, frame_params=None, **kwargs):
    if X is None:
        return X
    if isinstance(X, H2OFrame):
        return X.asfactor() if as_factor else X

    if not frame_params:
        frame_params = {}
    fr = H2OFrame(X, **frame_params)
    return fr.asfactor() if as_factor else fr


def _to_numpy(fr, **kwargs):
    """
    :param fr:
    :return:
    """
    if fr is None:
        return fr
    arr = fr
    if isinstance(fr, H2OFrame):
        arr = fr.as_data_frame()
        if can_use_pandas():
            arr = arr.values
    return (np.array(arr) if can_use_numpy() and isinstance(arr, list)
            else arr)


def _vector_to_1d_array(fr, estimator=estimator, **kwargs):
    if isinstance(estimator, BaseSklearnEstimator) and estimator._custom_params.get('predictions_col', 0) == 'all':
        return _to_numpy(fr)

    assert fr.ncol == 1 or fr.nrow == 1, "Only vectors can be converted to 1d array."
    return _to_numpy(fr).reshape(-1)


def _convert(converter, arg, arguments, **converter_args):
    ori = arguments.get(arg, None)
    new = converter(ori, **converter_args)
    converted = new is not ori
    arguments[arg] = new
    return converted


def _revert(converter,
            result,
            converted=False,
            result_conversion=None,
            estimator=None,
            **converter_args):
    """
    sklearn toolkit produces all its results as numpy format by default.
    However here, as we need to convert inputs to H2OFrames,
    and as we may chain H2O transformers and estimators,
    then we apply some detection logic to return results in the same format as input by default.
    This can be overridden at decorator level for specific methods (e.g. transform)
    or at estimator level for other cases (predict on a pipeline with H2O transformers and numpy inputs).
    :param result:
    :param converted:
    :param result_conversion:
    :return:
    """
    estimator_conversion = getattr(estimator, 'data_conversion', None)
    do_convert = (
            estimator_conversion is True
            or (estimator_conversion in (None, 'auto')
                and (result_conversion is True
                     or (result_conversion in (None, 'auto') and converted)
                     )
                )
    )
    return converter(result, estimator=estimator, **converter_args) if do_convert else result


def params_as_h2o_frames(frame_params=('X', 'y'),
                         target_param='y',
                         result_conversion='auto',
                         converter=_to_h2o_frame,
                         reverter=_to_numpy):
    """
    :param frame_params:
    :param target_param:
    :param result_conversion:
    :param converter:
    :param reverter:
    :return:
    """
    assert result_conversion in ('auto', True, False)

    def decorator(fn):
        """
        A decorator that can be applied to estimator methods, to support the consumption of non-H2OFrame datasets
        :param fn: the function to be decorated
        :return: a new function that will convert X, and y parameters before passing them to the original function.
        """
        sig = signature(fn)
        has_self = 'self' in sig.parameters
        assert any(arg in sig.parameters for arg in frame_params), \
            "@expect_h2o_frames decorator should be applied to methods taking at least one of {} parameters.".format(frame_params)

        @wraps(fn)
        def wrapper(*args, **kwargs):
            """
            the logic executed before fn: converts params to H2OFrame if needed
            and possibly backwards before returning result
            :param args:
            :param kwargs:
            :return:
            """
            _args = sig.bind(*args, **kwargs).arguments
            classifier = False
            self = {}
            frame_info = None
            if has_self:
                self = _args.get('self', None)
                if isinstance(self, H2OConnectionMonitorMixin):
                    self.__enter__()
                if isinstance(self, BaseEstimatorMixin):
                    classifier = self.is_classifier()
                    frame_info = getattr(self, '_frame_params')

            if hasattr(self, '_before_method') and callable(self._before_method):
                self._before_method(fn=fn, input=_args)

            converted = False
            for fr in frame_params:
                if fr in sig.parameters:
                    as_factor = classifier and fr == target_param
                    frame_info = frame_info if fr != target_param else None
                    converted = _convert(converter, fr, _args,
                                         as_factor=as_factor, frame_params=frame_info,
                                         estimator=self) or converted

            result = fn(**_args)
            result = _revert(reverter, result, converted=converted,
                             result_conversion=result_conversion,
                             estimator=self)

            if hasattr(self, '_after_method') and callable(self._after_method):
                self._after_method(fn=fn, output=result)

            return result

        return wrapper

    return decorator


class BaseEstimatorMixin(object):

    _classifier_distributions = ('bernoulli', 'binomial', 'quasibinomial', 'multinomial')

    def _is_classifier_distribution(self):
        return hasattr(self, 'distribution') and getattr(self, 'distribution') in self._classifier_distributions

    def _is_regressor_distribution(self):
        return hasattr(self, 'distribution') and getattr(self, 'distribution') not in (None,)+self._classifier_distributions

    def is_classifier(self):
        return is_classifier(self) or self._is_classifier_distribution()

    def is_regressor(self):
        return is_regressor(self) or self._is_regressor_distribution()


class H2OConnectionMonitorMixin(object):
    """ Mixin that automatically handles the connection to H2O backend"""

    _h2o_initialized_here = False
    _h2o_components_refs = []

    @classmethod
    def init_connection(cls, show_progress=True, **kwargs):
        """
        initialize the H2O connection if needed
        and track the current instance to be able to automatically close the connection
        when there's no more instances in scope
        """
        if not (h2o.connection() and h2o.connection().connected):
            h2o.init(**kwargs)
            cls._h2o_initialized_here = True
            if show_progress:
                h2o.show_progress()
            else:
                h2o.no_progress()

    @classmethod
    def close_connection(cls, force=False):
        if (force or cls._h2o_initialized_here) and h2o.connection():
            if h2o.connection().local_server:
                cls.shutdown_cluster()
            else:
                h2o.connection().close()
        cls._h2o_initialized_here = False

    @classmethod
    def shutdown_cluster(cls):
        """
        force H2O cluster shutdown
        """
        if h2o.cluster():
            local_server = h2o.connection() and h2o.connection().local_server
            h2o.cluster().shutdown()
            if local_server:
                local_server.shutdown()

    @classmethod
    def _add_component(cls, component):
        has_component = next((cr() for cr in cls._h2o_components_refs if cr() is component), None)
        if not has_component:
            cls._h2o_components_refs.append(ref(component, cls._remove_component_ref))
            return True
        return False

    @classmethod
    def _remove_component(cls, component):
        comp_ref = next((cr for cr in cls._h2o_components_refs if cr() is component), None)
        if comp_ref:
            cls._remove_component_ref(comp_ref)
            return True
        return False

    @classmethod
    def _remove_component_ref(cls, component_ref):
        """
        remove tracking reference to current h2o component
        and close the connection (or the local server) if there's no more tracked component.
        """
        # first removing possible refs to None
        # cls._h2o_components_refs = [cr for cr in cls._h2o_components_refs if cr()]
        if component_ref in cls._h2o_components_refs:
            cls._h2o_components_refs.remove(component_ref)
        if not cls._h2o_components_refs:
            cls.close_connection()

    def __enter__(self):
        if self._add_component(self):
            self.init_connection(**(getattr(self, '_init_connection_args') or {}))
        return self

    def __exit__(self, *args):
        self._remove_component(self)

    def __del__(self):
        self.__exit__()


class BaseSklearnEstimator(BaseEstimator, BaseEstimatorMixin, H2OConnectionMonitorMixin):

    # following params are necessary when sklearn is cloning the estimator,
    # but should not be passed to original H2O estimator
    _reserved_params = ('data_conversion',)

    def __init__(self,
                 estimator_cls=None,
                 estimator_type=None,
                 init_connection_args=None,
                 custom_params=None,
                 **estimator_params):
        """
        :param estimator_cls: the H2O Estimator class.
        :param estimator_type: if provided, must be one of ('classifier', 'regressor').
        :param data_conversion: if provided, must be one of ('auto', 'array', 'numpy', 'pandas', 'h2o')
        :param init_connection_args: the arguments passed to `h2o.init()` if there's no connection to H2O backend.
        :param estimator_params: the estimator/model parameters.
        """
        super(BaseSklearnEstimator, self).__init__()
        assert estimator_type in (None, 'classifier', 'regressor')
        self._estimator = None
        self._estimator_cls = estimator_cls
        if estimator_type:
            self._estimator_type = estimator_type

        # we only keep a ref to parameters names
        # all those params are also exposed as a regular attribute and can be modified directly
        #  on the estimator instance
        self._estimator_param_names = estimator_params.keys()
        self.set_params(**estimator_params)

        self._custom_params = custom_params or {}
        self._frame_params = None
        self._init_connection_args = init_connection_args


    @classmethod
    def _no_conflict(cls, param):
        # return param
        return param+'_' if param in dir(cls) else param

    def set_params(self, **params):
        """
        Override BaseEstimator logic to solve the conflict issue we have with some estimator parameters
        like `transform` that conflict with method names in sklearn context.
        :param params:
        :return:
        """
        if not params:
            return self
        valid_params = self.get_params(deep=True)

        nested_params = defaultdict(dict)  # grouped by prefix
        for key, value in params.items():
            key, delim, sub_key = key.partition('__')
            if key not in valid_params:
                raise ValueError("Invalid parameter %s for estimator %s. "
                                 "Check the list of available parameters "
                                 "with `estimator.get_params().keys()`."
                                 % (key, self))

            if delim:
                nested_params[key][sub_key] = value
            else:
                setattr(self, self._no_conflict(key), value)

        for key, sub_params in nested_params.items():
            valid_params[key].set_params(**sub_params)

        return self

    def get_params(self, deep=True):
        """
        Override BaseEstimator logic to solve the conflict issue we have with some estimator parameters
        like `transform` that conflict with method names in sklearn context.
        :param deep:
        :return:
        """
        params = {k: getattr(self, self._no_conflict(k), None) for k in self._estimator_param_names}
        if deep:
            out = dict()
            for k, v in params.items():
                if hasattr(v, 'get_params'):
                    deep_items = v.get_params().items()
                    out.update((k+'__'+key, val) for key, val in deep_items)
            params.update(out)
        return params

    def _make_estimator(self):
        params = {k: v for k, v in self.get_params().items() if k not in self._reserved_params}
        self._estimator = self._estimator_cls(**params)
        return self._estimator

    def _extract_frame_params(self, X):
        self._frame_params = dict(
            column_names=X.columns,
            column_types=X.types,
        )

    # def __str__(self):
    #     return str(self._estimator) if self._estimator else super(BaseSklearnEstimator, self).__str__()



class H2OtoSklearnEstimator(BaseSklearnEstimator):

    def __init__(self, *args, **kwargs):
        super(H2OtoSklearnEstimator, self).__init__(*args, **kwargs)

    @params_as_h2o_frames()
    def fit(self, X, y=None, **fit_params):
        self._extract_frame_params(X)
        self._make_estimator()
        training_frame = X if y is None else X.concat(y)
        self._estimator.train(y=-1, training_frame=training_frame, **fit_params)
        return self

    def _predict(self, X):
        return self._estimator.predict(X)

    @params_as_h2o_frames(reverter=_vector_to_1d_array)
    def predict(self, X):
        pred_col = self._custom_params.get('predictions_col', 0)
        preds = self._predict(X)
        return preds if pred_col == 'all' else preds[:, pred_col]

    @params_as_h2o_frames()
    def predict_proba(self, X):
        if self.is_classifier() and self._custom_params.get('predict_proba', True):
            pred_col = self._custom_params.get('predictions_col', 0)
            preds = self._predict(X)
            selector = [c for c in range(preds.ncol) if c != pred_col]
            return preds[:, selector]
        raise AttributeError("No `predict_proba` method in {}".format(self.__class__.__name__))

    def predict_log_proba(self, X):
        if self.is_classifier() and can_use_numpy():
            return np.log(self.predict_proba(X))
        raise AttributeError("No `predict_log_proba` method in {}".format(self.__class__.__name__))

    @params_as_h2o_frames()
    def score(self, X, y=None, sample_weight=None):
        if hasattr(self._estimator, 'score') and callable(self._estimator.score):
            return self._estimator.score(X, y=y, sample_weight=sample_weight)
        elif self._custom_params.get('score', True):
            def delegate_score(delegate):
                # suboptimal: X may have been converted to H2O frame for nothing
                #  however for now, it makes implementation simpler
                return delegate.score(_to_numpy(X), y=(_vector_to_1d_array(y)), sample_weight=sample_weight)

            # delegate to default sklearn scoring methods
            parent = super(H2OtoSklearnEstimator, self)
            if hasattr(parent, 'score') and callable(parent.score):
                return delegate_score(parent)
            elif self.is_classifier():
                with Mixin(self, ClassifierMixin) as delegate:
                    return delegate_score(delegate)
            elif self.is_regressor():
                with Mixin(self, RegressorMixin) as delegate:
                    return delegate_score(delegate)
        raise AttributeError("No `score` method in {}".format(self.__class__.__name__))


class H2OtoSklearnTransformer(BaseSklearnEstimator, TransformerMixin):

    def __init__(self, *args, **kwargs):
        super(H2OtoSklearnTransformer, self).__init__(*args, **kwargs)

    @params_as_h2o_frames()
    def fit(self, X, y=None, **fit_params):
        self._extract_frame_params(X)
        self._make_estimator()
        return self._estimator.fit(X, y=y, **fit_params)

    @params_as_h2o_frames(result_conversion=False)
    def transform(self, X):
        return self._estimator.transform(X)

    @params_as_h2o_frames(result_conversion=False)
    def fit_transform(self, X, y=None, **fit_params):
        if hasattr(self._estimator, 'fit_transform') and callable(self._estimator.fit_transform):
            self._make_estimator()
            return self._estimator.fit_transform(X, y=y, **fit_params)
        else:
            return super(H2OtoSklearnTransformer, self).fit_transform(X, y=y, **fit_params)

    @params_as_h2o_frames(result_conversion=False)
    def inverse_transform(self, X):
        if hasattr(self._estimator, 'inverse_transform') and callable(self._estimator.inverse_transform):
            return self._estimator.inverse_transform(X)
        raise AttributeError("No `inverse_transform` method in {}".format(self.__class__.__name__))
