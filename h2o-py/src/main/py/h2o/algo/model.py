"""
This module implements the abstract model class. All models inherit from this class.
"""

import abc


class ModelBase(object):
    """Abstract base class for H2O model objects.

    Every model inherits from ModelBase and must implement the following methods:
        summary, show, predict, performance
    """
    __metaclass__ = abc.ABCMeta

    def __init__(self, model_type=None, parameters=None):
        """
        :param model_type: An identifier stating the type of model (e.g., "gbm", "kmeans")
        :param parameters: A dictionary of parameters the model was constructed with.
        :return: A new model.
        """
        self.model_type = model_type
        self.parameters = parameters

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

    @abc.abstractmethod
    def show(self):
        """
        Print a brief summary of the model.
        :return:
        """
        return

    def get_type(self):
        return self.model_type

    def get_parameters(self):
        return self.parameters
