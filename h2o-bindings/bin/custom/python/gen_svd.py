rest_api_version = 99
supervised_learning = False


def class_extensions():
    def init_for_pipeline(self):
        """
        Returns H2OSVD object which implements fit and transform method to be used in sklearn.Pipeline properly.
        All parameters defined in self.__params, should be input parameters in H2OSVD.__init__ method.

        :returns: H2OSVD object

        :examples:

        >>> from h2o.transforms.preprocessing import H2OScaler
        >>> from h2o.estimators import H2ORandomForestEstimator
        >>> from h2o.estimators import H2OSingularValueDecompositionEstimator
        >>> from sklearn.pipeline import Pipeline
        >>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
        >>> pipe = Pipeline([("standardize", H2OScaler()),
        ...                  ("svd", H2OSingularValueDecompositionEstimator(nv=3).init_for_pipeline()),
        ...                  ("rf", H2ORandomForestEstimator(seed=42,ntrees=50))])
        >>> pipe.fit(arrests[1:], arrests[0])
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

examples = dict(
    nv="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(nv=4,
...                                                  transform="standardize",
...                                                  max_iterations=2000)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    transform="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(nv=4,
...                                                  transform="standardize",
...                                                  max_iterations=2000)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    max_iterations="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(nv=4,
...                                                  transform="standardize",
...                                                  max_iterations=2000)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> checkpoints_dir = tempfile.mkdtemp()
>>> fit_h2o = H2OSingularValueDecompositionEstimator(export_checkpoints_dir=checkpoints_dir,
...                                                  seed=-5)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> len(listdir(checkpoints_dir))
""",
    ignore_const_cols="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(ignore_const_cols=False,
...                                                  nv=4)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    keep_u="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(keep_u=False)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    max_runtime_secs="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(nv=4,
...                                                  transform="standardize",
...                                                  max_runtime_secs=25)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    score_each_iteration="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(nv=4,
...                                                  score_each_iteration=True)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    seed="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(nv=4, seed=-3)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    svd_method="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(svd_method="power")
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    training_frame="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator()
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    u_name="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(u_name="fit_h2o")
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o.u_name
>>> fit_h2o
""",
    use_all_factor_levels="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> fit_h2o = H2OSingularValueDecompositionEstimator(use_all_factor_levels=False)
>>> fit_h2o.train(x=list(range(4)), training_frame=arrests)
>>> fit_h2o
""",
    validation_frame="""
>>> arrests = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> train, valid = arrests.split_frame(ratios=[.8])
>>> fit_h2o = H2OSingularValueDecompositionEstimator()
>>> fit_h2o.train(x=list(range(4)),
...               training_frame=train,
...               validation_frame=valid)
>>> fit_h2o
"""
)
