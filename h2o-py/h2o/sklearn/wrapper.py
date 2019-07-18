from functools import partial, update_wrapper, wraps

from sklearn.base import BaseEstimator, MetaEstimatorMixin, TransformerMixin

from .. import h2o, H2OFrame

try:
    from inspect import signature
except ImportError:
    from sklearn.utils.fixes import signature


class H2OClusterMixin(object):
    """ Mixin that automatically hand"""

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
        return

    def shutdown_cluster(self):
        """

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
    obj.__class__ = type(obj.__class__.__name__, (obj.__class__,)+tuple(mixins), dict())
    return obj


def estimator(cls, name=None, mixins=None, is_generic=False):
    o = mixin(cls(), BaseEstimator)
    defaults = o.get_params()
    gen_class_name = '.'.join([__name__, (name if name is not None else cls.__name__+'Sklearn')])

    def gen_init():
        yield "def init(self,"
        for k, v in defaults.items():
            yield "         {k}={v},".format(k=k, v=repr(v))
        yield "         init_cluster_args=None,"
        yield "         estimator_cls=None,"
        if is_generic:
            yield "         estimator_type=None,"
        yield "         ):"
        yield "    kwargs = locals()"
        yield "    del kwargs['self']"
        yield "    super(self.__class__, self).__init__(**kwargs)"
        # yield "    super({name}, self).__init__(**kwargs)".format(name=gen_class_name)
    init_code = '\n'.join(list(gen_init()))
    # print(init_code)
    scope = {}
    exec(init_code, scope)
    init = (partial(scope['init'], estimator_cls=cls))
    update_wrapper(init, scope['init'])
    # init = scope['init']

    # def get_param_names(cls):
    #     return o._get_param_names()

    # extended = type(cls.__name__+'Sklearn', (cls, BaseEstimator, MetaEstimatorMixin)+tuple(mixins), dict(
    #     __init__=init,
        # _get_param_names=classmethod(get_param_names)
    # ))
    mixins = tuple(mixins) if mixins is not None else ()
    extended = type(gen_class_name, (H2OtoSklearnEstimator,)+mixins, dict(
        __init__=init,
        _h2o_estimator_cls=cls
    ))
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


def expect_h2o_frames(fn):
    sig = signature(fn)
    has_self = 'self' in sig.parameters
    has_X = 'X' in sig.parameters
    has_y = 'y' in sig.parameters
    assert has_X or has_y, "@expect_h2o_frames decorator is intended for methods " \
                           "taking at least an X or y argument."

    @wraps(fn)
    def decorator(*args, **kwargs):
        _args = sig.bind(*args, **kwargs).arguments
        is_classifier = False
        if has_self and isinstance(_args.get('self', None), MetaEstimatorMixin):
            is_classifier = _args.get('self').is_classifier()
        if has_X:
            _args['X'] = _to_h2o_frame(_args.get('X', None))
        if has_y:
            _args['y'] = _to_h2o_frame(_args.get('y', None), as_factor=is_classifier)
        return fn(**_args)

    return decorator


class H2OtoSklearnEstimator(BaseEstimator, MetaEstimatorMixin, H2OClusterMixin):

    def __init__(self, estimator_cls=None, estimator_type=None, init_cluster_args=None, **kwargs):
        super(H2OtoSklearnEstimator, self).__init__()
        self._estimator = None
        self._h2o_estimator_params = kwargs
        self._h2o_estimator_cls = estimator_cls
        if estimator_type is not None:
            self._estimator_type = estimator_type
        if init_cluster_args is None:
            init_cluster_args = {}
        self.init_cluster(**init_cluster_args)

    def get_params(self, deep=True):
        return self._h2o_estimator_params

    def set_params(self, **params):
        self._h2o_estimator_params.update(params)

    @expect_h2o_frames
    def fit(self, X, y=None, **fit_params):
        self._estimator = self._h2o_estimator_cls(self._h2o_estimator_params)
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



