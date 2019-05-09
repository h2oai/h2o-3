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
