extensions = dict(
    __class__="""
def init_for_pipeline(self):
    \"""
    Returns H2OPCA object which implements fit and transform method to be used in sklearn.Pipeline properly.
    All parameters defined in self.__params, should be input parameters in H2OPCA.__init__ method.

    :returns: H2OPCA object
    \"""
    import inspect
    from h2o.transforms.decomposition import H2OPCA
    # check which parameters can be passed to H2OPCA init
    var_names = list(dict(inspect.getmembers(H2OPCA.__init__.__code__))['co_varnames'])
    parameters = {k: v for k, v in self._parms.items() if k in var_names}
    return H2OPCA(**parameters)
""",
)
