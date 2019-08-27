# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from .model_base import ModelBase
from h2o.utils.shared_utils import can_use_pandas


class H2OAnomalyDetectionModel(ModelBase):

    def varsplits(self, use_pandas=False):
        """
        Retrieve per-variable split information for a given Isolation Forest model.

        :param use_pandas: If True, then the variable splits will be returned as a pandas data frame.

        :returns: A list or Pandas DataFrame.
        """
        model = self._model_json["output"]
        if "variable_splits" in list(model.keys()) and model["variable_splits"]:
            vals = model["variable_splits"].cell_values
            header = model["variable_splits"].col_header

            if use_pandas and can_use_pandas():
                import pandas
                return pandas.DataFrame(vals, columns=header)
            else:
                return vals
        else:
            print("Warning: This model doesn't provide variable split information")
