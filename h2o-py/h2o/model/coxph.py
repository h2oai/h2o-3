# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from .model_base import ModelBase


class H2OCoxPHModel(ModelBase):

    def formula(self):
        """Survival formula."""
        return self._model_json["output"]["formula"]

    def coefficients_table(self):
        """Coefficients table."""
        return self._model_json["output"]["coefficients_table"]

    def summary(self):
        """Prints summary information about this model."""
        print("Call: ")
        print(self.formula())
        print(self.coefficients_table())
        output = self._model_json["output"]
        print("Likelihood ratio test=%f" % (output["loglik_test"]))
        print("n=%d, number of events=%d" % (output["n"], output["total_event"]))

