# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals

import os

import h2o
from h2o.base import Keyed
from h2o.display import H2ODisplay, display, format_to_html, format_to_multiline, format_user_tips, print2 as print
from h2o.exceptions import H2OValueError, H2OTypeError
from h2o.job import H2OJob
from h2o.model.extensions import has_extension
from h2o.plot import decorate_plot_result, get_matplotlib_pyplot, get_matplotlib_cm, get_mplot3d_axes, RAISE_ON_FIGURE_ACCESS
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.compatibility import viewitems
from h2o.utils.metaclass import backwards_compatibility, deprecated_fn, h2o_meta, deprecated_params
from h2o.utils.shared_utils import can_use_pandas, can_use_numpy
from h2o.utils.typechecks import assert_is_type, assert_satisfies, Enum, is_type


@backwards_compatibility(
    instance_attrs=dict(
        giniCoef=lambda self, *args, **kwargs: self.gini(*args, **kwargs)
    )
)
class ModelBase(h2o_meta(Keyed, H2ODisplay)):
    """
    Base class for all models.
    """

    _options_ = {}    # dict of options declared in implementation

    def __init__(self):
        """Construct a new model instance."""
        self._id = None
        self._model_json = None
        self._metrics_class = None
        self._metrics_class_valid = None
        self._is_xvalidated = False
        self._xval_keys = None
        self._parms = {}  # internal, for object recycle
        self.parms = {}  # external
        self._estimator_type = None
        self._future = False  # used by __repr__/show to query job state
        self._job = None  # used when _future is True
        self._have_pojo = False
        self._have_mojo = False
        self._start_time = None
        self._end_time = None
        self._run_time = None

    @property
    def key(self):
        return self._id

    @property
    def model_id(self):
        """Model identifier."""
        return self._id

    @model_id.setter
    def model_id(self, newid):
        oldid = self._id
        self._id = newid
        h2o.rapids("(rename '%s' '%s')" % (oldid, newid))

    @property
    def params(self):
        """
        Get the parameters and the actual/default values only.

        :returns: A dictionary of parameters used to build this model.
        """
        params = {}
        for p in self.parms:
            params[p] = {"default": self.parms[p]["default_value"],
                         "actual": self.parms[p]["actual_value"],
                         "input": self.parms[p]["input_value"]}
        return params

    @property
    def default_params(self):
        """Dictionary of the default parameters of the model."""
        params = {}
        for p in self.parms:
            params[p] = self.parms[p]["default_value"]
        return params

    @property
    def actual_params(self):
        """Dictionary of actual parameters of the model."""
        params_to_select = {"model_id": "name",
                            "response_column": "column_name",
                            "training_frame": "name",
                            "validation_frame": "name"}
        params = {}
        for p in self.parms:
            if p in params_to_select.keys():
                params[p] = (self.parms[p].get("actual_value") or {}).get(params_to_select[p], None)
            else:
                params[p] = self.parms[p]["actual_value"]
        return params

    @property
    def full_parameters(self):
        """Dictionary of the full specification of all parameters."""
        return self.parms

    @property
    def type(self):
        """The type of model built. One of:

            - ``"classifier"``
            - ``"regressor"``
            - ``"unsupervised"``
        """
        return self._estimator_type

    @property
    def have_pojo(self):
        """True, if export to POJO is possible"""
        return self._have_pojo

    @property
    def have_mojo(self):
        """True, if export to MOJO is possible"""
        return self._have_mojo

    @property
    def start_time(self):
        """Timestamp (milliseconds since 1970) when the model training was started."""
        return self._start_time

    @property
    def end_time(self):
        """Timestamp (milliseconds since 1970) when the model training was ended."""
        return self._end_time

    @property
    def run_time(self):
        """Model training time in milliseconds."""
        return self._run_time

    def predict_leaf_node_assignment(self, test_data, type="Path"):
        """
        Predict on a dataset and return the leaf node assignment (only for tree-based models).

        :param H2OFrame test_data: Data on which to make predictions.
        :param Enum type: How to identify the leaf node. Nodes can be either identified by a path from to the root node
            of the tree to the node or by H2O's internal node id. One of: ``"Path"`` (default), ``"Node_ID"``.

        :returns: A new H2OFrame of predictions.
        """
        if not isinstance(test_data, h2o.H2OFrame): 
            raise ValueError("test_data must be an instance of H2OFrame")
        assert_is_type(type, None, Enum("Path", "Node_ID"))
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"leaf_node_assignment": True, "leaf_node_assignment_type": type})
        return h2o.get_frame(j["predictions_frame"]["name"])

    def staged_predict_proba(self, test_data):
        """
        Predict class probabilities at each stage of an H2O Model (only GBM models).

        The output structure is analogous to the output of function ``predict_leaf_node_assignment``. For each tree `t` and
        class `c` there will be a column `Tt.Cc` (eg. `T3.C1` for tree `3` and class `1`). The value will be the corresponding
        predicted probability of this class by combining the raw contributions of trees `T1.Cc,..,TtCc`. Binomial models
        build the trees just for the first class and values in columns `Tx.C1` thus correspond to the the probability p0.

        :param H2OFrame test_data: Data on which to make predictions.

        :returns: A new H2OFrame of staged predictions.
        """
        if not isinstance(test_data, h2o.H2OFrame): raise ValueError("test_data must be an instance of H2OFrame")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"predict_staged_proba": True})
        return h2o.get_frame(j["predictions_frame"]["name"])

    def predict_contributions(self, test_data, output_format="Original", top_n=None, bottom_n=None, compare_abs=False):
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
            contribution. One of: ``"Original"`` (default), ``"Compact"``.
        :param top_n: Return only #top_n highest contributions + bias:
        
            - If ``top_n<0`` then sort all SHAP values in descending order
            - If ``top_n<0 && bottom_n<0`` then sort all SHAP values in descending order
            
        :param bottom_n: Return only #bottom_n lowest contributions + bias:
        
            - If top_n and bottom_n are defined together then return array of #top_n + #bottom_n + bias
            - If ``bottom_n<0`` then sort all SHAP values in ascending order
            - If ``top_n<0 && bottom_n<0`` then sort all SHAP values in descending order
            
        :param compare_abs: True to compare absolute values of contributions
        :returns: A new H2OFrame made of feature contributions.

        :examples:
        
        >>> prostate = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv"
        >>> fr = h2o.import_file(prostate)
        >>> predictors = list(range(2, fr.ncol))
        >>> m = H2OGradientBoostingEstimator(ntrees=10, seed=1234)
        >>> m.train(x=predictors, y=1, training_frame=fr)
        >>> # Compute SHAP
        >>> m.predict_contributions(fr)
        >>> # Compute SHAP and pick the top two highest
        >>> m.predict_contributions(fr, top_n=2)
        >>> # Compute SHAP and pick the top two lowest
        >>> m.predict_contributions(fr, bottom_n=2)
        >>> # Compute SHAP and pick the top two highest regardless of the sign
        >>> m.predict_contributions(fr, top_n=2, compare_abs=True)
        >>> # Compute SHAP and pick top two lowest regardless of the sign
        >>> m.predict_contributions(fr, bottom_n=2, compare_abs=True)
        >>> # Compute SHAP values and show them all in descending order
        >>> m.predict_contributions(fr, top_n=-1)
        >>> # Compute SHAP and pick the top two highest and top two lowest
        >>> m.predict_contributions(fr, top_n=2, bottom_n=2)
        """
        if has_extension(self, 'Contributions'):
            return self._predict_contributions(test_data, output_format, top_n, bottom_n, compare_abs)
        err_msg = "This model doesn't support calculation of feature contributions."
        if has_extension(self, 'StandardCoef'):
            err_msg += " When features are independent, you can use the coef() method to get coefficients"
            err_msg += " for non-standardized data or coef_norm() to get coefficients for standardized data."
            err_msg += " You can plot standardized coefficient magnitudes by calling std_coef_plot() on the model."
        raise H2OTypeError(message=err_msg)

    def row_to_tree_assignment(self, original_training_data):
        """
        Output row to tree assignment for the model and provided training data.

        Output is frame of size nrow = nrow(original_training_data) and ncol = number_of_trees_in_model+1 in format:
         row_id    tree_1    tree_2    tree_3
              0         0         1         1
              1         1         1         1
              2         1         0         0
              3         1         1         0
              4         0         1         1
              5         1         1         1
              6         1         0         0
              7         0         1         0
              8         0         1         1
              9         1         0         0

        :param H2OFrame original_training_data: Data that was used for model training. Currently there is no validation
            of the input.
        :returns: A new H2OFrame made of row to tree assignment output.

        **Note**: Multinomial classification generate tree for each category, each tree use the same sample of the data.

        :examples:

        >>> prostate = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv"
        >>> fr = h2o.import_file(prostate)
        >>> predictors = list(range(2, fr.ncol))
        >>> m = H2OGradientBoostingEstimator(ntrees=10, seed=1234, sample_rate=0.6)
        >>> m.train(x=predictors, y=1, training_frame=fr)
        >>> # Output row to tree assignment
        >>> m.row_to_tree_assignment(fr)
        """
        if has_extension(self, 'RowToTreeAssignment'):
            return self._row_to_tree_assignment(original_training_data)
        err_msg = "This model doesn't support calculation of row to tree assignment."
        raise H2OTypeError(message=err_msg)

    def feature_frequencies(self, test_data):
        """
        Retrieve the number of occurrences of each feature for given observations 
        on their respective paths in a tree ensemble model. 
        Available for GBM, Random Forest and Isolation Forest models.

        :param H2OFrame test_data: Data on which to calculate feature frequencies.

        :returns: A new H2OFrame made of feature contributions.

        :examples:

        >>> from h2o.estimators import H2OIsolationForestEstimator
        >>> h2o_df = h2o.import_file("https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")
        >>> train,test = h2o_df.split_frame(ratios=[0.75])
        >>> model = H2OIsolationForestEstimator(sample_rate = 0.1,
        ...                                     max_depth = 20,
        ...                                     ntrees = 50)
        >>> model.train(training_frame=train)
        >>> model.feature_frequencies(test_data = test)
        """
        if not isinstance(test_data, h2o.H2OFrame): raise ValueError("test_data must be an instance of H2OFrame")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"feature_frequencies": True})
        return h2o.get_frame(j["predictions_frame"]["name"])
    
    def predict(self, test_data, custom_metric = None, custom_metric_func = None):
        """
        Predict on a dataset.

        :param H2OFrame test_data: Data on which to make predictions.
        :param custom_metric:  custom evaluation function defined as class reference, the class get uploaded
            into the cluster.
        :param custom_metric_func: custom evaluation function reference (e.g, result of ``upload_custom_metric``).

        :returns: A new H2OFrame of predictions.
        """
        # Upload evaluation function into DKV
        if custom_metric:
            assert_satisfies(custom_metric_func, custom_metric_func is None,
                             "The argument 'eval_func_ref' cannot be specified when eval_func is specified, ")
            eval_func_ref = h2o.upload_custom_metric(custom_metric)
        if not isinstance(test_data, h2o.H2OFrame): raise ValueError("test_data must be an instance of H2OFrame")
        j = H2OJob(h2o.api("POST /4/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id), data = {'custom_metric_func': custom_metric_func}),
                   self._model_json["algo"] + " prediction")
        j.poll()
        return h2o.get_frame(j.dest_key)

    def calibrate(self, calibration_model):
        """
        Calibrate a trained model with a supplied calibration model.

        Only tree-based models can be calibrated.

        :param calibration_model: a GLM model (for Platt Scaling) or Isotonic Regression model trained with the purpose
            of calibrating output of this model.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator
        >>> df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/ecology_model.csv")
        >>> df["Angaus"] = df["Angaus"].asfactor()
        >>> train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)
        >>> model = H2OGradientBoostingEstimator()
        >>> model.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)
        >>> isotonic_train = calib[["Angaus"]]
        >>> isotonic_train = isotonic_train.cbind(model.predict(calib)["p1"])
        >>> h2o_iso_reg = H2OIsotonicRegressionEstimator(out_of_bounds="clip")
        >>> h2o_iso_reg.train(training_frame=isotonic_train, x="p1", y="Angaus")
        >>> model.calibrate(h2o_iso_reg)
        >>> model.predict(train)
        """
        if has_extension(self, 'SupervisedTrees'):
            return self._calibrate(calibration_model)
        print("Only supervised tree-based models support model calibration")

    def is_cross_validated(self):
        """Return True if the model was cross-validated."""
        return self._is_xvalidated

    def xval_keys(self):
        """Return model keys for the cross-validated model."""
        return self._xval_keys

    def get_xval_models(self, key=None):
        """
        Return a Model object.

        :param key: If None, return all cross-validated models; otherwise return the model that key points to.

        :returns: A model or list of models.
        """
        return h2o.get_model(key) if key is not None else [h2o.get_model(k) for k in self._xval_keys]

    @property
    def xvals(self):
        """
        Return a list of the cross-validated models.

        :returns: A list of models.
        """
        return self.get_xval_models()

    def detach(self):
        self._id = None

    def deepfeatures(self, test_data, layer):
        """
        Return hidden layer details.

        :param test_data: Data to create a feature space on.
        :param layer: 0 index hidden layer.
        """
        if test_data is None: 
            raise ValueError("Must specify test data")
        if str(layer).isdigit():
            j = H2OJob(h2o.api("POST /4/Predictions/models/%s/frames/%s" % (self._id, test_data.frame_id),
                               data={"deep_features_hidden_layer": layer}), "deepfeatures")
        else:
            j = H2OJob(h2o.api("POST /4/Predictions/models/%s/frames/%s" % (self._id, test_data.frame_id),
                               data={"deep_features_hidden_layer_name": layer}), "deepfeatures")
        j.poll()
        return h2o.get_frame(j.dest_key)

    def weights(self, matrix_id=0):
        """
        Return the frame for the respective weight matrix.

        :param matrix_id: an integer, ranging from 0 to number of layers, that specifies the weight matrix to return.

        :returns: an H2OFrame which represents the weight matrix identified by ``matrix_id``.
        """
        num_weight_matrices = len(self._model_json["output"]["weights"])
        if matrix_id not in list(range(num_weight_matrices)):
            raise ValueError(
                "Weight matrix does not exist. Model has {0} weight matrices (0-based indexing), but matrix {1} "
                "was requested.".format(num_weight_matrices, matrix_id))
        return h2o.get_frame(self._model_json["output"]["weights"][matrix_id]["URL"].split("/")[3])

    def biases(self, vector_id=0):
        """
        Return the frame for the respective bias vector.

        :param vector_id: an integer, ranging from 0 to number of layers, that specifies the bias vector to return.

        :returns: an H2OFrame which represents the bias vector identified by ``vector_id``.
        """
        num_bias_vectors = len(self._model_json["output"]["biases"])
        if vector_id not in list(range(num_bias_vectors)):
            raise ValueError(
                "Bias vector does not exist. Model has {0} bias vectors (0-based indexing), but vector {1} "
                "was requested.".format(num_bias_vectors, vector_id))
        return h2o.get_frame(self._model_json["output"]["biases"][vector_id]["URL"].split("/")[3])

    def normmul(self):
        """Normalization/Standardization multipliers for numeric predictors."""
        return self._model_json["output"]["normmul"]

    def normsub(self):
        """Normalization/Standardization offsets for numeric predictors."""
        return self._model_json["output"]["normsub"]

    def respmul(self):
        """Normalization/Standardization multipliers for numeric response."""
        return self._model_json["output"]["normrespmul"]

    def respsub(self):
        """Normalization/Standardization offsets for numeric response."""
        return self._model_json["output"]["normrespsub"]

    def catoffsets(self):
        """Categorical offsets for one-hot encoding."""
        return self._model_json["output"]["catoffsets"]

    def training_model_metrics(self):
        """
        Return training model metrics for any model.
        """
        return self._model_json["output"]["training_metrics"]._metric_json
    
    def model_performance(self, test_data=None, train=False, valid=False, xval=False, auc_type=None, 
                          auuc_type=None, auuc_nbins=-1):
        """
        Generate model metrics for this model on ``test_data``.

        :param H2OFrame test_data: Data set for which model metrics shall be computed against. All three of train,
            valid and xval arguments are ignored if ``test_data`` is not ``None``.
        :param bool train: Report the training metrics for the model.
        :param bool valid: Report the validation metrics for the model.
        :param bool xval: Report the cross-validation metrics for the model. If train and valid are ``True``, then it
            defaults to True.
        :param String auc_type: Change default AUC type for multinomial classification AUC/AUCPR calculation when ``test_data`` is not ``None``. One of:

            - ``"auto"``
            - ``"none"`` (default)
            - ``"macro_ovr"``
            - ``"weighted_ovr"``
            - ``"macro_ovo"``
            - ``"weighted_ovo"``

            If type is ``"auto"`` or ``"none"``, AUC and AUCPR are not calculated.
        :param String auuc_type: Change default AUUC type for uplift binomial classification AUUC calculation 
            when ``test_data`` is not None. One of:

                - ``"AUTO"`` (default)
                - ``"qini"``
                - ``"lift"``
                - ``"gain"``
                
            If type is ``"auto"`` ("qini"), AUUC is calculated. 
        :param int auuc_nbins: Number of bins for calculation AUUC. Defaults to ``-1``, which means 1000.
        :returns: An instance of :class:`~h2o.model.metrics_base.MetricsBase` or one of its subclass.
        """
        
        if test_data is None:
            if auc_type is not None and auc_type != "none":
                print("WARNING: The `auc_type` parameter is set but it is not used because the `test_data` parameter is None.")
            if auuc_type is not None:
                print("WARNING: The `auuc_type` parameter is set but it is not used because the `test_data` parameter is None.")
            if train: 
                return self._model_json["output"]["training_metrics"]
            if valid: 
                return self._model_json["output"]["validation_metrics"]
            if xval: 
                return self._model_json["output"]["cross_validation_metrics"]
            return self._model_json["output"]["training_metrics"]
        else:  # cases dealing with test_data not None
            if not isinstance(test_data, h2o.H2OFrame):
                raise ValueError("`test_data` must be of type H2OFrame.  Got: " + type(test_data))
            if (self._model_json["response_column_name"] is not None) and not(self._model_json["response_column_name"] in test_data.names):
                print("WARNING: Model metrics cannot be calculated and metric_json is empty due to the absence of the response column in your dataset.")
                return
            if auc_type is not None:
                assert_is_type(auc_type, Enum("auto", "none", "macro_ovr", "weighted_ovr", "macro_ovo", "weighted_ovo"))
                res = h2o.api("POST /3/ModelMetrics/models/%s/frames/%s" % (self.model_id, test_data.frame_id), 
                              data={"auc_type": auc_type})
            elif auuc_type is not None:
                assert_is_type(auuc_type, Enum("AUTO", "qini", "gain", "lift"))
                if (self._model_json["treatment_column_name"] is not None) and not(self._model_json["treatment_column_name"] in test_data.names):
                    print("WARNING: Model metrics cannot be calculated and metric_json is empty due to the absence of the treatment column in your dataset.")
                    return
                res = h2o.api("POST /3/ModelMetrics/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                              data={"auuc_type": auuc_type, "auuc_nbins": auuc_nbins})
            else:
                res = h2o.api("POST /3/ModelMetrics/models/%s/frames/%s" % (self.model_id, test_data.frame_id))
            # FIXME need to do the client-side filtering...  (PUBDEV-874)
            raw_metrics = None
            for mm in res["model_metrics"]:
                if mm["frame"] is not None and mm["frame"]["name"] == test_data.frame_id:
                    raw_metrics = mm
                    break
            return self._metrics_class_valid(raw_metrics, algo=self._model_json["algo"])

    def scoring_history(self):
        """
        Retrieve Model Score History.

        :returns: The score history as an H2OTwoDimTable or a Pandas DataFrame.
        """
        model = self._model_json["output"]
        if "scoring_history" in model and model["scoring_history"] is not None:
            return model["scoring_history"].as_data_frame()
        print("No score history for this model")
        
    def negative_log_likelihood(self):
        """
        Retrieve model negative likelihood function value from scoring history if exists for GLM model
        
        :return: the negative likelihood function value
        """
        return self._extract_scoring_history("negative_log_likelihood")

    def average_objective(self):
        """
        Retrieve model average objective function value from scoring history if exists for GLM model.  If there is no 
        regularization, the avearge objective value*obj_reg should equal the neg_log_likelihood value.
        
        :return: the average objective function value
        """
        return self._extract_scoring_history("objective")

        
    def _extract_scoring_history(self, value):
        model = self._model_json["output"]
        if 'glm' == self.algo:
            if self.actual_params['generate_scoring_history'] is True:
                if "scoring_history" in model and model["scoring_history"] is not None:
                    sc_history = model["scoring_history"]
                    col_header = sc_history._col_header
                    if value in col_header:
                        index_val = col_header.index(value)
                        scLen = len(sc_history._cell_values)
                        return sc_history._cell_values[scLen-1][index_val]
                    else:
                        print("{0} not available.".format(value))
                else:
                    raise H2OValueError("GLM scoring_history is missing.  Cannot extract {0}.".value)
            else:
                raise H2OValueError("For now, need to set generate_scoring_history to True to get {0}".format(value))
        else:
            raise H2OValueError("{0} is only for GLM models.".format(value))



    def ntrees_actual(self):
        """
        Returns actual number of trees in a tree model. If early stopping is enabled, GBM can reset the ntrees value.
        In this case, the actual ntrees value is less than the original ntrees value a user set before
        building the model.
    
        Type: ``float``
        """
        # For now, redirect to h2o.model.extensions.trees for models that support the feature, and print legacy message for others..
        # Later, the method will be exposed only for models supporting the feature.
        if has_extension(self, 'Trees'):
            return self._ntrees_actual()
        print("No actual number of trees for this model")    

    def feature_interaction(self, max_interaction_depth=100, max_tree_depth=100, max_deepening=-1, path=None):
        """
        Feature interactions and importance, leaf statistics and split value histograms in a tabular form.
        Available for XGBoost and GBM.

        Metrics:

        - Gain - Total gain of each feature or feature interaction.
        - FScore - Amount of possible splits taken on a feature or feature interaction.
        - wFScore - Amount of possible splits taken on a feature or feature interaction weighed by the probability of the splits to take place.
        - Average wFScore - wFScore divided by FScore.
        - Average Gain - Gain divided by FScore.
        - Expected Gain - Total gain of each feature or feature interaction weighed by the probability to gather the gain.
        - Average Tree Index
        - Average Tree Depth

        :param max_interaction_depth: Upper bound for extracted feature interactions depth. Defaults to ``100``.
        :param max_tree_depth: Upper bound for tree depth. Defaults to ``100``.
        :param max_deepening: Upper bound for interaction start deepening (zero deepening => interactions 
            starting at root only). Defaults to ``-1.``
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
        # For now, redirect to h2o.model.extensions.feature_interaction for models that support the feature, and print legacy message for others..
        # Later, the method will be exposed only for models supporting the feature.
        if has_extension(self, 'FeatureInteraction'):
            return self._feature_interaction(max_interaction_depth=max_interaction_depth, 
                                             max_tree_depth=max_tree_depth, 
                                             max_deepening=max_deepening, 
                                             path=path)
        print("No calculation available for this model")

    def h(self, frame, variables):
        """
        Calculates Friedman and Popescu's H statistics, in order to test for the presence of an interaction between specified variables in H2O GBM and XGB models.
        H varies from ``0`` to ``1``. It will have a value of ``0`` if the model exhibits no interaction between specified variables and a correspondingly larger value for a 
        stronger interaction effect between them. ``NaN`` is returned if a computation is spoiled by weak main effects and rounding errors.
        
        See Jerome H. Friedman and Bogdan E. Popescu, 2008, "Predictive learning via rule ensembles", *Ann. Appl. Stat.*
        **2**:916-954, http://projecteuclid.org/download/pdfview_1/euclid.aoas/1223908046, s. 8.1.

        
        :param frame: the frame that current model has been fitted to.
        :param variables: variables of the interest.
        :return: H statistic of the variables.
        
        :examples:
        
        >>> prostate_train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate_train.csv")
        >>> prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
        >>> gbm_h2o = H2OGradientBoostingEstimator(ntrees=100, learn_rate=0.1,
        >>>                                 max_depth=5,
        >>>                                 min_rows=10,
        >>>                                 distribution="bernoulli")
        >>> gbm_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)
        >>> h = gbm_h2o.h(prostate_train, ['DPROS','DCAPS'])
        """
        if has_extension(self, 'HStatistic'):
            return self._h(frame=frame, variables=variables)
        print("No calculation available for this model")

    def update_tree_weights(self, frame, weights_column):
        """
        Re-calculates tree-node weights based on the provided dataset. Modifying node weights will affect how
        contribution predictions (Shapley values) are calculated. This can be used to explain the model
        on a curated sub-population of the training dataset.

        :param frame: frame that will be used to re-populate trees with new observations and to collect per-node weights. 
        :param weights_column: name of the weight column (can be different from training weights).
        """
        if has_extension(self, 'SupervisedTrees'):
            return self._update_tree_weights(frame, weights_column)
        print("Only supervised tree-based models support tree-reweighting")

    def cross_validation_metrics_summary(self):
        """
        Retrieve Cross-Validation Metrics Summary.

        :returns: The cross-validation metrics summary as an H2OTwoDimTable
        """
        model = self._model_json["output"]
        if "cross_validation_metrics_summary" in model and model["cross_validation_metrics_summary"] is not None:
            return model["cross_validation_metrics_summary"]
        print("No cross-validation metrics summary for this model")
        
    def varimp(self, use_pandas=False):
        """
        Pretty print the variable importances, or return them in a list.

        :param bool use_pandas: If ``True``, then the variable importances will be returned as a pandas data frame.

        :returns: A list or Pandas DataFrame.
        """
        model = self._model_json["output"]
        if "variable_importances" in list(model.keys()) and model["variable_importances"]:
            vals = model["variable_importances"].cell_values
            header = model["variable_importances"].col_header
                
            if use_pandas and can_use_pandas():
                import pandas
                return pandas.DataFrame(vals, columns=header)
            else:
                return vals
        else:
            print("Warning: This model doesn't have variable importances")

    def residual_deviance(self, train=False, valid=False, xval=None):
        """
        Retreive the residual deviance if this model has the attribute, or None otherwise.

        :param bool train: Get the residual deviance for the training set. If both train and valid are ``False``, then
            train is selected by default.
        :param bool valid: Get the residual deviance for the validation set. If both train and valid are ``True``, then
            train is selected by default.

        :returns: Return the residual deviance, or None if it is not present.
        """
        if xval: 
            raise H2OValueError("Cross-validation metrics are not available.")
        if valid and not train:
            return self._model_json["output"]["validation_metrics"].residual_deviance()
        else:
            return self._model_json["output"]["training_metrics"].residual_deviance()

    def residual_degrees_of_freedom(self, train=False, valid=False, xval=False):
        """
        Retreive the residual degress of freedom (dof) if this model has the attribute, or None otherwise.

        :param bool train: Get the residual dof for the training set. If both train and valid are ``False``, then train
            is selected by default.
        :param bool valid: Get the residual dof for the validation set. If both train and valid are ``True``, then train
            is selected by default.

        :returns: Return the residual dof, or None if it is not present.
        """
        if xval: 
            raise H2OValueError("Cross-validation metrics are not available.")
        if valid and not train:
            return self._model_json["output"]["validation_metrics"].residual_degrees_of_freedom()
        else:
            return self._model_json["output"]["training_metrics"].residual_degrees_of_freedom()

    def null_deviance(self, train=False, valid=False, xval=False):
        """
        Retreive the null deviance if this model has the attribute, or None otherwise.

        :param bool train: Get the null deviance for the training set. If both train and valid are ``False``, then train
            is selected by default.
        :param bool valid: Get the null deviance for the validation set. If both train and valid are ``True``, then train
            is selected by default.

        :returns: Return the null deviance, or None if it is not present.
        """
        if xval: 
            raise H2OValueError("Cross-validation metrics are not available.")
        if valid and not train:
            return self._model_json["output"]["validation_metrics"].null_deviance()
        else:
            return self._model_json["output"]["training_metrics"].null_deviance()

    def null_degrees_of_freedom(self, train=False, valid=False, xval=False):
        """
        Retreive the null degress of freedom (dof) if this model has the attribute, or None otherwise.

        :param bool train: Get the null dof for the training set. If both train and valid are ``False``, then train is
            selected by default.
        :param bool valid: Get the null dof for the validation set. If both train and valid are ``True``, then train is
            selected by default.

        :returns: Return the null dof, or None if it is not present.
        """
        if xval: 
            raise H2OValueError("Cross-validation metrics are not available.")
        if valid and not train:
            return self._model_json["output"]["validation_metrics"].null_degrees_of_freedom()
        else: 
            return self._model_json["output"]["training_metrics"].null_degrees_of_freedom()

    def pprint_coef(self):
        """Pretty print the coefficents table (includes normalized coefficients)."""
        print(self._model_json["output"]["coefficients_table"])  # will return None if no coefs!

    def get_variable_inflation_factors(self):
        if self.algo == 'glm':
            if self.parms['generate_variable_inflation_factors']:
                tbl = self._model_json["output"]["coefficients_table"]
                if tbl is None:
                    return None
                return {name: vif for name, vif in zip(tbl["names"], tbl["variable_inflation_factor"])}
            else:
                raise ValueError("variable inflation factors are generated only when "
                                 "generate_variable_inflation_factors is enabled.")
        else:
            raise ValueError("variable inflation factors are only found in GLM models for numerical predictors.")
        
    def coef(self):
        """
        Return the coefficients which can be applied to the non-standardized data.

        **Note**: ``standardize=True`` by default; when ``standardize=False``, then ``coef()`` will return the coefficients which are fit directly.
        """
        if (self._model_json["output"]['model_category']=="Multinomial") or \
            (self._model_json["output"]['model_category']=="Ordinal"):
            return self._fillMultinomialDict(False)
        else:
            tbl = self._model_json["output"]["coefficients_table"]
            if tbl is None:
                return None
            return {name: coef for name, coef in zip(tbl["names"], tbl["coefficients"])}

    def coef_norm(self):
        """
        Return coefficients fitted on the standardized data (requires ``standardize=True``, which is on by default).

        These coefficients can be used to evaluate variable importance.
        """
        if self._model_json["output"]["model_category"]=="Multinomial":
            return self._fillMultinomialDict(True)
        else:
            tbl = self._model_json["output"]["coefficients_table"]
            if tbl is None:
                return None
            return {name: coef for name, coef in zip(tbl["names"], tbl["standardized_coefficients"])}

    def coef_with_p_values(self):
        if self.algo == 'glm':
            if self.parms["compute_p_values"]["actual_value"]:
                return self._model_json["output"]["coefficients_table"]
            else:
                raise ValueError("p-values, z-values and std_error are not found in model.  Make sure to set "
                                 "compute_p_values=True.")
        else:
            raise ValueError("p-values, z-values and std_error are only found in GLM.")
        
    def _fillMultinomialDict(self, standardize=False):
        if self.algo == 'gam':
            tbl = self._model_json["output"]["coefficients_table"]
        else:
            tbl = self._model_json["output"]["coefficients_table_multinomials_with_class_names"]
        if tbl is None:
            return None
        coeff_dict = {} # contains coefficient names
        coeffNames = tbl["names"]
        all_col_header = tbl.col_header
        startIndex = 1
        endIndex = int((len(all_col_header)-1)/2+1)
        if standardize:
            startIndex = int((len(all_col_header)-1)/2+1) # start index for standardized coefficients
            endIndex = len(all_col_header)
        for nameIndex in list(range(startIndex, endIndex)):
            coeffList = tbl[all_col_header[nameIndex]]
            t1Dict = {name: coef for name, coef in zip(coeffNames, coeffList)}
            coeff_dict[all_col_header[nameIndex]]=t1Dict
        return coeff_dict

    def r2(self, train=False, valid=False, xval=False):
        """
        Return the R squared for this regression model.

        Will return :math:`R^2` for GLM Models. 

        The :math:`R^2` value is defined to be :math:`1 - MSE / var`, where var is computed as :math:`\sigma * \sigma`.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the R^2 value for the training data.
        :param bool valid: If ``valid=True``, then return the R^2 value for the validation data.
        :param bool xval:  If ``xval=True``, then return the R^2 value for the cross validation data.

        :returns: The R squared for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.r2()
        return list(m.values())[0] if len(m) == 1 else m

    def mse(self, train=False, valid=False, xval=False):
        """
        Get the Mean Square Error.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the MSE value for the training data.
        :param bool valid: If ``valid=True``, then return the MSE value for the validation data.
        :param bool xval:  If ``xval=True``, then return the MSE value for the cross validation data.

        :returns: The MSE for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.mse()
        return list(m.values())[0] if len(m) == 1 else m

    def rmse(self, train=False, valid=False, xval=False):
        """
        Get the Root Mean Square Error.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the RMSE value for the training data.
        :param bool valid: If ``valid=True``, then return the RMSE value for the validation data.
        :param bool xval:  If ``xval=True``, then return the RMSE value for the cross validation data.

        :returns: The RMSE for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.rmse()
        return list(m.values())[0] if len(m) == 1 else m

    def mae(self, train=False, valid=False, xval=False):
        """
        Get the Mean Absolute Error.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the MAE value for the training data.
        :param bool valid: If ``valid=True``, then return the MAE value for the validation data.
        :param bool xval:  If ``xval=True``, then return the MAE value for the cross validation data.

        :returns: The MAE for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.mae()
        return list(m.values())[0] if len(m) == 1 else m

    def rmsle(self, train=False, valid=False, xval=False):
        """
        Get the Root Mean Squared Logarithmic Error.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the RMSLE value for the training data.
        :param bool valid: If ``valid=True``, then return the RMSLE value for the validation data.
        :param bool xval:  If ``xval=True``, then return the RMSLE value for the cross validation data.

        :returns: The RMSLE for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.rmsle()
        return list(m.values())[0] if len(m) == 1 else m

    def logloss(self, train=False, valid=False, xval=False):
        """
        Get the Log Loss.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the log loss value for the training data.
        :param bool valid: If ``valid=True``, then return the log loss value for the validation data.
        :param bool xval:  If ``xval=True``, then return the log loss value for the cross validation data.

        :returns: The log loss for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.logloss()
        return list(m.values())[0] if len(m) == 1 else m

    def mean_residual_deviance(self, train=False, valid=False, xval=False):
        """
        Get the Mean Residual Deviances.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the Mean Residual Deviance value for the training data.
        :param bool valid: If ``valid=True``, then return the Mean Residual Deviance value for the validation data.
        :param bool xval:  If ``xval=True``, then return the Mean Residual Deviance value for the cross validation data.

        :returns: The Mean Residual Deviance for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.mean_residual_deviance()
        return list(m.values())[0] if len(m) == 1 else m

    def auc(self, train=False, valid=False, xval=False):
        """
        Get the AUC (Area Under Curve).

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the AUC value for the training data.
        :param bool valid: If ``valid=True``, then return the AUC value for the validation data.
        :param bool xval:  If ``xval=True``, then return the AUC value for the validation data.

        :returns: The AUC.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            m[k] = None if v is None else v.auc()
        return list(m.values())[0] if len(m) == 1 else m

    def aic(self, train=False, valid=False, xval=False):
        """
        Get the AIC (Akaike Information Criterium).

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the AIC value for the training data.
        :param bool valid: If ``valid=True``, then return the AIC value for the validation data.
        :param bool xval:  If ``xval=True``, then return the AIC value for the validation data.

        :returns: The AIC.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.aic()
        return list(m.values())[0] if len(m) == 1 else m

    def gini(self, train=False, valid=False, xval=False):
        """
        Get the Gini coefficient.

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval"

        :param bool train: If ``train=True``, then return the Gini Coefficient value for the training data.
        :param bool valid: If ``valid=True``, then return the Gini Coefficient value for the validation data.
        :param bool xval:  If ``xval=True``, then return the Gini Coefficient value for the cross validation data.

        :returns: The Gini Coefficient for this binomial model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.gini()
        return list(m.values())[0] if len(m) == 1 else m
    
    def aucpr(self, train=False, valid=False, xval=False):
        """
        Get the aucPR (Area Under PRECISION RECALL Curve).

        If all are ``False`` (default), then return the training metric value.
        If more than one option is set to ``True``, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If ``train=True``, then return the aucpr value for the training data.
        :param bool valid: If ``valid=True``, then return the aucpr value for the validation data.
        :param bool xval:  If ``xval=True``, then return the aucpr value for the validation data.

        :returns: The aucpr.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): 
            if v is not None and not is_type(v, h2o.model.metrics.binomial.H2OBinomialModelMetrics) and not is_type(v,
                                                                                                                    h2o.model.metrics.multinomial.H2OMultinomialModelMetrics):
                raise H2OValueError("aucpr() is only available for Binomial and Multinomial classifiers. For Multinomial classifiers is available average PR AUC value, default is Weighted One-to-Rest PR AUC.")
            m[k] = None if v is None else v.aucpr()
        return list(m.values())[0] if len(m) == 1 else m

    @deprecated_fn(replaced_by=aucpr)
    def pr_auc(self, train=False, valid=False, xval=False):
        pass

    def download_model(self, path="", filename=None):
        """
        Download an H2O Model object to disk.
    
        :param path: a path to the directory where the model should be saved.
        :param filename: a filename for the saved model.
    
        :returns: the path of the downloaded model.
        """
        assert_is_type(path, str)
        return h2o.download_model(self, path, filename=filename)

    def download_pojo(self, path="", get_genmodel_jar=False, genmodel_name=""):
        """
        Download the POJO for this model to the directory specified by path.

        If path is an empty string, then dump the output to screen.

        :param path:  An absolute path to the directory where POJO should be saved.
        :param get_genmodel_jar: if True, then also download h2o-genmodel.jar and store it in folder ``path``.
        :param genmodel_name: Custom name of genmodel jar
        
        :returns: name of the POJO file written.
        """
        assert_is_type(path, str)
        assert_is_type(get_genmodel_jar, bool)
        path = path.rstrip("/")
        return h2o.download_pojo(self, path, get_jar=get_genmodel_jar, jar_name=genmodel_name)

    def download_mojo(self, path=".", get_genmodel_jar=False, genmodel_name=""):
        """
        Download the model in MOJO format.

        :param path: the path where MOJO file should be saved.
        :param get_genmodel_jar: if True, then also download h2o-genmodel.jar and store it in folder ``path``.
        :param genmodel_name: Custom name of genmodel jar
        
        :returns: name of the MOJO file written.
        """
        assert_is_type(path, str)
        assert_is_type(get_genmodel_jar, bool)

        if not self.have_mojo:
            raise H2OValueError("Export to MOJO not supported")

        if get_genmodel_jar:
            if genmodel_name == "":
                h2o.api("GET /3/h2o-genmodel.jar", save_to=os.path.join(path, "h2o-genmodel.jar"))
            else:
                h2o.api("GET /3/h2o-genmodel.jar", save_to=os.path.join(path, genmodel_name))
        return h2o.api("GET /3/Models/%s/mojo" % self.model_id, save_to=path)

    def save_mojo(self, path="", force=False, filename=None):
        """
        Save an H2O Model as MOJO (Model Object, Optimized) to disk.

        :param path: a path to save the model at (e.g. hdfs, s3, local).
        :param force: if ``True``, overwrite destination directory in case it exists, or throw exception if set to ``False``.
        :param filename: a filename for the saved model (file type is always .zip).

        :returns str: the path of the saved model
        """
        assert_is_type(path, str)
        assert_is_type(force, bool)
        if not self.have_mojo:
            raise H2OValueError("Export to MOJO not supported")
        if filename is None:
            filename = self.model_id + ".zip"
        else:
            assert_is_type(filename, str)
        path = os.path.join(os.getcwd() if path == "" else path, filename)
        return h2o.api("GET /99/Models.mojo/%s" % self.model_id, data={"dir": path, "force": force})["dir"]

    def save_model_details(self, path="", force=False, filename=None):
        """
        Save Model Details of an H2O Model in JSON Format to disk.

        :param path: a path to save the model details at (e.g. hdfs, s3, local).
        :param force: if ``True``, overwrite destination directory in case it exists, or throw exception if set to ``False``.
        :param filename: a filename for the saved model (file type is always .json).

        :returns str: the path of the saved model details
        """
        assert_is_type(path, str)
        assert_is_type(force, bool)
        if filename is None:
            filename = self.model_id + ".json"
        else:
            assert_is_type(filename, str)
        path = os.path.join(os.getcwd() if path == "" else path, filename)
        return h2o.api("GET /99/Models/%s/json" % self.model_id, data={"dir": path, "force": force})["dir"]

    @staticmethod
    def _get_metrics(o, train, valid, xval):
        # noinspection PyProtectedMember
        output = o._model_json["output"]
        metrics = {}
        if train: 
            metrics["train"] = output["training_metrics"]
        if valid: 
            metrics["valid"] = output["validation_metrics"]
        if xval: 
            metrics["xval"] = output["cross_validation_metrics"]
        if len(metrics) == 0: 
            metrics["train"] = output["training_metrics"]
        return metrics

    @deprecated_params({'save_to_file': 'save_plot_path'})
    def partial_plot(self, data, cols=None, destination_key=None, nbins=20, weight_column=None,
                     plot=True, plot_stddev=True, figsize=(7, 10), server=False, include_na=False, user_splits=None,
                     col_pairs_2dpdp=None, save_plot_path=None, row_index=None, targets=None):
        """
        Create partial dependence plot which gives a graphical depiction of the marginal effect of a variable on the
        response. The effect of a variable is measured in change in the mean response.

        :param H2OFrame data: An H2OFrame object used for scoring and constructing the plot.
        :param cols: Feature(s) for which partial dependence will be calculated.
        :param destination_key: A key reference to the created partial dependence tables in H2O.
        :param nbins: Number of bins used. For categorical columns make sure the number of bins exceed the level count. If you enable ``add_missing_NA``, the returned length will be nbin+1.
        :param weight_column: A string denoting which column of data should be used as the weight column.
        :param plot: A boolean specifying whether to plot partial dependence table.
        :param plot_stddev: A boolean specifying whether to add std err to partial dependence plot.
        :param figsize: Dimension/size of the returning plots, adjust to fit your output cells.
        :param server: Specify whether to activate matplotlib "server" mode. In this case, the plots are saved to a file instead of being rendered.
        :param include_na: A boolean specifying whether missing value should be included in the Feature values.
        :param user_splits: A dictionary containing column names as key and user defined split values as value in a list.
        :param col_pairs_2dpdp: List containing pairs of column names for 2D pdp
        :param save_plot_path: Fully qualified name to an image file the resulting plot should be saved to (e.g. ``'/home/user/pdpplot.png'``). The 'png' postfix might be omitted. If the file already exists, it will be overridden. Plot is only saved if ``plot=True``.
        :param row_index: Row for which partial dependence will be calculated instead of the whole input frame.
        :param targets: Target classes for multiclass model.

        :returns: Plot and list of calculated mean response tables for each feature requested + the resulting plot (can be accessed using ``result.figure()``).
        """
        if not isinstance(data, h2o.H2OFrame): raise ValueError("Data must be an instance of H2OFrame.")
        num_1dpdp = 0
        num_2dpdp = 0
        if cols is not None:
            assert_is_type(cols, [str])
            num_1dpdp = len(cols)
        if col_pairs_2dpdp is not None:
            assert_is_type(col_pairs_2dpdp, [[str, str]])
            num_2dpdp = len(col_pairs_2dpdp)
            
        if cols is None and col_pairs_2dpdp is None:
            raise ValueError("Must specify either cols or col_pairs_2dpd to generate partial dependency plots.")

        if col_pairs_2dpdp and targets and len(targets) > 1:
            raise ValueError("Multinomial 2D Partial Dependency is available only for one target.")
            
        assert_is_type(destination_key, None, str)
        assert_is_type(nbins, int)
        assert_is_type(plot, bool)
        assert_is_type(figsize, (int, int))

        # Check cols specified exist in frame data
        if cols is not None:
            for xi in cols:
                if xi not in data.names:
                    raise H2OValueError("Column %s does not exist in the training frame." % xi)
        if col_pairs_2dpdp is not None:
            for oneP in col_pairs_2dpdp:
                if oneP[0] not in data.names:
                    raise H2OValueError("Column %s does not exist in the training frame." % oneP[0])
                if oneP[1] not in data.names:
                    raise H2OValueError("Column %s does not exist in the training frame." % oneP[1])
                if oneP[0] is oneP[1]:
                    raise H2OValueError("2D pdp must be with different columns.")
        if isinstance(weight_column, int) and not (weight_column == -1):
            raise H2OValueError("Weight column should be a column name in your data frame.")
        elif isinstance(weight_column, str): # index is a name
            if weight_column not in data.names:
                raise H2OValueError("Column %s does not exist in the data frame" % weight_column)
            weight_column = data.names.index(weight_column)
        
        if row_index is not None:
            if not isinstance(row_index, int):
                raise H2OValueError("Row index should be of type int.")
        else:
            row_index = -1
            
        if targets is not None:
            assert_is_type(targets, list)
            for i in targets:
                assert_is_type(i, str)
            num_1dpdp = num_1dpdp
            num_2dpdp = num_2dpdp
        
        kwargs = {}
        kwargs["cols"] = cols
        kwargs["model_id"] = self.model_id
        kwargs["frame_id"] = data.frame_id
        kwargs["nbins"] = nbins
        kwargs["destination_key"] = destination_key
        kwargs["weight_column_index"] = weight_column
        kwargs["add_missing_na"] = include_na
        kwargs["row_index"] = row_index
        kwargs["col_pairs_2dpdp"] = col_pairs_2dpdp
        if targets:
            kwargs["targets"] = targets

        self.__generate_user_splits(user_splits, data, kwargs)
        json = H2OJob(h2o.api("POST /3/PartialDependence/", data=kwargs),  job_type="PartialDependencePlot").poll()
        json = h2o.api("GET /3/PartialDependence/%s" % json.dest_key)

        # Extract partial dependence data from json response
        pps = json["partial_dependence_data"]

        # Plot partial dependence plots using matplotlib
        return self.__generate_partial_plots(num_1dpdp, num_2dpdp, plot, server, pps, figsize, 
                                             col_pairs_2dpdp, data, nbins,
                                             kwargs["user_cols"], kwargs["num_user_splits"], 
                                             plot_stddev, cols, save_plot_path, row_index, targets, include_na)

    def __generate_user_splits(self, user_splits, data, kwargs):
        # extract user defined split points from dict user_splits into an integer array of column indices
        # and a double array of user define values for the corresponding columns
        if user_splits is not None and len(user_splits) > 0:
            if not(isinstance(user_splits, dict)):
                raise H2OValueError("user_splits must be a Python dict.")
            else:
                user_cols = []
                user_values = []
                user_num_splits = []
                data_ncol = data.ncol
                column_names = data.names
                for colKey,val in user_splits.items():
                    if is_type(colKey, str) and colKey in column_names:
                        user_cols.append(colKey)
                    elif isinstance(colKey, int) and colKey < data_ncol:
                        user_cols.append(column_names[colKey])
                    else:
                        raise H2OValueError("Column names/indices used in user_splits are not valid.  They "
                                                "should be chosen from the columns of your data set.")

                    if data[colKey].isfactor()[0] or data[colKey].isnumeric()[0] or data.type(colKey) == "time": # replace enum string with actual value
                        nVal = len(val)
                        if data[colKey].isfactor()[0]:
                            domains = data[colKey].levels()[0]

                            numVal = [0]*nVal
                            for ind in range(nVal):
                                if val[ind] in domains:
                                    numVal[ind] = domains.index(val[ind])
                                else:
                                    raise H2OValueError("Illegal enum value {0} encountered.  To include missing"
                                                            " values in your feature values, set include_na to "
                                                            "True.".format(val[ind]))

                            user_values.extend(numVal)
                        else:
                            user_values.extend(val)
                        user_num_splits.append(nVal)
                    else:
                        raise H2OValueError("Partial dependency plots are generated for numerical and categorical "
                                                "columns only.")
                kwargs["user_cols"] = user_cols
                kwargs["user_splits"] = user_values
                kwargs["num_user_splits"] = user_num_splits
        else:
            kwargs["user_cols"] = None
            kwargs["user_splits"] = None
            kwargs["num_user_splits"] = None

    def __generate_partial_plots(self, num_1dpdp, num_2dpdp, plot, server, pps, figsize, col_pairs_2dpdp, data, nbins,
                                 user_cols, user_num_splits, plot_stddev, cols, save_to_file, row_index, targets, include_na):
        # Plot partial dependence plots using matplotlib
        to_fig = num_1dpdp + num_2dpdp
        if plot and to_fig > 0:     # plot 1d pdp for now
            plt = get_matplotlib_pyplot(server)
            cm = get_matplotlib_cm("Partial dependency plots")
            if not plt: 
                return decorate_plot_result(res=pps, figure=RAISE_ON_FIGURE_ACCESS)
            import matplotlib.gridspec as gridspec
            fig = plt.figure(figsize=figsize)
            gxs = gridspec.GridSpec(to_fig, 1)
            if num_2dpdp > 0: # 2d pdp requested
                axes_3d = get_mplot3d_axes("2D partial plots")
            fig_plotted = False  # indicated number of figures plotted
            data_index = 0
            target = None
            if targets and len(targets) == 1:
                target = targets[0]
            for i in range(to_fig):
                if i >= num_1dpdp:  # plot 2D pdp
                    if axes_3d is None or cm is None or plt is None:    # quit if cannot find toolbox
                        break
                    fig_plotted = self.__plot_2d_pdp(fig, col_pairs_2dpdp, gxs, num_1dpdp, data, pps[i], nbins,
                                                     user_cols, user_num_splits, plot_stddev, cm, i, row_index)                  
                else:  # plot 1D pdp
                    col = cols[i]
                    if targets is None or target:
                        fig_plotted = self.__plot_1d_pdp(col, i, data, pps[i], fig, gxs, plot_stddev, row_index, target, include_na)
                    else:
                        fig_plotted = self.__plot_1d_pdp_multinomial(col, i, data, pps, data_index, fig, gxs, cm, 
                                                                     plot_stddev, row_index, targets, include_na)
                        data_index = data_index + len(targets)
            if fig_plotted:
                fig.tight_layout(pad=0.4, w_pad=0.5, h_pad=1.0)
            else:
                print("No partial plot is generated and/or saved.  You may be missing toolboxes like "
                      "mpl_toolkits.mplot3d or matplotlib.")
            if (save_to_file is not None) and fig_plotted:  # only save when a figure is actually plotted
                plt.savefig(save_to_file)
            return decorate_plot_result(res=pps, figure=fig)
        else:
            return decorate_plot_result(res=pps)

    def __plot_2d_pdp(self, fig, col_pairs_2dpdp, gxs, num_1dpdp, data, pp, nbins, user_cols, user_num_splits, 
                      plot_stddev, cm, i, row_index):
        ax = fig.add_subplot(gxs[i], projection='3d')
        col_pairs = col_pairs_2dpdp[i-num_1dpdp]
        x = self.__grab_values(pp, 0, data, col_pairs[0], ax) # change to numpy 2d_array
        y = self.__grab_values(pp, 1, data, col_pairs[1], ax)
        X,Y,Z = self.__pred_for_3d(x, y, pp[2], col_pairs, nbins, user_cols, user_num_splits)

        zupper = [a + b for a, b in zip(pp[2], pp[3])]  # pp[1] is mean, pp[2] is std
        zlower = [a - b for a, b in zip(pp[2], pp[3])]
        _,_,Zupper = self.__pred_for_3d(x, y, zupper, col_pairs, nbins, user_cols, user_num_splits)
        _,_,Zlower = self.__pred_for_3d(x, y, zlower, col_pairs, nbins, user_cols, user_num_splits)
        ax.plot_surface(X, Y, Z, cmap=cm.coolwarm,linewidth=1, antialiased=False, alpha=0.5, edgecolor='k')
        if plot_stddev:
            zupper = [a + b for a, b in zip(pp[2], pp[3])]  # pp[1] is mean, pp[2] is std
            zlower = [a - b for a, b in zip(pp[2], pp[3])]
            _,_,Zupper = self.__pred_for_3d(x,y,zupper, col_pairs, nbins, user_cols, user_num_splits)
            _,_,Zlower = self.__pred_for_3d(x,y,zlower, col_pairs, nbins, user_cols, user_num_splits)
            ax.plot_surface(X, Y, Zupper, cmap=cm.coolwarm,linewidth=0.2, antialiased=False, alpha=0.3, edgecolor='y')
            ax.plot_surface(X, Y, Zlower, cmap=cm.coolwarm,linewidth=0.2, antialiased=False, alpha=0.3, edgecolor='g')
        ax.set_xlabel(col_pairs[0])
        ax.set_xlim(min(x), max(x))
        ax.set_ylabel(col_pairs[1])
        ax.set_ylim(min(y), max(y))
        ax.set_zlabel('Partial dependence')
        title = '2D partial dependence plot for '+col_pairs[0] + ' and '+col_pairs[1]
        if row_index >= 0:
            title += ' and row index {}'.format(row_index)
        ax.set_title(title)
        return True
    
    def __plot_1d_pdp(self, col, i, data, pp, fig, gxs, plot_stddev, row_index, target=None, include_na=False):
        cat = data[col].isfactor()[0]
        axs = fig.add_subplot(gxs[i])
        self.__set_axs_1d(axs, plot_stddev, cat, pp, col, row_index, target, include_na) 
        return True
    
    def __plot_1d_pdp_multinomial(self, col, i, data, pps, data_start_index, fig, gxs, cm, plot_stddev, row_index, 
                                    targets, include_na):
        cat = data[col].isfactor()[0]
        axs = fig.add_subplot(gxs[i])
        self.__set_axs_1d_multinomial(axs, cm, plot_stddev, cat, pps, data_start_index, col, row_index, targets, include_na)
        return True
        
    # change x, y, z to be 2-D numpy arrays in order to plot it.
    # note that, x stays at one value for the duration of y value changes.
    def __pred_for_3d(self, x, y, z, colPairs, nbins, user_cols, user_num_splits):
        # deal with y axis first
        if not can_use_numpy():
            raise ImportError("numpy is required for 3D partial plots.")
        import numpy as np
        ycol = colPairs[1]
        nBins = nbins
        if user_cols is not None and ycol in user_cols:
            ind = user_cols.index(ycol)
            nBins = user_num_splits[ind]
        nrow = int(len(x)/nBins)
        X = np.transpose(np.array(x).reshape(nrow, nBins))
        Y = np.transpose(np.array(y).reshape(nrow, nBins))
        Z = np.transpose(np.array(z).reshape(nrow, nBins))
        return X,Y,Z
    
    def __grab_values(self, pp, index, data, col, axs):
        cat = data[col].isfactor()[0]
        if cat:
            labels = pp[index]
            uniqueL = list(set(labels))
            x = range(len(uniqueL))
            xlab = [None]*len(uniqueL)
            for ind in range(len(uniqueL)):
                xlab[ind] = labels[labels.index(uniqueL[ind])]

            # replace string enum labels with integer values
            xext = [None]*len(labels)
            for ind in range(len(labels)):
                xext[ind] = labels.index(labels[ind])
                
            if index == 0:    # x-axis
                axs.set_xticks(x)
                axs.set_xticklabels(xlab)
            else:   # y-axis
                axs.set_yticks(x)
                axs.set_yticklabels(xlab)
            axs.margins(0.2) 
            return xext
        else:
            return pp[index]
        
    def __set_axs_1d(self, axs, plot_stddev, cat, pp, col, row_index, target, include_na):
        if not can_use_numpy():
            raise ImportError("numpy is required for partial plots.")
        import numpy as np
        pp_start_index = 0
        x = pp[pp_start_index]
        y = pp[pp_start_index+1]
        if len(x) == 1:
            fmt = 'o'
        else:
            fmt = '-'
            if isinstance(x[0], str):
                axs.set_xlim(0, len(x)-1)
            else:
                axs.set_xlim(min(x), max(x))
        if cat:
            labels = x  # 1d pdp, this is 0
            x = range(len(labels))
            fmt = "o"
            axs.set_xticks(x)
            axs.set_xticklabels(labels, rotation=45)
            axs.set_xlim(min(x) - 0.2, max(x) + 0.2)
        if plot_stddev:
            std = pp[pp_start_index+2]
            upper = np.array([a + b for a, b in zip(y, std)])  # pp[1] is mean, pp[2] is std
            lower = np.array([a - b for a, b in zip(y, std)])
            if cat:
                axs.errorbar(x, y, yerr=std, fmt=fmt, alpha=0.5, capsize=5, label=target)
            else:
                numline, = axs.plot(x, y, fmt, label=target)
            axs.fill_between(x, lower, upper, where=lower < upper, alpha=0.1, interpolate=False)
            axs.set_ylim(min(lower) - 0.2 * abs(min(lower)), max(upper) + 0.2 * abs(max(upper)))
        else:
            numline, = axs.plot(x, y, fmt, label=target)
            axs.set_ylim(min(y) - 0.2 * abs(min(y)), max(y) + 0.2 * abs(max(y)))
        if (not cat) and include_na:
            axs.plot(x, [y[np.argwhere(np.isnan(x))[0][0]]] * len(x), '--', color=numline._color,label="NAN")
            axs.legend() 
        title = "Partial Dependence Plot for {}".format(col)
        if target:
            title += " and class {}".format(target)
        if row_index >= 0:
            title += " and row index {}".format(row_index)
        axs.set_title(title)
        axs.set_xlabel(pp.col_header[pp_start_index])
        axs.set_ylabel(pp.col_header[pp_start_index+1])
        axs.xaxis.grid()
        axs.yaxis.grid()
        
    def __set_axs_1d_multinomial(self, axs, cm, plot_stddev, cat, pps, data_start_index, col, row_index, targets, include_na):
        if not can_use_numpy():
            raise ImportError("numpy is required for multinomial partial plots.")
        import numpy as np
        pp_start_index = 0
        pp = pps[data_start_index]
        x = pp[pp_start_index]
        y = pp[pp_start_index + 1]
        # get initial maximum and minimum values to set xaxis and yaxis
        min_y = min(y)
        max_y = max(y)
        if plot_stddev:
            min_lower = min_y
            max_upper = max_y
        fmt = None
        if cat:  # adjust x axis to categorical values
            labels = pp[pp_start_index]
            x = range(len(labels))
            axs.set_xticks(x)
            axs.set_xticklabels(labels, rotation=45)
            fmt = "o"
            axs.set_xlim(min(x) - 0.2, max(x) + 0.2)
        else:
            axs.set_xlim(min(x), max(x))
        axs.set_xlabel(pp.col_header[pp_start_index])  # set x axis label
        axs.set_ylabel(pp.col_header[pp_start_index+1])  # set y axis label 
        cmap = cm.get_cmap("rainbow", len(targets))  # get color map 
        for i in range(len(targets)):
            pp = pps[data_start_index + i]
            y = pp[pp_start_index + 1]
            min_y = min(min_y, min(y))
            max_y = max(max_y, max(y))
            if plot_stddev:  # set std
                std = pp[pp_start_index + 2]
                upper = np.array([a + b for a, b in zip(y, std)])  # pp[1] is mean, pp[2] is std
                lower = np.array([a - b for a, b in zip(y, std)])
                min_lower = min(min_lower, min(lower))
                max_upper = max(max_upper, max(upper))
                if cat:
                    axs.errorbar(x, y, yerr=std, fmt=fmt, c=cmap(i), alpha=0.5, capsize=5, label=targets[i])
                else:
                    numline, = axs.plot(x, y, c=cmap(i), label=targets[i])
                axs.fill_between(x, lower, upper, where=lower < upper, facecolor=cmap(i), alpha=0.1, interpolate=False)
            else:
                numline, = axs.plot(x, y, c=cmap(i), marker=fmt, label=targets[i])
            if (not cat) and include_na:
                axs.plot(x, [y[np.argwhere(np.isnan(x))[0][0]]] * len(x), '--', color=numline._color,label=targets[i] + " NAN")
        if plot_stddev:
            axs.set_ylim(min_lower - 0.2 * abs(min_lower), max_upper + 0.2 * abs(max_upper))
        else:
            axs.set_ylim(min_y - 0.2 * abs(min_y), max_y + 0.2 * abs(max_y))
        axs.legend()
        title = "Partial Dependence Plot for {} and classes \n {}".format(col, ', '.join(targets))
        if row_index >= 0:
            title += " and row index {}".format(row_index)
        axs.set_title(title)
        axs.xaxis.grid()
        axs.yaxis.grid()
        
    def varimp_plot(self, num_of_features=None, server=False, save_plot_path=None):
        """
        Plot the variable importance for a trained model.

        :param num_of_features: the number of features shown in the plot (default is ``10`` or all if less than 10).
        :param server: if ``True``, set server settings to matplotlib and do not show the graph.
        :param save_plot_path: a path to save the plot via using matplotlib function savefig.

        :returns: object that contains the resulting figure (can be accessed using ``result.figure()``).
        """
        # For now, redirect to h2o.model.extensions.varimp for models that support the feature, and raise legacy error for others.
        # Later, the method will be exposed only for models supporting the feature.
        if has_extension(self, 'VariableImportance'):
            return self._varimp_plot(num_of_features=num_of_features, server=server, save_plot_path=save_plot_path)
        raise H2OValueError("Variable importance plot is not available for this type of model (%s)." % self.algo)

    def std_coef_plot(self, num_of_features=None, server=False, save_plot_path=None):
        """
        Plot a model's standardized coefficient magnitudes.

        :param num_of_features: the number of features shown in the plot.
        :param server: if ``True``, set server settings to matplotlib and show the graph.
        :param save_plot_path: a path to save the plot via using matplotlib function savefig.

        :returns: object that contains the resulting figure (can be accessed using ``result.figure()``).
        """
        # For now, redirect to h2o.model.extensions.std_coef for models that support the feature, and raise legacy error for others.
        # Later, the method will be exposed only for models supporting the feature.
        if has_extension(self, 'StandardCoef'):
            return self._std_coef_plot(num_of_features=num_of_features, server=server, save_plot_path=save_plot_path)
        raise H2OValueError("Standardized coefficient plot is not available for this type of model (%s)." % self.algo)

    @staticmethod
    def _check_targets(y_actual, y_predicted):
        """Check that y_actual and y_predicted have the same length.

        :param H2OFrame y_actual:
        :param H2OFrame y_predicted:

        :returns: None
        """
        if len(y_actual) != len(y_predicted):
            raise ValueError("Row mismatch: [{},{}]".format(len(y_actual), len(y_predicted)))

    def cross_validation_models(self):
        """
        Obtain a list of cross-validation models.

        :returns: list of H2OModel objects.
        """
        cvmodels = self._model_json["output"]["cross_validation_models"]
        if cvmodels is None: 
            return None
        m = []
        for p in cvmodels: m.append(h2o.get_model(p["name"]))
        return m

    def cross_validation_predictions(self):
        """
        Obtain the (out-of-sample) holdout predictions of all cross-validation models on their holdout data.

        Note that the predictions are expanded to the full number of rows of the training data, with 0 fill-in.

        :returns: list of H2OFrame objects.
        """
        preds = self._model_json["output"]["cross_validation_predictions"]
        if preds is None: 
            return None
        m = []
        for p in preds: m.append(h2o.get_frame(p["name"]))
        return m

    def cross_validation_holdout_predictions(self):
        """
        Obtain the (out-of-sample) holdout predictions of all cross-validation models on the training data.

        This is equivalent to summing up all H2OFrames returned by ``cross_validation_predictions``.

        :returns: H2OFrame
        """
        preds = self._model_json["output"]["cross_validation_holdout_predictions_frame_id"]
        if preds is None: 
            return None
        return h2o.get_frame(preds["name"])

    def cross_validation_fold_assignment(self):
        """
        Obtain the cross-validation fold assignment for all rows in the training data.

        :returns: H2OFrame
        """
        fid = self._model_json["output"]["cross_validation_fold_assignment_frame_id"]
        if fid is None: 
            return None
        return h2o.get_frame(fid["name"])

    def rotation(self):
        """
        Obtain the rotations (eigenvectors) for a PCA model.

        :return: H2OFrame
        """
        if self._model_json["algo"] != "pca":
            raise H2OValueError("This function is available for PCA models only")
        return self._model_json["output"]["eigenvectors"]

    def score_history(self):
        """DEPRECATED. Use :meth:`scoring_history` instead."""
        return self.scoring_history()

    def permutation_importance(self, frame, metric="AUTO", n_samples=10000, n_repeats=1, features=None, seed=-1, use_pandas=False):
        """
        Get Permutation Variable Importance.

        When ``n_repeats == 1``, the result is similar to the one from ``varimp()`` method (i.e. it contains
        the following columns: "Relative Importance", "Scaled Importance", and "Percentage").

        When ``n_repeats > 1``, the individual columns correspond to the permutation variable
        importance values from individual runs which corresponds to the "Relative Importance" and also
        to the distance between the original prediction error and prediction error using a frame with
        a given feature permuted.

        :param frame: training frame.
        :param metric: metric to be used. One of:

            - "AUTO"
            - "AUC"
            - "MAE"
            - "MSE"
            - "RMSE"
            - "logloss"
            - "mean_per_class_error"
            - "PR_AUC"

            Defaults to "AUTO".
        :param n_samples: number of samples to be evaluated. Use ``-1`` to use the whole dataset. Defaults to ``10 000``.
        :param n_repeats: number of repeated evaluations. Defaults to ``1``.
        :param features: features to include in the permutation importance. Use ``None`` to include all.
        :param seed: seed for the random generator. Use ``-1`` (default) to pick a random seed. 
        :param use_pandas: set to ``True`` to return pandas data frame.
        
        :return: H2OTwoDimTable or Pandas data frame
        """
        from h2o.two_dim_table import H2OTwoDimTable
        from h2o.frame import H2OFrame
        from h2o.expr import ExprNode
        from h2o.exceptions import H2OValueError
        from h2o.utils.shared_utils import can_use_pandas

        if type(frame) is not H2OFrame:
            raise H2OValueError("Frame is not H2OFrame")

        if self.actual_params["response_column"] not in frame.columns:
            raise H2OValueError("Frame must contain the response column!")

        if features is not None and len(features) == 0:
            features = None

        if n_samples < -1 or n_samples in (0, 1):
            raise H2OValueError("Argument n_samples has to be either -1 to use the whole frame or greater than 2!")

        if n_samples > frame.nrows:
            n_samples = -1

        if n_repeats < 1:
            raise H2OValueError("Argument n_repeats must be greater than 0!")

        assert_is_type(features, None, [str])
        if features is not None:
            not_in_frame = [f for f in features if f not in frame.columns]
            if len(not_in_frame) > 0:
                raise H2OValueError("Features " + ", ".join(not_in_frame) + " are not present in the provided frame!")

        existing_metrics = [k.lower() for k in self._model_json['output']['training_metrics']._metric_json.keys()]
        if metric.lower() not in ["auto"] + existing_metrics:
            raise H2OValueError("Metric " + metric + " doesn't exist for this model.")
        m_frame = H2OFrame._expr(ExprNode(
            "PermutationVarImp",
            self,
            frame,
            metric,
            n_samples,
            n_repeats,
            features,
            seed))
        if use_pandas and can_use_pandas():
            import pandas
            pd = h2o.as_list(m_frame)
            return pandas.DataFrame(pd, columns=pd.columns).set_index("Variable")
        else:
            def _replace_empty_str(row):
                return [
                    float("nan") if "" == elem else elem
                    for elem in row
                ]
            varimp = H2OTwoDimTable(
                table_header="Variable Importances",
                col_header=m_frame.columns,
                col_types=["string"] + ["double"] * (len(m_frame.columns) - 1),
                raw_cell_values=list(map(_replace_empty_str, zip(*m_frame.as_data_frame(use_pandas=False, header=False))))  # transpose
            )
            return varimp

    def permutation_importance_plot(self, frame, metric="AUTO", n_samples=10000, n_repeats=1, features=None, seed=-1,
                                    num_of_features=10, server=False, save_plot_path=None):
        """
        Plot Permutation Variable Importance. This method plots either a bar plot or, if ``n_repeats > 1``, a box plot and
        returns the variable importance table.

        :param frame: training frame.
        :param metric: metric to be used. One of:

            - "AUTO"
            - "AUC"
            - "MAE"
            - "MSE"
            - "RMSE"
            - "logloss"
            - "mean_per_class_error",
            - "PR_AUC"

            Defaults to "AUTO".
        :param n_samples: number of samples to be evaluated. Use ``-1`` to use the whole dataset. Defaults to ``10 000``.
        :param n_repeats: number of repeated evaluations. Defaults to ``1``.
        :param features: features to include in the permutation importance. Use ``None`` to include all.
        :param seed: seed for the random generator. Use ``-1`` (default) to pick a random seed. 
        :param num_of_features: number of features to plot. Defaults to ``10``.
        :param server: if ``True``, set server settings to matplotlib and do not show the plot.
        :param save_plot_path: a path to save the plot via using matplotlib function savefig.
        
        :return: object that contains H2OTwoDimTable with variable importance and the resulting figure (can be accessed using ``result.figure()``)
        """
        plt = get_matplotlib_pyplot(server)
        if not plt:
            return decorate_plot_result(figure=RAISE_ON_FIGURE_ACCESS)

        importance = self.permutation_importance(frame, metric, n_samples, n_repeats, features, seed, use_pandas=False)
        fig, ax = plt.subplots(1, 1, figsize=(14, 10))
        if n_repeats > 1:
            vi = sorted([{"feature": row[0], "mean": sum(row[1:])/(len(row)-1), "values": row[1:]}
                         for row in importance.cell_values],
                        key=lambda x: -x["mean"])[:num_of_features][::-1]
            ax.boxplot([x["values"] for x in vi], vert=False, labels=[x["feature"] for x in vi])
        else:
            importance_val = importance["Scaled Importance"]
            # specify bar centers on the y axis, but flip the order so largest bar appears at top
            pos = range(len(importance_val))[::-1]
            num_of_features = min(len(importance_val), num_of_features)
            plt.barh(pos[0:num_of_features], importance_val[0:num_of_features], align="center",
                     height=0.8, color="#1F77B4", edgecolor="none")
            plt.yticks(pos[0:num_of_features], importance["Variable"][0:num_of_features])  # col 0 is str: importance
            plt.ylim([min(pos[0:num_of_features]) - 1, max(pos[0:num_of_features]) + 1])
        # Hide the right and top spines, color others grey
        ax.spines["right"].set_visible(False)
        ax.spines["top"].set_visible(False)
        ax.spines["bottom"].set_color("#7B7B7B")
        ax.spines["left"].set_color("#7B7B7B")
        # Only show ticks on the left and bottom spines
        ax.yaxis.set_ticks_position("left")
        ax.xaxis.set_ticks_position("bottom")
        plt.title("Permutation Variable Importance: " + self.algo +
                  (" (" + metric.lower() + ")" if metric.lower() != "auto" else ""), fontsize=20)
        if not server:
            plt.show()
        if save_plot_path is not None:
            fig.savefig(fname=save_plot_path)    
        return decorate_plot_result(res=importance, figure=fig)

    def predicted_vs_actual_by_variable(self, frame, predicted, variable, use_pandas=False):
        """
        Calculates per-level mean of predicted value vs actual value for a given variable.

        In the basic setting, this function is equivalent to doing group-by on variable and calculating
        mean on predicted and actual. It also handles NAs in response and weights
        automatically.

        :param frame: input frame (can be ``training/test/...`` frame).
        :param predicted: frame of predictions for the given input frame.
        :param variable: variable to inspect.
        :param use_pandas: set true to return pandas data frame.

        :return: H2OTwoDimTable or Pandas data frame
        """
        from h2o.two_dim_table import H2OTwoDimTable
        from h2o.frame import H2OFrame
        from h2o.expr import ExprNode
        from h2o.exceptions import H2OValueError
        from h2o.utils.shared_utils import can_use_pandas

        if type(frame) is not H2OFrame:
            raise H2OValueError("Frame is not H2OFrame")

        if type(predicted) is not H2OFrame:
            raise H2OValueError("Frame is not H2OFrame")

        assert_is_type(variable, str)

        m_frame = H2OFrame._expr(ExprNode(
            "predicted.vs.actual.by.var",
            self,
            frame,
            variable,
            predicted
        ))
        if use_pandas and can_use_pandas():
            import pandas
            pd = h2o.as_list(m_frame)
            return pandas.DataFrame(pd, columns=pd.columns).set_index(variable)
        else:
            def _replace_empty_str(row):
                return [
                    float("nan") if "" == elem else elem
                    for elem in row
                ]

            varimp = H2OTwoDimTable(
                table_header="Predicted vs Actual by Variable '%s'" % variable,
                col_header=m_frame.columns,
                col_types=["string"] + ["double"] * (len(m_frame.columns) - 1),
                raw_cell_values=list(
                    map(_replace_empty_str, zip(*m_frame.as_data_frame(use_pandas=False, header=False))))  # transpose
            )
            return varimp

    # --------------------------------
    # ModelBase representation methods
    # --------------------------------
    
    def _str_items(self, verbosity=None):
        verbosity = verbosity or 'full'  # default verbosity when printing model
        # edge cases
        if self._future:
            self._job.poll_once()
            return
        if self._model_json is None:
            return "No model available"
        if self.key is None:
            return "This model (key={}) has been removed".format(self.key)

        items = []
        if verbosity in ['full']:
            items.extend([
                "Model Details",
                "============="
            ])
        items.extend([
            "%s : %s" % (self.__class__.__name__, self._model_json["algo_full_name"]),
            "Model Key: %s" % self.key,
        ])
        if verbosity in ['medium', 'full']:
            summary = self.get_summary()
            if summary is not None:
                items.extend(["", summary])
            
        if verbosity in ['full']:
            model = self._model_json["output"]
            tm = model["training_metrics"]
            if tm is not None: items.append(tm)
            vm = model["validation_metrics"]
            if vm is not None: items.append(vm)
            xm = model["cross_validation_metrics"]
            if xm is not None: items.append(xm)
            xms = model["cross_validation_metrics_summary"]
            if xms is not None: items.append(xms)

            if "scoring_history" in model and model["scoring_history"]:
                items.append(model["scoring_history"])
            if "variable_importances" in model and model["variable_importances"]:
                items.append(model["variable_importances"])
            
        return items
    
    def _str_usage(self, verbosity=None, fmt=None):
        verbosity = verbosity or 'full'  # default verbosity when printing model
        if not self._model_json or verbosity == 'short':
            return ""
        lines = []
        if verbosity != 'full':
            lines.append("Use `model.show()` for more details.")
        lines.append("Use `model.explain()` to inspect the model.")
        lines.extend(self._str_usage_custom())
        return format_user_tips(format_to_multiline(lines), fmt=fmt) if lines else ""
    
    def _str_usage_custom(self):
        """
        Specific models can override this function to add model-specific user tips
        :return: a list of strings, each describing a user tip for this model.
        """
        return []

    def _str_(self, verbosity=None):
        items = self._str_items(verbosity)
        if isinstance(items, list):
            return format_to_multiline(items)
        return items

    def _str_pretty_(self, verbosity=None):
        return self._str_(verbosity)+self._str_usage(verbosity, 'pretty')
    
    def _str_html_(self, verbosity=None):
        items = self._str_items(verbosity)
        html = format_to_html(items) if isinstance(items, list) else items
        usage = self._str_usage(verbosity, 'html')
        return html+usage

    def _summary(self):
        model = self._model_json["output"]
        if "model_summary" in model and model["model_summary"] is not None:
            return model["model_summary"]

    def summary(self):
        """Deprecated. Please use ``get_summary`` instead"""
        return self.get_summary()
    
    def get_summary(self):
        """Return a detailed summary of the model."""
        return self._summary() or "No summary for this model"
    
    def show_summary(self):
        """Print a detailed summary of the model."""
        summary = self.get_summary()
        if summary is not None:
            display(summary)
            
    def show(self, verbosity=None, fmt=None):
        verbosity = verbosity or 'full'  # default verbosity for showing model
        return display(self, fmt=fmt, verbosity=verbosity)

    # FIXME: find a way to get rid of this awful habit that consists in doing [if data is present return data else print("no data")]
    
