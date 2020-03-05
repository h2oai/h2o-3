import h2o
from h2o.model import ModelBase


class H2OAutoMLBaseMixin:
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
