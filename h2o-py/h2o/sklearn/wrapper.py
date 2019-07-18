from collections import OrderedDict
from functools import partial, update_wrapper, wraps

from sklearn.base import is_classifier, is_regressor, BaseEstimator, TransformerMixin

from .. import h2o, H2OFrame
from ..utils.shared_utils import can_use_numpy, can_use_pandas

try:
    from inspect import signature
except ImportError:
    from sklearn.utils.fixes import signature


class H2OClusterMixin(object):
    """ Mixin that automatically handle the connection to H2O backend"""

    _h2o_components = []

    def init_cluster(self, **kwargs):
        """
        initialize the H2O cluster if needed
        and track the current instance to be able to automatically shutdown the cluster
        when there's no more instances in scope
        """
        if not h2o.connection():
            h2o.init(**kwargs)
        self._h2o_components.append(self)

    def shutdown_cluster(self):
        """
        force H2O cluster shutdown
        """
        if h2o.connection():
            h2o.cluster().shutdown()

    def __del__(self):
        """
        remove tracking reference to current h2o component
        and shutdown the H2O cluster if there's no more tracked component
        """
        if self in self._h2o_components:
            self._h2o_components.remove(self)
        if not self._h2o_components:
            self.shutdown_cluster()



def mixin(obj, *mixins):
    """

    :param obj: the object on which to apply the mixins
    :param mixins: the list of mixin classes to add to the object
    :return: the object with the mixins applied
    """
    obj.__class__ = type(obj.__class__.__name__, (obj.__class__,)+tuple(mixins), dict())
    return obj


def estimator(cls, name=None, module=None, default_params=None, mixins=None, is_generic=False):
    if default_params is None:
        try:
            # try to obtain the list of estimator params (with defaults) by mixing it with BaseEstimator
            # create a default instance, and use the introspection logic implemented in BaseEstimator.get_params
            o = mixin(cls(), BaseEstimator)
            default_params = o.get_params()
        except:
            # if the previous logic fails (constructor may do some nasty logic)
            # then we're just obtain the signature from the estimator class constructor
            sig = signature(cls.__init__)
            default_params = OrderedDict((p.name, p.default if p.default is not p.empty else None)
                                         for p in sig.parameters.values())
            del default_params['self']

    gen_class_name = name if name else cls.__name__+'Sklearn'
    gen_class_module = module if module else __name__

    # generate the constructor signature for introspection by BaseEstimator (get_params)
    #  and also for auto-completion in some environments.
    def gen_init_sig():
        yield "def init(self,"
        for k, v in default_params.items():
            yield "         {k}={v},".format(k=k, v=repr(v))
        yield "         init_cluster_args=None,"
        if is_generic:
            yield "         estimator_type=None,"
        yield "         ): pass"
    init_sig = '\n'.join(list(gen_init_sig()))
    scope = {}
    exec(init_sig, scope)  # execute the signature code in given scope for further access

    # the real constructor implementation logic
    def init(self, **kwargs):
        for k, v in default_params.items():
            kwargs.setdefault(k, v)
        kwargs.update(estimator_cls=cls)
        super(self.__class__, self).__init__(**kwargs)

    # modify init to look like the signature previously generated
    update_wrapper(init, scope['init'])

    mixins = tuple(mixins) if mixins else ()
    extended = type(gen_class_name, (_H2OtoSklearnEstimator,)+mixins, dict(
        __init__=init,
    ))
    extended.__module__ = gen_class_module
    return extended


def transformer(cls, *mixins):
    extended = type(cls.__name__+'Sklearn', (cls, BaseEstimator, TransformerMixin)+tuple(mixins), dict(
    ))
    return extended


def _to_h2o_frame(X, as_factor=False):
    if X is None or isinstance(X, H2OFrame):
        return X
    fr = H2OFrame(X)
    return fr.asfactor() if as_factor else fr


def _to_numpy(fr):
    if not isinstance(fr, H2OFrame):
        return fr
    df = fr.as_data_frame()
    if can_use_pandas():
        return df.values
    elif can_use_numpy():
        from numpy import array as np_array
        return np_array(df)
    else:
        return df


def expect_h2o_frames(fn):
    sig = signature(fn)
    has_self = 'self' in sig.parameters
    has_X = 'X' in sig.parameters
    has_y = 'y' in sig.parameters
    assert has_X or has_y, "@expect_h2o_frames decorator is intended for methods " \
                           "taking at least an X or y argument."

    def convert(arg, arguments, **kwargs):
        ori = arguments.get(arg, None)
        new = _to_h2o_frame(ori, **kwargs)
        converted = new is not ori
        arguments[arg] = new
        return converted

    def revert(result, converted=False):
        return _to_numpy(result) if converted else result

    @wraps(fn)
    def decorator(*args, **kwargs):
        _args = sig.bind(*args, **kwargs).arguments
        is_classifier = False
        if has_self and isinstance(_args.get('self', None), BaseEstimatorMixin):
            is_classifier = _args.get('self').is_classifier()
        converted = False
        if has_X:
            converted = convert('X', _args)
        if has_y:
            converted = convert('y', _args, as_factor=is_classifier) or converted
        return revert(fn(**_args), converted=converted)

    return decorator


class BaseEstimatorMixin(object):

    def is_classifier(self):
        return is_classifier(self)

    def is_regressor(self):
        return is_regressor(self)


class _H2OtoSklearnEstimator(BaseEstimator, BaseEstimatorMixin, H2OClusterMixin):

    def __init__(self,
                 estimator_cls=None,
                 estimator_type=None,
                 init_cluster_args=None,
                 **estimator_params):
        """
        :param estimator_cls: the H2O Estimator class.
        :param estimator_type: if provided, must be one of ('classifier', 'regressor').
        :param init_cluster_args: the arguments passed to `h2o.init()` if there's no connection to H2O backend.
        :param estimator_params: the estimator/model parameters.
        """
        super(_H2OtoSklearnEstimator, self).__init__()
        self._estimator_cls = estimator_cls
        if estimator_type:
            self._estimator_type = estimator_type

        # we only keep a ref to parameters names
        # all those params are also exposed as a regular attribute and can be modified directly
        #  on the estimator instance
        self._estimator_params = estimator_params.keys()
        self.__dict__.update(estimator_params)

        self._estimator = None

        if init_cluster_args is None:
            init_cluster_args = {}
        self.init_cluster(**init_cluster_args)

    @expect_h2o_frames
    def fit(self, X, y=None, **fit_params):
        params = {k: getattr(self, k, None) for k in self._estimator_params}
        print(params)
        self._estimator = self._estimator_cls(**params)
        training_frame = X if y is None else X.concat(y)
        self._estimator.train(y=-1, training_frame=training_frame, **fit_params)
        return self

    def _predict(self, X):
        return self._estimator.predict(X)

    @expect_h2o_frames
    def predict(self, X):
        return self._predict(X)[:, 0]

    @expect_h2o_frames
    def predict_proba(self, X):
        return self._predict(X)[:, 1:]



