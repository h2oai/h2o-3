import h2o


class FeatureInteraction:

    def _feature_interaction(self, max_interaction_depth=100, max_tree_depth=100, max_deepening=-1, path=None):
        """
        Feature interactions and importance, leaf statistics and split value histograms in a tabular form.
        Available for XGBoost and GBM.

        Metrics:
        Gain - Total gain of each feature or feature interaction.
        FScore - Amount of possible splits taken on a feature or feature interaction.
        wFScore - Amount of possible splits taken on a feature or feature interaction weighed by 
        the probability of the splits to take place.
        Average wFScore - wFScore divided by FScore.
        Average Gain - Gain divided by FScore.
        Expected Gain - Total gain of each feature or feature interaction weighed by the probability to gather the gain.
        Average Tree Index
        Average Tree Depth

        :param max_interaction_depth: Upper bound for extracted feature interactions depth. Defaults to 100.
        :param max_tree_depth: Upper bound for tree depth. Defaults to 100.
        :param max_deepening: Upper bound for interaction start deepening (zero deepening => interactions 
            starting at root only). Defaults to -1.
        :param path: (Optional) Path where to save the output in .xlsx format (e.g. ``/mypath/file.xlsx``).
            Please note that Pandas and XlsxWriter need to be installed for using this option. Defaults to None.


        :examples:
        >>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
        >>> predictors = boston.columns[:-1]
        >>> response = "medv"
        >>> boston['chas'] = boston['chas'].asfactor()
        >>> train, valid = boston.split_frame(ratios=[.8])
        >>> boston_xgb = H2OXGBoostEstimator(seed=1234)
        >>> boston_xgb.train(y=response, x=predictors, training_frame=train)
        >>> feature_interactions = boston_xgb.feature_interaction()
        """
        kwargs = {}
        kwargs["model_id"] = self.model_id
        kwargs["max_interaction_depth"] = max_interaction_depth
        kwargs["max_tree_depth"] = max_tree_depth
        kwargs["max_deepening"] = max_deepening

        json = h2o.api("POST /3/FeatureInteraction", data=kwargs)
        if path is not None:
            import pandas as pd
            writer = pd.ExcelWriter(path, engine='xlsxwriter')
            for fi in json['feature_interaction']:
                fi.as_data_frame().to_excel(writer, sheet_name=fi._table_header)
            writer.save()

        return json['feature_interaction']
