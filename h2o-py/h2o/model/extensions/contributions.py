import h2o
from h2o.exceptions import H2OValueError
from h2o.job import H2OJob
from h2o.utils.typechecks import assert_is_type, Enum


class Contributions:

    def _predict_contributions(self, test_data, output_format, top_n, bottom_n, compare_abs):
        """
        Predict feature contributions - SHAP values on an H2O Model (only GBM, XGBoost, DRF models and equivalent
        imported MOJOs).
        
        Returned H2OFrame has shape (#rows, #features + 1). There is a feature contribution column for each input
        feature, and the last column is the model bias (same value for each row). The sum of the feature contributions
        and the bias term is equal to the raw prediction of the model. Raw prediction of tree-based models is the sum 
        of the predictions of the individual trees before the inverse link function is applied to get the actual
        prediction. For Gaussian distribution the sum of the contributions is equal to the model prediction. 

        **Note**: Multinomial classification models are currently not supported.

        :param H2OFrame test_data: Data on which to calculate contributions.
        :param Enum output_format: Specify how to output feature contributions in XGBoost. XGBoost by default outputs 
            contributions for 1-hot encoded features, specifying a Compact output format will produce a per-feature
            contribution. One of: ``"Original"``, ``"Compact"``.
        :param top_n: Return only #top_n highest contributions + bias:
        
            - If ``top_n<0`` then sort all SHAP values in descending order
            - If ``top_n<0 && bottom_n<0`` then sort all SHAP values in descending order
            
        :param bottom_n: Return only #bottom_n lowest contributions + bias:
        
            - If top_n and bottom_n are defined together then return array of #top_n + #bottom_n + bias
            - If ``bottom_n<0`` then sort all SHAP values in ascending order
            - If ``top_n<0 && bottom_n<0`` then sort all SHAP values in descending order
            
        :param compare_abs: True to compare absolute values of contributions
        :returns: A new H2OFrame made of feature contributions.

        """
        assert_is_type(output_format, None, Enum("Original", "Compact"))
        if not isinstance(test_data, h2o.H2OFrame): raise H2OValueError("test_data must be an instance of H2OFrame")
        j = H2OJob(h2o.api("POST /4/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                           data={"predict_contributions": True,
                                 "predict_contributions_output_format": output_format,
                                 "top_n": top_n,
                                 "bottom_n": bottom_n,
                                 "compare_abs": compare_abs}), "contributions")
        j.poll()
        return h2o.get_frame(j.dest_key)
