def class_extensions():
    def init_for_pipeline(self):
        """
        Returns H2OPCA object which implements fit and transform method to be used in sklearn.Pipeline properly.
        All parameters defined in self.__params, should be input parameters in H2OPCA.__init__ method.

        :returns: H2OPCA object
        """
        import inspect
        from h2o.transforms.decomposition import H2OPCA
        # check which parameters can be passed to H2OPCA init
        var_names = list(dict(inspect.getmembers(H2OPCA.__init__.__code__))['co_varnames'])
        parameters = {k: v for k, v in self._parms.items() if k in var_names}
        return H2OPCA(**parameters)


extensions = dict(
    __class__=class_extensions,
)

examples = dict(
    compute_metrics="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
>>> prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
>>> prostate['RACE'] = prostate['RACE'].asfactor()
>>> prostate['DCAPS'] = prostate['DCAPS'].asfactor()
>>> prostate['DPROS'] = prostate['DPROS'].asfactor()
>>> pros_pca = H2OPrincipalComponentAnalysisEstimator(compute_metrics=False)
>>> pros_pca.train(x=prostate.names, training_frame=prostate)
>>> pros_pca.show()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
>>> prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
>>> prostate['RACE'] = prostate['RACE'].asfactor()
>>> prostate['DCAPS'] = prostate['DCAPS'].asfactor()
>>> prostate['DPROS'] = prostate['DPROS'].asfactor()
>>> checkpoints_dir = tempfile.mkdtemp()
>>> pros_pca = H2OPrincipalComponentAnalysisEstimator()
>>> pros_pca.train(x=prostate.names, training_frame=prostate)
>>> len(listdir(checkpoints_dir))
""",
    ignore_const_cols="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
>>> prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
>>> prostate['RACE'] = prostate['RACE'].asfactor()
>>> prostate['DCAPS'] = prostate['DCAPS'].asfactor()
>>> prostate['DPROS'] = prostate['DPROS'].asfactor()
>>> pros_pca = H2OPrincipalComponentAnalysisEstimator(ignore_const_cols=False)
>>> pros_pca.train(x=prostate.names, training_frame=prostate)
>>> pros_pca.show()
""",
    impute_missing="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
>>> prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
>>> prostate['RACE'] = prostate['RACE'].asfactor()
>>> prostate['DCAPS'] = prostate['DCAPS'].asfactor()
>>> prostate['DPROS'] = prostate['DPROS'].asfactor()
>>> pros_pca = H2OPrincipalComponentAnalysisEstimator(impute_missing=True)
>>> pros_pca.train(x=prostate.names, training_frame=prostate)
>>> pros_pca.show()
""",
    init_for_pipeline="""
>>> from sklearn.pipeline import Pipeline
>>> from h2o.transforms.preprocessing import H2OScaler
>>> from h2o.estimators import H2ORandomForestEstimator
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> pipe = Pipeline([("standardize", H2OScaler()),
...                  ("pca", H2OPrincipalComponentAnalysisEstimator(k=2).init_for_pipeline()),
...                  ("rf", H2ORandomForestEstimator(seed=42,ntrees=5))])
>>> pipe.fit(iris[:4], iris[4])
""",
)
