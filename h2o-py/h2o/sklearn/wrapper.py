"""
Provides all the logic to construct the `sklearn` wrappers from standard H2O estimators or transformers.
The only requirements from the original estimator are the following:
 - it must have a `train` method (equivalent of  `sklearn` `fit` method).
 - `train` must accept `h2o.H2OFrame` as `training_frame` param.

"""
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
    Function adding one or more mixin class to the list of parent classes of the current object.
    This is done by dynamically changing the class hierarchy of the current object,
    not only by adding the mixin methods to the object.

    :param obj: the object on which to apply the mixins.
    :param mixins: the list of mixin classes to add to the object.
    :return: the extended object.
    """
    obj.__class__ = type(obj.__class__.__name__, (obj.__class__,)+tuple(mixins), dict())
    return obj


class Mixin(object):
    """
    Context manager used to temporarily add mixins to an object.
    """

    def __init__(self, obj, *mixins):
        """
        :param obj: the object on which the mixins are temporarily added.
        :param mixins: the list of mixins to apply.
        """
        self._inst = mixin(copy.copy(obj), *mixins)  # no deepcopy necessary, we just want to ensure mixin methods are not added to original instance

    def __enter__(self):
        if hasattr(self._inst, '__enter__'):
            return self._inst.__enter__()
        return self._inst

    def __exit__(self, *args):
        if hasattr(self._inst, '__exit__'):
            self._inst.__exit__()


def register_module(module_name):
    """
    Creates and globally registers a module with given name.

    :param module_name: the name of the module to register.
    :return: the module with given name.
    """
    if module_name not in sys.modules:
        mod = imp.new_module(module_name)
        sys.modules[module_name] = mod
    return sys.modules[module_name]


def register_class(cls):
    """
    Register a class module, and adds it to it's module.

    :param cls: the class to register.
    """
    module = register_module(cls.__module__)
    setattr(module, cls.__name__, cls)


def wrap_estimator(cls,
                   bases,
                   name=None,
                   module=None,
                   default_params=None,
                   is_generic=True,
                   custom_params=None):
    """
    Creates a `sklearn`-compatible wrapper class from an H2O class.

    :param type cls: the class of the original estimator.
    :param list[type] bases: the base classes used to generate the wrapper.
    :param str name: the name of the generated class (without module).
        If None, then `cls.__name__ + 'Sklearn'` is used.
    :param str module: the module name of the generated class.
        If None, then the current module is used (not the original class module to avoid unintentional module pollution).
    :param dict default_params: a dictionary listing the default parameters used when constructing a new instance of the wrapper.
        If None, then the default params are obtained by parsing the constructor of the original class.
    :param bool is_generic: if True then the generated wrapper will have an additional `estimator_type` parameter
        accepting values (`classifier`, `regressor`) to offer the possibility to enforce certain features
        like auto-conversion of target to categoricals for classification problems.
    :param dict custom_params: a dictionary providing hooks to customize the behaviour of the generated wrapper.
    :return: a wrapper class for the given class that is `sklearn` compatible.
    """
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
    """
    A slightly simpler method on top of :func:`wrap_estimator` to generate `Estimator` wrapper classes.
    """
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
    """
    A slightly simpler method on top of :func:`wrap_estimator` to generate `Transformer` wrapper classes.
    """
    mixins = tuple(mixins) if mixins else ()
    return wrap_estimator(cls=cls,
                          bases=(H2OtoSklearnTransformer,) + mixins,
                          name=name,
                          module=module,
                          default_params=default_params,
                          custom_params=custom_params)


def _to_h2o_frame(X, as_factor=False, frame_params=None, **kwargs):
    """
    Converts X to an :class:`h2o.H2OFrame`
    """
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
    Converts given frame to a :class:`numpy.ndarray`.
    If numpy is not available (extremely unlikely in sklearn context...), this will just return a multidimensional list.
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
    """
    Converts the given frame (expected to be a vector frame) to a 1-dimensional numpy array.
    This default behaviour can be avoided using custom hooks: see implementation details.
    """
    if isinstance(estimator, BaseEstimatorMixin) and estimator._get_custom_param('predictions_col') == 'all':
        return _to_numpy(fr, **kwargs)

    assert fr.ncol == 1 or fr.nrow == 1, "Only vectors can be converted to 1d array."
    return _to_numpy(fr, **kwargs).reshape(-1)


def _convert(converter, arg, arguments, **converter_args):
    """
    Converts inplace the `arg` param of `arguments` using the given `converter`.
    Used internally to convert some methods parameters (usually to H2OFrame).
    """
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
    Use the `converter` to convert the given `result` if necessary.

    sklearn toolkit produces all its results as numpy format by default.
    However here, as we need to convert inputs to H2OFrames,
    and as we may chain H2O transformers and estimators,
    then we apply some detection logic to return results in the same format as input by default.
    This can be overridden at decorator level for specific methods (e.g. transform)
    or at estimator level for other cases (predict on a pipeline with H2O transformers and numpy inputs).
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
    Parametrized decorator function that allow to automatically convert some function params and the function result.
    The converted params and the conversion logic is customizable thanks to the following parameters:

    :param tuple[str] frame_params: the list of param names that need to be converted (by `converter`).
    :param str target_param: the name of the target param.
        This allows to apply special logic on this param, like the auto-conversion to categoricals.
    :param str result_conversion: the conversion policy for the result from the wrapped function.
        Must be one of (None, `auto`, True, False). Defaults to `auto`.
        - None and `auto` use some detection logic to convert the result back only if the params were themselves converted.
        - True will always convert the result.
        - False will disable result conversion.
    :param function converter: the converter used to convert the function params.
    :param function reverter: the converter used to convert the function result.
    :return: the decorated function.
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
            "@params_as_h2o_frames decorator should be applied to methods taking at least one of {} parameters.".format(frame_params)

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
                    frame_info = getattr(self, '_frame_params', None)

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
    """
    A simply mixin providing methods to identify if the estimator is a classifier or a regressor,
    as well as access to custom params changing the default behaviour of the sklearn wrappers.
    """

    _classifier_distributions = ('bernoulli', 'binomial', 'quasibinomial', 'multinomial')
    """ the names of the distributions that will identify the estimator as a classifier """

    def _get_custom_param(self, name, default=None):
        return self._custom_params.get(name, default) if hasattr(self, '_custom_params') else default

    def _is_classifier_distribution(self):
        distribution_prop = self._get_custom_param('distribution_param', 'distribution')
        return hasattr(self, distribution_prop) and getattr(self, distribution_prop) in self._classifier_distributions

    def _is_regressor_distribution(self):
        distribution_prop = self._get_custom_param('distribution_param', 'distribution')
        return hasattr(self, distribution_prop) and getattr(self, distribution_prop) not in (None,)+self._classifier_distributions

    def is_classifier(self):
        return is_classifier(self) or self._is_classifier_distribution()

    def is_regressor(self):
        return is_regressor(self) or self._is_regressor_distribution()


class H2OConnectionMonitorMixin(object):
    """
    Mixin that automatically handles the connection to H2O backend.
    This mixin can also be used as a Python context manager to obtain a connection to the H2O backend.
    """

    _h2o_connection_ref = None
    """
    True iff one component inheriting from this mixin had to create the connection to the backend.
    False if the connection was created upfront.
    """
    _h2o_components_refs = []
    """
    A list of weak references to the components that asked for a connection.
    Those references are automatically removed once the component is destroyed.
    Once this list gets empty and if the connection was created by this mixin, then it is automatically closed.
    If the creation of the connection also required the creation of a local server instance, this one is also shutdown. 
    """

    @classmethod
    def init_connection(cls, auto_close=True, show_progress=True, **kwargs):
        """
        Initialize the H2O connection if needed and track the current instance
        to be able to automatically close the connection when there's no more components in scope
        that require this connection.
        See :func:`h2o.init` for the list of allowed parameters.

        :param: bool auto_close: if True (default) the H2O connection (and possibly the local server if started for this connection)
        are automatically closed once there's no more components in scope requiring this connection.
        :param: bool show_progress: if True (default) the H2O progress bars are always rendered.
        """
        if not (h2o.connection() and h2o.connection().connected):
            try:
                h2o.init(**kwargs)
                conn = h2o.connection()
                if auto_close:
                    H2OConnectionMonitorMixin._h2o_connection_ref = ref(conn)
                # conn.start_logging()
            except:
                cls.close_connection()  # ensure that the connection is properly closed on error
                raise

            if show_progress:
                h2o.show_progress()
            else:
                h2o.no_progress()

    @classmethod
    def close_connection(cls, force=False):
        """
        Close the connection if needed.

        :param force: if True, the connection is closed regardless of its origin.
        """
        conn = h2o.connection()
        if conn and (force or cls._is_current_connection(conn)):
            if h2o.connection().local_server:
                cls.shutdown_cluster()
            else:
                conn.close()
        H2OConnectionMonitorMixin._h2o_connection_ref = None

    @staticmethod
    def _is_current_connection(conn):
        return conn and H2OConnectionMonitorMixin._h2o_connection_ref and conn is H2OConnectionMonitorMixin._h2o_connection_ref()

    @classmethod
    def shutdown_cluster(cls):
        """
        Force H2O cluster shutdown.
        """
        if h2o.cluster():
            local_server = h2o.connection() and h2o.connection().local_server
            h2o.cluster().shutdown()
            if local_server:
                local_server.shutdown()

    @classmethod
    def _add_component(cls, component):
        """Track the given component by keeping a weak reference and registering a callback executed on the component destruction."""
        has_component = next((cr() for cr in H2OConnectionMonitorMixin._h2o_components_refs if cr() is component), None)
        if not has_component:
            H2OConnectionMonitorMixin._h2o_components_refs.append(ref(component, cls._remove_component_ref))
            return True
        return False

    @classmethod
    def _remove_component(cls, component):
        """Remove the weak reference of the given component"""
        comp_ref = next((cr for cr in H2OConnectionMonitorMixin._h2o_components_refs if cr() is component), None)
        if comp_ref:
            cls._remove_component_ref(comp_ref)
            return True
        return False

    @classmethod
    def _remove_component_ref(cls, component_ref):
        """
        Remove the tracking reference to the current h2o component
        and close the connection if there's no more tracked component.
        """
        # first removing possible refs to None
        # cls._h2o_components_refs = [cr for cr in cls._h2o_components_refs if cr()]
        if component_ref in H2OConnectionMonitorMixin._h2o_components_refs:
            H2OConnectionMonitorMixin._h2o_components_refs.remove(component_ref)
        if not H2OConnectionMonitorMixin._h2o_components_refs:
            cls.close_connection()

    def __enter__(self):
        try:
            if self._add_component(self):
                self.init_connection(**(getattr(self, '_init_connection_args') or {}))
        except:
            self.__exit__()
            raise
        return self

    def __exit__(self, *args):
        self._remove_component(self)

    def __del__(self):
        self.__exit__()


class BaseSklearnEstimator(BaseEstimator, BaseEstimatorMixin, H2OConnectionMonitorMixin):

    _reserved_params = ('data_conversion',)
    """Params required when `sklearn` is cloning the estimator (e.g. in search estimators),
    but that should not be forwarded to the original wrapped estimator"""

    def __init__(self,
                 estimator_cls=None,
                 estimator_type=None,
                 init_connection_args=None,
                 custom_params=None,
                 **estimator_params):
        """
        The base wrapper class exposing `sklearn` common methods.

        :param estimator_cls: the H2O Estimator class.
        :param estimator_type: if provided, must be one of ('classifier', 'regressor').
        :param data_conversion: if provided, must be one of ('auto', 'array', 'numpy', 'pandas', 'h2o')
        :param init_connection_args: the arguments passed to `h2o.init()` if there's no connection to H2O backend.
        :param estimator_params: the estimator/model parameters.
        """
        super(BaseSklearnEstimator, self).__init__()
        self._custom_params = custom_params or {}

        assert estimator_type in (None, 'classifier', 'regressor')
        self._estimator = None
        self._estimator_cls = estimator_cls
        estimator_type = self._get_custom_param('default_estimator_type', estimator_type)
        if estimator_type:
            self._estimator_type = estimator_type

        # we only keep a ref to parameters names
        # all those params are also exposed as a regular attribute and can be modified directly
        #  on the estimator instance
        self._estimator_param_names = list(estimator_params.keys())
        self.set_params(**estimator_params)

        self._frame_params = None
        self._init_connection_args = init_connection_args


    @classmethod
    def _no_conflict(cls, param):
        """
        :return: `param`, or, if the param name may conflict with another method or property on the instance,`param_`
        """
        return param+'_' if param in dir(cls) else param

    @property
    def estimator(self):
        """
        The wrapped estimator is created only when the current object is `fit`, so this property returns None until then.
        :return: the wrapped estimator or None if `fit` hasn't been called on the current object yet.
        """
        return self._estimator

    def set_params(self, **params):
        """Set the parameters of this estimator.

        The method works on simple estimators as well as on nested objects
        (such as pipelines). The latter have parameters of the form
        ``<component>__<parameter>`` so that it's possible to update each
        component of a nested object.

        Override BaseEstimator logic to solve the conflict issue we have with some estimator parameters
        like `transform` that conflict with method names in sklearn context.
        :param params:
        :return: self
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
        """Get parameters for this estimator.

        Override BaseEstimator logic to solve the conflict issue we have with some estimator parameters
        like `transform` that conflict with method names in sklearn context.

        :param bool deep: Optional. If True, will return the parameters for this estimator
            and contained subobjects that are estimators.
        :return: Parameter names mapped to their values.
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
        """Instantiate a new underlying estimator using the wrapper params."""
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

    def show(self):
        if hasattr(self._estimator, 'show') and callable(self._estimator.show):
            self._estimator.show()
        else:
            print(self)



class H2OtoSklearnEstimator(BaseSklearnEstimator):
    """
    The base wrapper class exposing `sklearn` estimator methods.
    """

    def __init__(self, *args, **kwargs):
        """Note: the real constructor signature is defined dynamically by :func:`wrap_estimator` based on the default params."""
        super(H2OtoSklearnEstimator, self).__init__(*args, **kwargs)

    def _fit(self, X, y=None, **fit_params):
        self._extract_frame_params(X)
        self._make_estimator()
        if self._get_custom_param('_fit'):
            self._get_custom_param('_fit')(self, X, y=y, **fit_params)
        else:
            training_frame = X if y is None else X.concat(y)
            target = None if y is None else -1
            self._estimator.train(y=target, training_frame=training_frame, **fit_params)
        return self

    def _predict(self, X):
        return self._estimator.predict(X)

    @params_as_h2o_frames()
    def fit(self, X, y=None, **fit_params):
        """Fit the model.

        Fit all the transforms one after the other and transform the
        data, then fit the transformed data using the final estimator.

        :param iterable X: training data (array-like or :class:`h2o.H2OFrame`).
        :param iterable y: training target (array-like or :class:`h2o.H2OFrame`), (default is None).
        :param fit_params: parameters passed to the underlying `train` method of the :class:`h2o.estimators.H2OEstimator`.
        :return: self
        """
        return self._fit(X, y, **fit_params)

    @params_as_h2o_frames(reverter=_vector_to_1d_array)
    def fit_predict(self, X, y=None, **fit_params):
        """Fit the model and predict on the input data.

        :param iterable X: training data (array-like or :class:`h2o.H2OFrame`).
        :param iterable y: training target (array-like or :class:`h2o.H2OFrame`), (default is None).
        :param fit_params: parameters passed to the underlying `train` method of the :class:`h2o.estimators.H2OEstimator`.
        :return: the predictions on X (array-like or :class:`h2o.H2OFrame`).
        """
        return self._fit(X, y, **fit_params).predict(X)

    @params_as_h2o_frames(reverter=_vector_to_1d_array)
    def predict(self, X):
        """Predicts on the data.

        :param iterable X: data to predict on (array-like or :class:`h2o.H2OFrame`).
        :return: the predictions (array-like or :class:`h2o.H2OFrame`).
        """
        preds = self._predict(X)
        pred_col = self._get_custom_param('predictions_col', 0)
        return preds if pred_col == 'all' else preds[:, pred_col]


class H2OtoSklearnTransformer(BaseSklearnEstimator, TransformerMixin):
    """
    The base wrapper class exposing `sklearn` transformer (a.k.a. preprocessor) methods.
    """

    def __init__(self, *args, **kwargs):
        """Note: the real constructor signature is defined dynamically by :func:`wrap_estimator` based on the default params."""
        super(H2OtoSklearnTransformer, self).__init__(*args, **kwargs)

    @params_as_h2o_frames()
    def fit(self, X, y=None, **fit_params):
        """Fit the model.

        Fit all the transforms one after the other and transform the
        data, then fit the transformed data using the final estimator.

        :param iterable X: training data (array-like or :class:`h2o.H2OFrame`).
        :param iterable y: training target (array-like or :class:`h2o.H2OFrame`) (default is None).
        :param fit_params: parameters passed to the underlying `train` method of the :class:`h2o.estimators.H2OEstimator`.
        :return: self
        """
        self._extract_frame_params(X)
        self._make_estimator()
        return self._estimator.fit(X, y=y, **fit_params)

    @params_as_h2o_frames(result_conversion=False)
    def transform(self, X):
        """Transform the data on the fitted model.

        Note that it doesn't convert result back to numpy by default as it is intended to be chained
        with another H2O transformer or estimator.
        The transformer should be instantiated with `data_conversion=True` to always obtain numpy objects as results.

        :param iterable X: data to transform (array-like or :class:`h2o.H2OFrame`).
        :return: transformed data (:class:`h2o.H2OFrame` by default).
        """
        return self._estimator.transform(X)

    @params_as_h2o_frames(result_conversion=False)
    def fit_transform(self, X, y=None, **fit_params):
        """Fit the model and apply transform on the fitted model.

        Note that it doesn't convert result back to numpy by default as it is intended to be chained
        with another H2O transformer or estimator.
        The transformer should be instantiated with `data_conversion=True` to always obtain numpy objects as results.

        :param iterable X: training data (array-like or :class:`h2o.H2OFrame`).
        :param iterable y: training target (array-like or :class:`h2o.H2OFrame`) (default is None).
        :param fit_params: parameters passed to the underlying `train` method of the :class:`h2o.estimators.H2OEstimator`.
        :return: transformed data (:class:`h2o.H2OFrame` by default).
        """
        if hasattr(self._estimator, 'fit_transform') and callable(self._estimator.fit_transform):
            self._make_estimator()
            return self._estimator.fit_transform(X, y=y, **fit_params)
        else:
            return super(H2OtoSklearnTransformer, self).fit_transform(X, y=y, **fit_params)

    @params_as_h2o_frames(result_conversion=False)
    def inverse_transform(self, X):
        """Apply reverse transformation.

        Note that it doesn't convert result back to numpy by default as it is intended to be chained
        with another H2O transformer or estimator.
        The transformer should be instantiated with `data_conversion=True` to always obtain numpy objects as results.

        :param iterable X: data to transform (array-like or :class:`h2o.H2OFrame`).
        :return: transformed data (:class:`h2o.H2OFrame` by default).
        """
        if hasattr(self._estimator, 'inverse_transform') and callable(self._estimator.inverse_transform):
            return self._estimator.inverse_transform(X)
        raise AttributeError("{} does not support 'inverse_transform'.".format(self.__class__.__name__))



class H2OEstimatorPredictProbabilitiesSupport(BaseEstimatorMixin):

    @params_as_h2o_frames()
    def predict_proba(self, X):
        """Predicts on the data.

        :param iterable X: data to predict on (array-like or :class:`h2o.H2OFrame`).
        :return: the predictions probabilities, shape=[n_samples, n_classes] (array-like or :class:`h2o.H2OFrame`).
        """
        if self.is_classifier():
            preds = self._predict(X)
            pred_col = self._get_custom_param('predictions_col', 0)
            selector = [c for c in range(preds.ncol) if c != pred_col]
            return preds[:, selector]
        raise AttributeError("{} attribute 'predict_proba' is supported only for classification.".format(self.__class__.__name__))

    def predict_log_proba(self, X):
        """Predicts on the data.

        :param iterable X: data to predict on (array-like or :class:`h2o.H2OFrame`).
        :return: the predictions log-probabilities, shape=[n_samples, n_classes] (array-like or :class:`h2o.H2OFrame`).
        """
        if self.is_classifier() and can_use_numpy():
            return np.log(self.predict_proba(X))
        raise AttributeError("{} attribute 'predict_proba' is supported only for classification.".format(self.__class__.__name__))


class H2OEstimatorScoreSupport(BaseEstimatorMixin):

    @params_as_h2o_frames()
    def score(self, X, y=None, sample_weight=None):
        """Predicts on the data and score the predictions using by default:
            - :func:`sklearn.metrics.accuracy_score` for classification estimators.
            - :func:`sklearn.metrics.r2_score` for regression estimators.

        :param iterable X: data to predict on (array-like or :class:`h2o.H2OFrame`).
        :param iterable y: true target values used for scoring (array-like or :class:`h2o.H2OFrame`) (default is None).
        :param sample_weight: passed to the score function.
        :return:
        """
        if hasattr(self._estimator, 'score') and callable(self._estimator.score):
            return self._estimator.score(X, y=y, sample_weight=sample_weight)

        def delegate_score(delegate):
            # suboptimal: X may have been converted to H2O frame for nothing
            #  however for now, it makes implementation simpler
            return delegate.score(_to_numpy(X), y=(_vector_to_1d_array(y)), sample_weight=sample_weight)

        # delegate to default sklearn scoring methods
        parent = super(H2OEstimatorScoreSupport, self)
        if hasattr(parent, 'score') and callable(parent.score):
            return delegate_score(parent)
        elif self.is_classifier():
            with Mixin(self, ClassifierMixin) as delegate:
                return delegate_score(delegate)
        elif self.is_regressor():
            with Mixin(self, RegressorMixin) as delegate:
                return delegate_score(delegate)


class H2OEstimatorTransformSupport(BaseSklearnEstimator, TransformerMixin):

    @params_as_h2o_frames(result_conversion=False)
    def fit_transform(self, X, y=None, **fit_params):
        """Fit the model and apply transform on the fitted model.

        Note that it doesn't convert result back to numpy by default as it is intended to be chained
        with another H2O transformer or estimator.
        The transformer should be instantiated with `data_conversion=True` to always obtain numpy objects as results.

        :param iterable X: training data (array-like or :class:`h2o.H2OFrame`).
        :param iterable y: training target (array-like or :class:`h2o.H2OFrame`) (default is None).
        :param fit_params: parameters passed to the underlying `train` method of the :class:`h2o.estimators.H2OEstimator`.
        :return: transformed data (:class:`h2o.H2OFrame` by default).
        """
        if hasattr(self._estimator, 'fit_transform') and callable(self._estimator.fit_transform):
            self._make_estimator()
            return self._estimator.fit_transform(X, y=y, **fit_params)
        else:
            return self._fit(X, y=y, **fit_params).transform(X)

    @params_as_h2o_frames(result_conversion=False)
    def transform(self, X, **transform_params):
        """Transform the data on the fitted model.

        Note that it doesn't convert result back to numpy by default as it is intended to be chained
        with another H2O transformer or estimator.
        The transformer should be instantiated with `data_conversion=True` to always obtain numpy objects as results.

        :param iterable X: data to transform (array-like or :class:`h2o.H2OFrame`).
        :param transform_params: parameters passed to the underlying `transform` method of the :class:`h2o.estimators.H2OEstimator` (if supported).
        :return: transformed data (:class:`h2o.H2OFrame` by default ).
        """
        if self._get_custom_param('_transform'):
            return self._get_custom_param('_transform')(self, X)
        elif hasattr(self._estimator, 'transform') and callable(self._estimator.transform):
            return self._estimator.transform(X, **transform_params)
        else:
            return self._predict(X)

    @params_as_h2o_frames(result_conversion=False)
    def inverse_transform(self, X):
        """Apply reverse transformation.

        Note that it doesn't convert result back to numpy by default as it is intended to be chained
        with another H2O transformer or estimator.
        The transformer should be instantiated with `data_conversion=True` to always obtain numpy objects as results.

        :param iterable X: data to transform (array-like or :class:`h2o.H2OFrame`).
        :return: transformed data (:class:`h2o.H2OFrame` by default).
        """
        if hasattr(self._estimator, 'inverse_transform') and callable(self._estimator.inverse_transform):
            return self._estimator.inverse_transform(X)
        raise AttributeError("{} does not support 'inverse_transform'.".format(self.__class__.__name__))
