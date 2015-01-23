"""
Binomial Models should be comparable.
"""

import ModelBase


class H2OBinomialModel(ModelBase):

    def summary(self):
        """
        This method prints out various relevant pieces of information for a binomial
        model (e.g. AUC, thresholds for various criteria, etc.)
        :return:
        """
        pass

    def show(self):
        super(H2OBinomialModel, self).show()

    def performance(self, test_data=None):
        pass

    def predict(self, test_data=None):
        pass