# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.model import ModelBase
from h2o.utils.shared_utils import can_use_pandas


class H2OAnomalyDetectionModel(ModelBase):

    def varsplits(self, use_pandas=False):
        """
        Retrieve per-variable split information for a given Isolation Forest model. Output will include:
        
        - count
            The number of times a variable was used to make a split.
        - aggregated_split_ratios
            The split ratio is defined as ``abs(#left_observations - #right_observations) / #before_split``.
            Even splits (``#left_observations`` approx the same as ``#right_observations``) contribute
            less to the total aggregated split ratio value for the given feature;
            highly imbalanced splits (eg. ``#left_observations >> #right_observations``) contribute more.
        - aggregated_split_depths
            The sum of all depths of a variable used to make a split. 
            (If a variable is used on level N of a tree, then it contributes with N to the total aggregate.)

        :param use_pandas: If ``True``, then the variable splits will be returned as a Pandas data frame.
        :returns: A list or Pandas DataFrame.

        :examples:

        >>> from h2o.estimators import H2OIsolationForestEstimator
        >>> h2o_df = h2o.import_file("https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")
        >>> train,test = h2o_df.split_frame(ratios=[0.75])
        >>> model = H2OIsolationForestEstimator(sample_rate = 0.1,
        ...                                     max_depth = 20,
        ...                                     ntrees = 50)
        >>> model.train(training_frame=train)
        >>> model.varsplits()
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
