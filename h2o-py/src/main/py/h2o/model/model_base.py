"""
This module implements the abstract model class. All model things inherit from this class.
"""

import abc


class H2OModelInstantiationException(Exception):
    pass


class ModelBase(object):
    """Abstract base class for H2O model objects.

    Every model inherits from ModelBase and must implement the following methods:
        summary, show, predict, performance
    """
    __metaclass__ = abc.ABCMeta

    BINOMIAL    = "H2OBinomialModel"
    MULTINOMIAL = "H2OMultinomialModel"
    CLUSTERING  = "H2OClusteringModel"
    REGRESSION  = "H2ORegressionModel"


    @abc.abstractmethod
    def predict(self, test_data=None):
        """
        Predict on a dataset.
        :param test_data: Data to be predicted on.
        :return: An object of class H2OFrame.
        """
        return

    @abc.abstractmethod
    def performance(self, test_data=None):
        """
        Generate model metrics for this model on test_data.
        :param test_data: Data set for which model metrics shall be computed against.
        :return: An object of class H2OModelMetrics.
        """
        return

    @abc.abstractmethod
    def summary(self):
        """
        Print a detailed summary of the model.
        :return:
        """
        return

    def show(self):
        """
        Print a brief summary of the model.
        :return: None
        """
        model_type = getattr(self, "_model_type")
        algo = getattr(self, "_algo")
        print
        print model_type + ": " + algo
        print
        print "Model Details:"
        print
        print