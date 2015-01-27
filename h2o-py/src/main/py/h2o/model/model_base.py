"""
This module implements the abstract model class. All model things inherit from this class.
"""

import abc
from ..two_dim_table import H2OTwoDimTable
import tabulate


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
        fitted_model = getattr(self, "_fitted_model")
        model = fitted_model.raw_model_output
        sub = [k for k in model.keys() if k in model["help"].keys()
               and not k.startswith("_") and k != "help"]
        val = [[model[k]] for k in sub]
        lab = [model["help"][k] + ":" for k in sub if k != "help"]

        for i in range(len(val)):
            val[i].insert(0, lab[i])

        print
        print "Model Details:"
        print
        print tabulate.tabulate(val, headers=["Description", "Value"])
        print
        for v in val:
            if isinstance(v[1], H2OTwoDimTable):
                v[1].show()