supervised_learning = False
options = dict(requires_training_frame=False)


def class_extensions():
    @staticmethod
    def from_file(file=str, model_id=None):
        """
        Creates new Generic model by loading existing embedded model into library, e.g. from H2O MOJO.
        The imported model must be supported by H2O.

        :param file: A string containing path to the file to create the model from
        :param model_id: Model ID
        :return: H2OGenericEstimator instance representing the generic model

        :examples:

        >>> from h2o.estimators import H2OIsolationForestEstimator, H2OGenericEstimator
        >>> import tempfile
        >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/airlines_train.csv")
        >>> ifr = H2OIsolationForestEstimator(ntrees=1)
        >>> ifr.train(x=["Origin","Dest"], y="Distance", training_frame=airlines)
        >>> original_model_filename = tempfile.mkdtemp()
        >>> original_model_filename = ifr.download_mojo(original_model_filename)
        >>> model = H2OGenericEstimator.from_file(original_model_filename)
        >>> model.model_performance()
        """
        model = H2OGenericEstimator(path=file, model_id=model_id)
        model.train()

        return model


    @staticmethod
    def guess_model_id(path):
        # FIXME: this method doesn't work in edge cases (see https://h2oai.atlassian.net/browse/PUBDEV-8489)
        path_split = path.split('/')
        return path_split[len(path_split)-1].split('.')[0]

extensions = dict(
    __class__=class_extensions,
    __init__model_id="""
if model_id is None and path is not None:
    model_id = H2OGenericEstimator.guess_model_id(path)
self._id = self._parms['model_id'] = model_id
"""
)

examples = dict(
    model_key="""
>>> from h2o.estimators import H2OGenericEstimator, H2OXGBoostEstimator
>>> import tempfile
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/airlines_train.csv")
>>> y = "IsDepDelayed"
>>> x = ["fYear","fMonth","Origin","Dest","Distance"]
>>> xgb = H2OXGBoostEstimator(ntrees=1, nfolds=3)
>>> xgb.train(x=x, y=y, training_frame=airlines)
>>> original_model_filename = tempfile.mkdtemp()
>>> original_model_filename = xgb.download_mojo(original_model_filename)
>>> key = h2o.lazy_import(original_model_filename)
>>> fr = h2o.get_frame(key[0])
>>> model = H2OGenericEstimator(model_key=fr)
>>> model.train()
>>> model.auc()
""",
    path="""
>>> from h2o.estimators import H2OIsolationForestEstimator, H2OGenericEstimator
>>> import tempfile
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/airlines_train.csv")
>>> ifr = H2OIsolationForestEstimator(ntrees=1)
>>> ifr.train(x=["Origin","Dest"], y="Distance", training_frame=airlines)
>>> generic_mojo_filename = tempfile.mkdtemp("zip","genericMojo")
>>> generic_mojo_filename = model.download_mojo(path=generic_mojo_filename)
>>> model = H2OGenericEstimator.from_file(generic_mojo_filename)
>>> model.model_performance()
"""
)
