"""
This module implements the abstract model class. All models inherit from this class.
"""

import abc
import frame
import h2o


class ModelBase(object):
    """Abstract base class for H2O model objects.

    Every model inherits from ModelBase and must implement the following methods:
        summary, show, predict, performance
    """
    __metaclass__ = abc.ABCMeta

    def __init__(self, parameters=None):
        """
        :param parameters: A dictionary of parameters the model was constructed with.
        :return: A new model object.
        """
        self.model_type = None  # set after the model is fitted
        self.parameters = parameters
        self.model = None  # set after the model is fitted

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
