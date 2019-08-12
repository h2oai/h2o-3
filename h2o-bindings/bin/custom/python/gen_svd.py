rest_api_version = 99

def class_extensions():
    def init_for_pipeline(self):
        """
        Returns H2OSVD object which implements fit and transform method to be used in sklearn.Pipeline properly.
        All parameters defined in self.__params, should be input parameters in H2OSVD.__init__ method.

        :returns: H2OSVD object
        """
        import inspect
        from h2o.transforms.decomposition import H2OSVD
        # check which parameters can be passed to H2OSVD init
        var_names = list(dict(inspect.getmembers(H2OSVD.__init__.__code__))['co_varnames'])
        parameters = {k: v for k, v in self._parms.items() if k in var_names}
        return H2OSVD(**parameters)


extensions = dict(
    __class__=class_extensions,
)
