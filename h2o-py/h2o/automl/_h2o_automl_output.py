import h2o
from h2o.model import ModelBase


class H2OAutoMLOutput:
    """
    AutoML Output object containing the results of AutoML
    """
    
    def __init__(self, state):
        self._project_name = state['project_name']
        self._leader = state['leader']
        self._leaderboard = state['leaderboard']
        self._event_log = el = state['event_log']
        self._training_info = {r[0]: r[1]
                               for r in el[el['name'] != '', ['name', 'value']]
                                   .as_data_frame(use_pandas=False, header=False)
                               }

    def __getitem__(self, item):
        if (
            hasattr(self, item) and
            # do not enable user to get anything else than properties through the dictionary interface
            hasattr(self.__class__, item) and 
            isinstance(getattr(self.__class__, item), property)
        ):
            return getattr(self, item)
        raise KeyError(item)

    @property
    def project_name(self):
        """
        Retrieve a string indicating the project_name of the automl instance to retrieve.
        :return: a string containing the project_name
        """
        return self._project_name

    @property
    def leader(self):
        """
        Retrieve the top model from an H2OAutoML object

        :return: an H2O model
        """
        return self._leader

    @property
    def leaderboard(self):
        """
        Retrieve the leaderboard from an H2OAutoML object

        :return: an H2OFrame with model ids in the first column and evaluation metric in the second column sorted
                 by the evaluation metric
        """
        return self._leaderboard

    @property
    def training_info(self):
        """
        Expose the name/value columns of `event_log` as a simple dictionary, for example `start_epoch`, `stop_epoch`, ...
        See :func:`event_log` to obtain a description of those key/value pairs.

        :return: a dictionary with event_log['name'] column as keys and event_log['value'] column as values.
        """
        return self._training_info

    @property
    def event_log(self):
        """
        Retrieve the backend event log from an H2OAutoML object

        :return: an H2OFrame with detailed events occurred during the AutoML training.
        """
        return self._event_log

    def predict(self, test_data):
        """
        Predict on a dataset.

        :param H2OFrame test_data: Data on which to make predictions.

        :returns: A new H2OFrame of predictions.

        :examples:
        >>> # Get an H2OAutoMLOutput object
        >>> aml = get_automl('project_name')
        >>> # Predict with top model from AutoML Leaderboard on a H2OFrame called 'test'
        >>> aml.predict(test)
        """
        return self.leader.predict(test_data)

    # ---------------------------------------------------------------------------
    # Download POJO/MOJO with AutoML
    # ---------------------------------------------------------------------------

    def download_pojo(self, path="", get_genmodel_jar=False, genmodel_name=""):
        """
        Download the POJO for the leader model in AutoML to the directory specified by path.

        If path is an empty string, then dump the output to screen.

        :param path:  An absolute path to the directory where POJO should be saved.
        :param get_genmodel_jar: if True, then also download h2o-genmodel.jar and store it in folder ``path``.
        :param genmodel_name: Custom name of genmodel jar
        :returns: name of the POJO file written.
        """

        return h2o.download_pojo(self.leader, path, get_jar=get_genmodel_jar, jar_name=genmodel_name)

    def download_mojo(self, path=".", get_genmodel_jar=False, genmodel_name=""):
        """
        Download the leader model in AutoML in MOJO format.

        :param path: the path where MOJO file should be saved.
        :param get_genmodel_jar: if True, then also download h2o-genmodel.jar and store it in folder ``path``.
        :param genmodel_name: Custom name of genmodel jar
        :returns: name of the MOJO file written.
        """

        return ModelBase.download_mojo(self.leader, path, get_genmodel_jar, genmodel_name)
