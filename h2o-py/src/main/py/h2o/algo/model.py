"""
This module implements the abstract model class. All models inherit from this class.
"""

import abc
from frame import H2OFrame
from frame import H2OCONN


class ModelBase(object):
    """Abstract base class for H2O model objects.

    Every model inherits from ModelBase and must implement the following methods:
        summary, show, predict, performance
    """
    __metaclass__ = abc.ABCMeta

    def __init__(self, model_type=None, parameters=None, model=None):
        """
        :param model_type: An identifier stating the type of model (e.g., "gbm", "kmeans")
        :param parameters: A dictionary of parameters the model was constructed with.
        :param model: A fitted model.
        :return: A new model object.
        """
        self.model_type = model_type
        self.parameters = parameters
        self.model = None

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

    def fit(self, x=None, y=None):
        """
        Fit the model to the inputs x, y
        :param x: A list[] of 0-based indices or column names.
        :param y: A 0-based index or a column name
        :return: Returns self (a fitted model).
        """
        if not x or not y:
            if not self.parameters.x or not self.parameters.y:
                raise ValueError("No fit can be made, missing training data (x,y).")
            if x:
                self.parameters.x = x
            if y:
                self.parameters.y = y

        x = self.parameters.x
        y = self.parameters.y
        dataset = self.parameters.dataset

        if not isinstance(dataset, H2OFrame):
            raise ValueError("`dataset` must be a H2OFrame not " + str(type(dataset)))

        if not self.parameters.dataset[x]:
            raise ValueError(x + " must be column(s) in " + str(dataset))

        fr = H2OFrame.send_frame(dataset)  # a temp frame to train on
        vfr = None  # a temp validation frame
        if self.parameters.validation_dataset:
            vfr = H2OFrame.send_frame(dataset)

        # TODO: need to check self.parameters fully with /Parameters endpoint

        self.model = H2OCONN.modelBuilder(self, fr, vfr)
        H2OCONN.Remove(fr)s
