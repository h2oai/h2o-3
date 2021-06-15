supervised_learning = False


def class_extensions():
    def init_for_pipeline(self):
        """
        Returns H2OPCA object which implements fit and transform method to be used in sklearn.Pipeline properly.
        All parameters defined in self.__params, should be input parameters in H2OPCA.__init__ method.

        :returns: H2OPCA object

        :examples:

        >>> from sklearn.pipeline import Pipeline
        >>> from h2o.transforms.preprocessing import H2OScaler
        >>> from h2o.estimators import H2ORandomForestEstimator
        >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> pipe = Pipeline([("standardize", H2OScaler()),
        ...                  ("pca", H2OPrincipalComponentAnalysisEstimator(k=2).init_for_pipeline()),
        ...                  ("rf", H2ORandomForestEstimator(seed=42,ntrees=5))])
        >>> pipe.fit(iris[:4], iris[4])
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
>>> pros_pca = H2OPrincipalComponentAnalysisEstimator(impute_missing=True,
...                                                   export_checkpoints_dir=checkpoints_dir)
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
    k="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator(k=-1,
...                                                   transform="standardize",
...                                                   pca_method="power",
...                                                   impute_missing=True,
...                                                   max_iterations=800)
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    max_iterations="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator(k=-1,
...                                                   transform="standardize",
...                                                   pca_method="power",
...                                                   impute_missing=True,
...                                                   max_iterations=800)
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    max_runtime_secs="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator(k=-1,
...                                                   transform="standardize",
...                                                   pca_method="power",
...                                                   impute_missing=True,
...                                                   max_iterations=800
...                                                   max_runtime_secs=15)
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    pca_impl="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator(k=3,
...                                                   pca_impl="jama",
...                                                   impute_missing=True,
...                                                   max_iterations=1200)
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    pca_method="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator(k=-1,
...                                                   transform="standardize",
...                                                   pca_method="power",
...                                                   impute_missing=True,
...                                                   max_iterations=800)
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    score_each_iteration="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator(k=3,
...                                                   score_each_iteration=True,
...                                                   seed=1234,
...                                                   impute_missing=True)
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    seed="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator(k=3,
...                                                   seed=1234,
...                                                   impute_missing=True)
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    training_frame="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator()
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    transform="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator(k=-1,
...                                                   transform="standardize",
...                                                   pca_method="power",
...                                                   impute_missing=True,
...                                                   max_iterations=800)
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    use_all_factor_levels="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> data_pca = H2OPrincipalComponentAnalysisEstimator(k=3,
...                                                   use_all_factor_levels=True,
...                                                   seed=1234)
>>> data_pca.train(x=data.names, training_frame=data)
>>> data_pca.show()
""",
    validation_frame="""
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/SDSS_quasar.txt.zip")
>>> train, valid = data.split_frame(ratios=[.8], seed=1234)
>>> model_pca = H2OPrincipalComponentAnalysisEstimator(impute_missing=True)
>>> model_pca.train(x=data.names,
...                training_frame=train,
...                validation_frame=valid)
>>> model_pca.show()
"""
)
