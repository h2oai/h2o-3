# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals

import os
import traceback
import warnings

import h2o
from h2o.exceptions import H2OValueError
from h2o.job import H2OJob
from h2o.utils.backward_compatibility import backwards_compatible
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.compatibility import viewitems
from h2o.utils.shared_utils import can_use_pandas
from h2o.utils.typechecks import I, assert_is_type, assert_satisfies, Enum
from six import string_types


class ModelBase(backwards_compatible()):
    """Base class for all models."""

    def __init__(self):
        """Construct a new model instance."""
        super(ModelBase, self).__init__()
        self._id = None
        self._model_json = None
        self._metrics_class = None
        self._is_xvalidated = False
        self._xval_keys = None
        self._parms = {}  # internal, for object recycle
        self.parms = {}  # external
        self._estimator_type = "unsupervised"
        self._future = False  # used by __repr__/show to query job state
        self._job = None  # used when _future is True
        self._have_pojo = False
        self._have_mojo = False


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
                         "actual": self.parms[p]["actual_value"]}
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
                params[p] = self.parms[p]["actual_value"].get(params_to_select[p], None)
            else:
                params[p] = self.parms[p]["actual_value"]
        return params


    @property
    def full_parameters(self):
        """Dictionary of the full specification of all parameters."""
        return self.parms


    @property
    def type(self):
        """The type of model built: ``"classifier"`` or ``"regressor"`` or ``"unsupervised"``"""
        return self._estimator_type

    @property
    def have_pojo(self):
        """True, if export to POJO is possible"""
        return self._have_pojo

    @property
    def have_mojo(self):
        """True, if export to MOJO is possible"""
        return self._have_mojo


    def __repr__(self):
        # PUBDEV-2278: using <method>? from IPython caused everything to dump
        stk = traceback.extract_stack()
        if not ("IPython" in stk[-2][0] and "info" == stk[-2][2]):
            self.show()
        return ""


    def predict_leaf_node_assignment(self, test_data, type="Path"):
        """
        Predict on a dataset and return the leaf node assignment (only for tree-based models).

        :param H2OFrame test_data: Data on which to make predictions.
        :param Enum type: How to identify the leaf node. Nodes can be either identified by a path from to the root node
        of the tree to the node or by H2O's internal node id. One of: ``"Path"``, ``"Node_ID"`` (default: ``"Path"``).

        :returns: A new H2OFrame of predictions.
        """
        if not isinstance(test_data, h2o.H2OFrame): raise ValueError("test_data must be an instance of H2OFrame")
        assert_is_type(type, None, Enum("Path", "Node_ID"))
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"leaf_node_assignment": True, "leaf_node_assignment_type": type})
        return h2o.get_frame(j["predictions_frame"]["name"])


    def staged_predict_proba(self, test_data):
        """
        Predict class probabilities at each stage of an H2O Model (only GBM models).

        The output structure is analogous to the output of function predict_leaf_node_assignment. For each tree t and
        class c there will be a column Tt.Cc (eg. T3.C1 for tree 3 and class 1). The value will be the corresponding
        predicted probability of this class by combining the raw contributions of trees T1.Cc,..,TtCc. Binomial models
        build the trees just for the first class and values in columns Tx.C1 thus correspond to the the probability p0.

        :param H2OFrame test_data: Data on which to make predictions.

        :returns: A new H2OFrame of staged predictions.
        """
        if not isinstance(test_data, h2o.H2OFrame): raise ValueError("test_data must be an instance of H2OFrame")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"predict_staged_proba": True})
        return h2o.get_frame(j["predictions_frame"]["name"])


    def predict(self, test_data, custom_metric = None, custom_metric_func = None):
        """
        Predict on a dataset.

        :param H2OFrame test_data: Data on which to make predictions.
        :param custom_metric:  custom evaluation function defined as class reference, the class get uploaded
        into cluster
        :param custom_metric_func: custom evaluation function reference, e.g, result of upload_custom_metric

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


    def deepfeatures(self, test_data, layer):
        """
        Return hidden layer details.

        :param test_data: Data to create a feature space on
        :param layer: 0 index hidden layer
        """
        if test_data is None: raise ValueError("Must specify test data")
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

        :param: matrix_id: an integer, ranging from 0 to number of layers, that specifies the weight matrix to return.

        :returns: an H2OFrame which represents the weight matrix identified by matrix_id
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

        :param: vector_id: an integer, ranging from 0 to number of layers, that specifies the bias vector to return.

        :returns: an H2OFrame which represents the bias vector identified by vector_id
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


    def model_performance(self, test_data=None, train=False, valid=False, xval=False):
        """
        Generate model metrics for this model on test_data.

        :param H2OFrame test_data: Data set for which model metrics shall be computed against. All three of train,
            valid and xval arguments are ignored if test_data is not None.
        :param bool train: Report the training metrics for the model.
        :param bool valid: Report the validation metrics for the model.
        :param bool xval: Report the cross-validation metrics for the model. If train and valid are True, then it
            defaults to True.

        :returns: An object of class H2OModelMetrics.
        """
        if test_data is None:
            if not train and not valid and not xval: train = True  # default to train
            if train: return self._model_json["output"]["training_metrics"]
            if valid: return self._model_json["output"]["validation_metrics"]
            if xval: return self._model_json["output"]["cross_validation_metrics"]

        else:  # cases dealing with test_data not None
            if not isinstance(test_data, h2o.H2OFrame):
                raise ValueError("`test_data` must be of type H2OFrame.  Got: " + type(test_data))
            if (self._model_json["response_column_name"] != None) and not(self._model_json["response_column_name"] in test_data.names):
                print("WARNING: Model metrics cannot be calculated and metric_json is empty due to the absence of the response column in your dataset.")
                return
            res = h2o.api("POST /3/ModelMetrics/models/%s/frames/%s" % (self.model_id, test_data.frame_id))

            # FIXME need to do the client-side filtering...  (PUBDEV-874)
            raw_metrics = None
            for mm in res["model_metrics"]:
                if mm["frame"] is not None and mm["frame"]["name"] == test_data.frame_id:
                    raw_metrics = mm
                    break
            return self._metrics_class(raw_metrics, algo=self._model_json["algo"])


    def scoring_history(self):
        """
        Retrieve Model Score History.

        :returns: The score history as an H2OTwoDimTable or a Pandas DataFrame.
        """
        model = self._model_json["output"]
        if "scoring_history" in model and model["scoring_history"] is not None:
            return model["scoring_history"].as_data_frame()
        print("No score history for this model")


    def cross_validation_metrics_summary(self):
        """
        Retrieve Cross-Validation Metrics Summary.

        :returns: The cross-validation metrics summary as an H2OTwoDimTable
        """
        model = self._model_json["output"]
        if "cross_validation_metrics_summary" in model and model["cross_validation_metrics_summary"] is not None:
            return model["cross_validation_metrics_summary"]
        print("No cross-validation metrics summary for this model")


    def summary(self):
        """Print a detailed summary of the model."""
        model = self._model_json["output"]
        if "model_summary" in model and model["model_summary"] is not None:
            return model["model_summary"]
        print("No model summary for this model")


    def show(self):
        """Print innards of model, without regards to type."""
        if self._future:
            self._job.poll_once()
            return
        if self._model_json is None:
            print("No model trained yet")
            return
        if self.model_id is None:
            print("This H2OEstimator has been removed.")
            return
        model = self._model_json["output"]
        print("Model Details")
        print("=============")

        print(self.__class__.__name__, ": ", self._model_json["algo_full_name"])
        print("Model Key: ", self._id)

        self.summary()

        print()
        # training metrics
        tm = model["training_metrics"]
        if tm: tm.show()
        vm = model["validation_metrics"]
        if vm: vm.show()
        xm = model["cross_validation_metrics"]
        if xm: xm.show()
        xms = model["cross_validation_metrics_summary"]
        if xms: xms.show()

        if "scoring_history" in model and model["scoring_history"]:
            model["scoring_history"].show()
        if "variable_importances" in model and model["variable_importances"]:
            model["variable_importances"].show()


    def varimp(self, use_pandas=False):
        """
        Pretty print the variable importances, or return them in a list.

        :param use_pandas: If True, then the variable importances will be returned as a pandas data frame.

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

        :param bool train: Get the residual deviance for the training set. If both train and valid are False, then
            train is selected by default.
        :param bool valid: Get the residual deviance for the validation set. If both train and valid are True, then
            train is selected by default.

        :returns: Return the residual deviance, or None if it is not present.
        """
        if xval: raise H2OValueError("Cross-validation metrics are not available.")
        if not train and not valid: train = True
        if train and valid:  train = True
        if train:
            return self._model_json["output"]["training_metrics"].residual_deviance()
        else:
            return self._model_json["output"]["validation_metrics"].residual_deviance()


    def residual_degrees_of_freedom(self, train=False, valid=False, xval=False):
        """
        Retreive the residual degress of freedom if this model has the attribute, or None otherwise.

        :param bool train: Get the residual dof for the training set. If both train and valid are False, then train
            is selected by default.
        :param bool valid: Get the residual dof for the validation set. If both train and valid are True, then train
            is selected by default.

        :returns: Return the residual dof, or None if it is not present.
        """
        if xval: raise H2OValueError("Cross-validation metrics are not available.")
        if not train and not valid: train = True
        if train and valid:         train = True
        if train:
            return self._model_json["output"]["training_metrics"].residual_degrees_of_freedom()
        else:
            return self._model_json["output"]["validation_metrics"].residual_degrees_of_freedom()


    def null_deviance(self, train=False, valid=False, xval=False):
        """
        Retreive the null deviance if this model has the attribute, or None otherwise.

        :param bool train: Get the null deviance for the training set. If both train and valid are False, then train
            is selected by default.
        :param bool valid: Get the null deviance for the validation set. If both train and valid are True, then train
            is selected by default.

        :returns: Return the null deviance, or None if it is not present.
        """
        if xval: raise H2OValueError("Cross-validation metrics are not available.")
        if not train and not valid: train = True
        if train and valid:         train = True
        if train:
            return self._model_json["output"]["training_metrics"].null_deviance()
        else:
            return self._model_json["output"]["validation_metrics"].null_deviance()


    def null_degrees_of_freedom(self, train=False, valid=False, xval=False):
        """
        Retreive the null degress of freedom if this model has the attribute, or None otherwise.

        :param bool train: Get the null dof for the training set. If both train and valid are False, then train is
            selected by default.
        :param bool valid: Get the null dof for the validation set. If both train and valid are True, then train is
            selected by default.

        :returns: Return the null dof, or None if it is not present.
        """
        if xval: raise H2OValueError("Cross-validation metrics are not available.")
        if not train and not valid: train = True
        if train and valid:         train = True
        if train:
            return self._model_json["output"]["training_metrics"].null_degrees_of_freedom()
        else:
            return self._model_json["output"]["validation_metrics"].null_degrees_of_freedom()


    def pprint_coef(self):
        """Pretty print the coefficents table (includes normalized coefficients)."""
        print(self._model_json["output"]["coefficients_table"])  # will return None if no coefs!


    def coef(self):
        """
        Return the coefficients which can be applied to the non-standardized data.

        Note: standardize = True by default, if set to False then coef() return the coefficients which are fit directly.
        """
        tbl = self._model_json["output"]["coefficients_table"]
        if tbl is None:
            return None
        return {name: coef for name, coef in zip(tbl["names"], tbl["coefficients"])}


    def coef_norm(self):
        """
        Return coefficients fitted on the standardized data (requires standardize = True, which is on by default).

        These coefficients can be used to evaluate variable importance.
        """
        tbl = self._model_json["output"]["coefficients_table"]
        if tbl is None:
            return None
        return {name: coef for name, coef in zip(tbl["names"], tbl["standardized_coefficients"])}


    def r2(self, train=False, valid=False, xval=False):
        """
        Return the R squared for this regression model.

        Will return R^2 for GLM Models and will return NaN otherwise.

        The R^2 value is defined to be 1 - MSE/var, where var is computed as sigma*sigma.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is True, then return the R^2 value for the training data.
        :param bool valid: If valid is True, then return the R^2 value for the validation data.
        :param bool xval:  If xval is True, then return the R^2 value for the cross validation data.

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

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is True, then return the MSE value for the training data.
        :param bool valid: If valid is True, then return the MSE value for the validation data.
        :param bool xval:  If xval is True, then return the MSE value for the cross validation data.

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

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is True, then return the RMSE value for the training data.
        :param bool valid: If valid is True, then return the RMSE value for the validation data.
        :param bool xval:  If xval is True, then return the RMSE value for the cross validation data.

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

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is True, then return the MAE value for the training data.
        :param bool valid: If valid is True, then return the MAE value for the validation data.
        :param bool xval:  If xval is True, then return the MAE value for the cross validation data.

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

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is True, then return the RMSLE value for the training data.
        :param bool valid: If valid is True, then return the RMSLE value for the validation data.
        :param bool xval:  If xval is True, then return the RMSLE value for the cross validation data.

        :returns: The RMSLE for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.rmsle()
        return list(m.values())[0] if len(m) == 1 else m


    def logloss(self, train=False, valid=False, xval=False):
        """
        Get the Log Loss.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is True, then return the log loss value for the training data.
        :param bool valid: If valid is True, then return the log loss value for the validation data.
        :param bool xval:  If xval is True, then return the log loss value for the cross validation data.

        :returns: The log loss for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.logloss()
        return list(m.values())[0] if len(m) == 1 else m


    def mean_residual_deviance(self, train=False, valid=False, xval=False):
        """
        Get the Mean Residual Deviances.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is True, then return the Mean Residual Deviance value for the training data.
        :param bool valid: If valid is True, then return the Mean Residual Deviance value for the validation data.
        :param bool xval:  If xval is True, then return the Mean Residual Deviance value for the cross validation data.

        :returns: The Mean Residual Deviance for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.mean_residual_deviance()
        return list(m.values())[0] if len(m) == 1 else m


    def auc(self, train=False, valid=False, xval=False):
        """
        Get the AUC (Area Under Curve).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is True, then return the AUC value for the training data.
        :param bool valid: If valid is True, then return the AUC value for the validation data.
        :param bool xval:  If xval is True, then return the AUC value for the validation data.

        :returns: The AUC.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.auc()
        return list(m.values())[0] if len(m) == 1 else m


    def aic(self, train=False, valid=False, xval=False):
        """
        Get the AIC (Akaike Information Criterium).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param bool train: If train is True, then return the AIC value for the training data.
        :param bool valid: If valid is True, then return the AIC value for the validation data.
        :param bool xval:  If xval is True, then return the AIC value for the validation data.

        :returns: The AIC.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.aic()
        return list(m.values())[0] if len(m) == 1 else m


    def gini(self, train=False, valid=False, xval=False):
        """
        Get the Gini coefficient.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval"

        :param bool train: If train is True, then return the Gini Coefficient value for the training data.
        :param bool valid: If valid is True, then return the Gini Coefficient value for the validation data.
        :param bool xval:  If xval is True, then return the Gini Coefficient value for the cross validation data.

        :returns: The Gini Coefficient for this binomial model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.gini()
        return list(m.values())[0] if len(m) == 1 else m

    def download_pojo(self, path="", get_genmodel_jar=False, genmodel_name=""):
        """
        Download the POJO for this model to the directory specified by path.

        If path is an empty string, then dump the output to screen.

        :param path:  An absolute path to the directory where POJO should be saved.
        :param get_genmodel_jar: if True, then also download h2o-genmodel.jar and store it in folder ``path``.
        :param genmodel_name Custom name of genmodel jar
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
        :param genmodel_name Custom name of genmodel jar
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

    def save_mojo(self, path="", force=False):
        """
        Save an H2O Model as MOJO (Model Object, Optimized) to disk.

        :param model: The model object to save.
        :param path: a path to save the model at (hdfs, s3, local)
        :param force: if True overwrite destination directory in case it exists, or throw exception if set to False.

        :returns str: the path of the saved model
        """
        assert_is_type(path, str)
        assert_is_type(force, bool)
        if not self.have_mojo:
            raise H2OValueError("Export to MOJO not supported")
        path = os.path.join(os.getcwd() if path == "" else path, self.model_id + ".zip")
        return h2o.api("GET /99/Models.mojo/%s" % self.model_id, data={"dir": path, "force": force})["dir"]

    def save_model_details(self, path="", force=False):
        """
        Save Model Details of an H2O Model in JSON Format to disk.

        :param model: The model object to save.
        :param path: a path to save the model details at (hdfs, s3, local)
        :param force: if True overwrite destination directory in case it exists, or throw exception if set to False.

        :returns str: the path of the saved model details
        """
        assert_is_type(path, str)
        assert_is_type(force, bool)
        path = os.path.join(os.getcwd() if path == "" else path, self.model_id + ".json")
        return h2o.api("GET /99/Models/%s/json" % self.model_id, data={"dir": path, "force": force})["dir"]

    @staticmethod
    def _get_metrics(o, train, valid, xval):
        # noinspection PyProtectedMember
        output = o._model_json["output"]
        metrics = {}
        if train: metrics["train"] = output["training_metrics"]
        if valid: metrics["valid"] = output["validation_metrics"]
        if xval: metrics["xval"] = output["cross_validation_metrics"]
        if len(metrics) == 0: metrics["train"] = output["training_metrics"]
        return metrics


    # Delete from cluster as model goes out of scope
    # def __del__(self):
    #   h2o.remove(self._id)

    def _plot(self, timestep, metric, server=False):
        plt = _get_matplotlib_pyplot(server)
        if not plt: return

        scoring_history = self.scoring_history()
        # Separate functionality for GLM since its output is different from other algos
        if self._model_json["algo"] == "glm":
            # GLM has only one timestep option, which is `iteration`
            timestep = "iteration"
            if metric == "AUTO":
                metric = "log_likelihood"
            elif metric not in ("log_likelihood", "objective"):
                raise H2OValueError("for GLM, metric must be one of: log_likelihood, objective")
            plt.xlabel(timestep)
            plt.ylabel(metric)
            plt.title("Validation Scoring History")
            plt.plot(scoring_history[timestep], scoring_history[metric])

        elif self._model_json["algo"] in ("deeplearning", "deepwater", "xgboost", "drf", "gbm"):
            # Set timestep
            if self._model_json["algo"] in ("gbm", "drf", "xgboost"):
                assert_is_type(timestep, "AUTO", "duration", "number_of_trees")
                if timestep == "AUTO":
                    timestep = "number_of_trees"
            else:  # self._model_json["algo"] == "deeplearning":
                # Delete first row of DL scoring history since it contains NAs & NaNs
                if scoring_history["samples"][0] == 0:
                    scoring_history = scoring_history[1:]
                assert_is_type(timestep, "AUTO", "epochs",  "samples", "duration")
                if timestep == "AUTO":
                    timestep = "epochs"

            training_metric = "training_{}".format(metric)
            validation_metric = "validation_{}".format(metric)
            if timestep == "duration":
                dur_colname = "duration_{}".format(scoring_history["duration"][1].split()[1])
                scoring_history[dur_colname] = [str(x).split()[0] for x in scoring_history["duration"]]
                timestep = dur_colname

            if can_use_pandas():
                valid = validation_metric in list(scoring_history)
                ylim = (scoring_history[[training_metric, validation_metric]].min().min(),
                        scoring_history[[training_metric, validation_metric]].max().max()) if valid \
                    else (scoring_history[training_metric].min(), scoring_history[training_metric].max())
            else:
                valid = validation_metric in scoring_history.col_header
                ylim = (min(min(scoring_history[[training_metric, validation_metric]])),
                        max(max(scoring_history[[training_metric, validation_metric]]))) if valid \
                    else (min(scoring_history[training_metric]), max(scoring_history[training_metric]))
            if ylim[0] == ylim[1]: ylim = (0, 1)

            if valid:  # Training and validation scoring history
                plt.xlabel(timestep)
                plt.ylabel(metric)
                plt.title("Scoring History")
                plt.ylim(ylim)
                plt.plot(scoring_history[timestep], scoring_history[training_metric], label="Training")
                plt.plot(scoring_history[timestep], scoring_history[validation_metric], color="orange",
                         label="Validation")
                plt.legend()
            else:  # Training scoring history only
                plt.xlabel(timestep)
                plt.ylabel(training_metric)
                plt.title("Training Scoring History")
                plt.ylim(ylim)
                plt.plot(scoring_history[timestep], scoring_history[training_metric])

        else:  # algo is not glm, deeplearning, drf, gbm, xgboost
            raise H2OValueError("Plotting not implemented for this type of model")
        if not server: plt.show()


    def partial_plot(self, data, cols, destination_key=None, nbins=20, weight_column=None,
                     plot=True, plot_stddev = True, figsize=(7, 10), server=False, include_na=False, user_splits=None):
        """
        Create partial dependence plot which gives a graphical depiction of the marginal effect of a variable on the
        response. The effect of a variable is measured in change in the mean response.

        :param H2OFrame data: An H2OFrame object used for scoring and constructing the plot.
        :param cols: Feature(s) for which partial dependence will be calculated.
        :param destination_key: An key reference to the created partial dependence tables in H2O.
        :param nbins: Number of bins used. For categorical columns make sure the number of bins exceed the level count. If you enable add_missing_NA, the returned length will be nbin+1.
        :param weight_column: A string denoting which column of data should be used as the weight column.
        :param plot: A boolean specifying whether to plot partial dependence table.
        :param plot_stddev: A boolean specifying whether to add std err to partial dependence plot.
        :param figsize: Dimension/size of the returning plots, adjust to fit your output cells.
        :param server: ?
        :param include_na: A boolean specifying whether missing value should be included in the Feature values.
        :param user_splits: a dictionary containing column names as key and user defined split values as value in a list.
        :returns: Plot and list of calculated mean response tables for each feature requested.
        """

        if not isinstance(data, h2o.H2OFrame): raise ValueError("data must be an instance of H2OFrame")
        assert_is_type(cols, [str])
        assert_is_type(destination_key, None, str)
        assert_is_type(nbins, int)
        assert_is_type(plot, bool)
        assert_is_type(figsize, (int, int))

        # Check cols specified exist in frame data
        for xi in cols:
            if xi not in data.names:
                raise H2OValueError("Column %s does not exist in the training frame" % xi)
        if isinstance(weight_column, int) and not (weight_column == -1):
            raise H2OValueError("Weight column should be a column name in your data frame.")
        elif isinstance(weight_column, str): # index is a name
            if weight_column not in data.names:
                raise H2OValueError("Column %s does not exist in the data frame" % weight_column)
            weight_column = data.names.index(weight_column)

        kwargs = {}
        kwargs["cols"] = cols
        kwargs["model_id"] = self.model_id
        kwargs["frame_id"] = data.frame_id
        kwargs["nbins"] = nbins
        kwargs["destination_key"] = destination_key
        kwargs["weight_column_index"] = weight_column
        kwargs["add_missing_na"] = include_na

        # extract user defined split points from dict user_splits into an integer array of column indices
        # and a double array of user define values for the corresponding columns
        if not(user_splits == None):
            if not(isinstance(user_splits, dict)):
                raise H2OValueError("user_splits must be a Python dict.")
            else:
                if len(user_splits)>0: # do nothing with an empty dict
                    user_cols = []
                    user_values = []
                    user_num_splits = []
                    data_ncol = data.ncol
                    column_names = data.names
                    for colKey,val in user_splits.items():
                        if isinstance(colKey, string_types) and colKey in column_names:
                            user_cols.append(colKey)
                        elif isinstance(colKey, int) and colKey < data_ncol:
                            user_cols.append(column_names[colKey])
                        else:
                            raise H2OValueError("column names/indices used in user_splits are not valid.  They "
                                                "should be chosen from the columns of your data set.")

                        if data[colKey].isfactor()[0] or data[colKey].isnumeric()[0]: # replace enum string with actual value
                            nVal = len(val)
                            if data[colKey].isfactor()[0]:
                                domains = data[colKey].levels()[0]

                                numVal = [0]*nVal
                                for ind in range(nVal):
                                    if (val[ind] in domains):
                                        numVal[ind] = domains.index(val[ind])
                                    else:
                                        raise H2OValueError("Illegal enum value {0} encountered.  To include missing"
                                                        " values in your feature values, set include_na to "
                                                        "True".format(val[ind]))

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

        json = H2OJob(h2o.api("POST /3/PartialDependence/", data=kwargs),  job_type="PartialDependencePlot").poll()
        json = h2o.api("GET /3/PartialDependence/%s" % json.dest_key)

        # Extract partial dependence data from json response
        pps = json["partial_dependence_data"]

        # Plot partial dependence plots using matplotlib
        if plot:
            plt = _get_matplotlib_pyplot(server)
            if not plt: return

            fig, axs = plt.subplots(len(cols), squeeze=False, figsize=figsize)
            for i, pp in enumerate(pps):
                # Check weather column was categorical or numeric
                col = cols[i]
                cat = data[col].isfactor()[0]
                upper = [a + b for a, b in zip(pp[1], pp[2]) ]
                lower = [a - b for a, b in zip(pp[1], pp[2]) ]
                if cat:
                    labels = pp[0]
                    x = range(len(labels))
                    y = pp[1]
                    axs[i, 0].plot(x, y, "ro")
                    if plot_stddev:
                        axs[i, 0].plot(x, lower, 'b--')
                        axs[i, 0].plot(x, upper, 'b--')
                    axs[i, 0].set_ylim(min(lower) - 0.1*abs(min(lower)), max(upper) + 0.1*abs(max(upper)))
                    axs[i, 0].set_xticks(x)
                    axs[i, 0].set_xticklabels(labels)
                    axs[i, 0].margins(0.2)
                else:
                    x = pp[0]
                    y = pp[1]
                    axs[i, 0].plot(x, y, "r-")
                    if plot_stddev:
                        axs[i, 0].plot(x, lower, 'b--')
                        axs[i, 0].plot(x, upper, 'b--')
                    axs[i, 0].set_xlim(min(x), max(x))
                    axs[i, 0].set_ylim(min(lower) - 0.1*abs(min(lower)), max(upper) + 0.1*abs(max(upper)))

                axs[i, 0].set_title("Partial Dependence Plot For {}".format(col))
                axs[i, 0].set_xlabel(pp.col_header[0])
                axs[i, 0].set_ylabel(pp.col_header[1])
                axs[i, 0].xaxis.grid()
                axs[i, 0].yaxis.grid()
            if len(col) > 1:
                fig.tight_layout(pad=0.4, w_pad=0.5, h_pad=1.0)

        return pps


    def varimp_plot(self, num_of_features=None, server=False):
        """
        Plot the variable importance for a trained model.

        :param num_of_features: the number of features shown in the plot (default is 10 or all if less than 10).
        :param server: ?

        :returns: None.
        """
        assert_is_type(num_of_features, None, int)
        assert_is_type(server, bool)

        plt = _get_matplotlib_pyplot(server)
        if not plt: return

        # check if the model is a glm
        if self._model_json["algo"] == "glm":
            # print statement to used std_coef_plot(), and use std_coef_plt instead
            print("Variable importance does not apply to GLM. Will use std_coef_plot() instead.")
            self.std_coef_plot(num_of_features)
            return

        # get the variable importances as a list of tuples, do not use pandas dataframe
        importances = self.varimp(use_pandas=False)
        # features labels correspond to the first value of each tuple in the importances list
        feature_labels = [tup[0] for tup in importances]
        # relative importances correspond to the first value of each tuple in the importances list
        scaled_importances = [tup[2] for tup in importances]
        # specify bar centers on the y axis, but flip the order so largest bar appears at top
        pos = range(len(feature_labels))[::-1]
        # specify the bar lengths
        val = scaled_importances

        # # check that num_of_features is an integer
        # if num_of_features is None:
        #     num_of_features = len(val)

        # default to 10 or less features if num_of_features is not specified
        if num_of_features is None:
            num_of_features = min(len(val), 10)

        fig, ax = plt.subplots(1, 1, figsize=(14, 10))
        # create separate plot for the case where num_of_features == 1
        if num_of_features == 1:
            plt.barh(pos[0:num_of_features], val[0:num_of_features], align="center",
                     height=0.8, color="#1F77B4", edgecolor="none")
            # Hide the right and top spines, color others grey
            ax.spines["right"].set_visible(False)
            ax.spines["top"].set_visible(False)
            ax.spines["bottom"].set_color("#7B7B7B")
            ax.spines["left"].set_color("#7B7B7B")
            # Only show ticks on the left and bottom spines
            ax.yaxis.set_ticks_position("left")
            ax.xaxis.set_ticks_position("bottom")
            plt.yticks(pos[0:num_of_features], feature_labels[0:num_of_features])
            ax.margins(None, 0.5)

        else:
            plt.barh(pos[0:num_of_features], val[0:num_of_features], align="center",
                     height=0.8, color="#1F77B4", edgecolor="none")
            # Hide the right and top spines, color others grey
            ax.spines["right"].set_visible(False)
            ax.spines["top"].set_visible(False)
            ax.spines["bottom"].set_color("#7B7B7B")
            ax.spines["left"].set_color("#7B7B7B")
            # Only show ticks on the left and bottom spines
            ax.yaxis.set_ticks_position("left")
            ax.xaxis.set_ticks_position("bottom")
            plt.yticks(pos[0:num_of_features], feature_labels[0:num_of_features])
            plt.ylim([min(pos[0:num_of_features])- 1, max(pos[0:num_of_features])+1])
            # ax.margins(y=0.5)

        # check which algorithm was used to select right plot title
        if self._model_json["algo"] == "gbm":
            plt.title("Variable Importance: H2O GBM", fontsize=20)
            if not server: plt.show()
        elif self._model_json["algo"] == "drf":
            plt.title("Variable Importance: H2O DRF", fontsize=20)
            if not server: plt.show()
        elif self._model_json["algo"] == "xgboost":
            plt.title("Variable Importance: H2O XGBoost", fontsize=20)
            if not server: plt.show()
        # if H2ODeepLearningEstimator has variable_importances == True
        elif self._model_json["algo"] == "deeplearning":
            plt.title("Variable Importance: H2O Deep Learning", fontsize=20)
            if not server: plt.show()
        else:
            raise H2OValueError("A variable importances plot is not implemented for this type of model")


    def std_coef_plot(self, num_of_features=None, server=False):
        """
        Plot a GLM model"s standardized coefficient magnitudes.

        :param num_of_features: the number of features shown in the plot.
        :param server: ?

        :returns: None.
        """
        assert_is_type(num_of_features, None, I(int, lambda x: x > 0))

        # check that model is a glm
        if self._model_json["algo"] != "glm":
            raise H2OValueError("This function is available for GLM models only")

        plt = _get_matplotlib_pyplot(server)
        if not plt: return

        # get unsorted tuple of labels and coefficients
        unsorted_norm_coef = self.coef_norm().items()
        # drop intercept value then sort tuples by the coefficient"s absolute value
        drop_intercept = [tup for tup in unsorted_norm_coef if tup[0] != "Intercept"]
        norm_coef = sorted(drop_intercept, key=lambda x: abs(x[1]), reverse=True)

        signage = []
        for element in norm_coef:
            # if positive including zero, color blue, else color orange (use same colors as Flow)
            if element[1] >= 0:
                signage.append("#1F77B4")  # blue
            else:
                signage.append("#FF7F0E")  # dark orange

        # get feature labels and their corresponding magnitudes
        feature_labels = [tup[0] for tup in norm_coef]
        norm_coef_magn = [abs(tup[1]) for tup in norm_coef]
        # specify bar centers on the y axis, but flip the order so largest bar appears at top
        pos = range(len(feature_labels))[::-1]
        # specify the bar lengths
        val = norm_coef_magn

        # check number of features, default is all the features
        if num_of_features is None:
            num_of_features = len(val)

        # plot horizontal plot
        fig, ax = plt.subplots(1, 1, figsize=(14, 10))
        # create separate plot for the case where num_of_features = 1
        if num_of_features == 1:
            plt.barh(pos[0], val[0],
                     align="center", height=0.8, color=signage[0], edgecolor="none")
            # Hide the right and top spines, color others grey
            ax.spines["right"].set_visible(False)
            ax.spines["top"].set_visible(False)
            ax.spines["bottom"].set_color("#7B7B7B")
            ax.spines["left"].set_color("#7B7B7B")
            # Only show ticks on the left and bottom spines
            ax.yaxis.set_ticks_position("left")
            ax.xaxis.set_ticks_position("bottom")
            plt.yticks([0], feature_labels[0])
            ax.margins(None, 0.5)

        else:
            plt.barh(pos[0:num_of_features], val[0:num_of_features],
                     align="center", height=0.8, color=signage[0:num_of_features], edgecolor="none")
            # Hide the right and top spines, color others grey
            ax.spines["right"].set_visible(False)
            ax.spines["top"].set_visible(False)
            ax.spines["bottom"].set_color("#7B7B7B")
            ax.spines["left"].set_color("#7B7B7B")
            # Only show ticks on the left and bottom spines
            ax.yaxis.set_ticks_position("left")
            ax.xaxis.set_ticks_position("bottom")
            plt.yticks(pos[0:num_of_features], feature_labels[0:num_of_features])
            ax.margins(None, 0.05)

        # generate custom fake lines that will be used as legend entries:
        # check if positive and negative values exist
        # if positive create positive legend
        if "#1F77B4" in signage[0:num_of_features] and "#FF7F0E" not in signage[0:num_of_features]:
            color_ids = ("Positive",)
            markers = [plt.Line2D([0, 0], [0, 0], color=color, marker="s", linestyle="")
                       for color in signage[0:num_of_features]]
            lgnd = plt.legend(markers, color_ids, numpoints=1, loc="best", frameon=False, fontsize=13)
            lgnd.legendHandles[0]._legmarker.set_markersize(10)
        # if neg create neg legend
        elif "#FF7F0E" in signage[0:num_of_features] and "#1F77B4" not in signage[0:num_of_features]:
            color_ids = ("Negative",)
            markers = [plt.Line2D([0, 0], [0, 0], color=color, marker="s", linestyle="")
                       for color in set(signage[0:num_of_features])]
            lgnd = plt.legend(markers, color_ids, numpoints=1, loc="best", frameon=False, fontsize=13)
            lgnd.legendHandles[0]._legmarker.set_markersize(10)
        # if both provide both colors in legend
        else:
            color_ids = ("Positive", "Negative")
            markers = [plt.Line2D([0, 0], [0, 0], color=color, marker="s", linestyle="")
                       for color in set(signage[0:num_of_features])]
            lgnd = plt.legend(markers, color_ids, numpoints=1, loc="best", frameon=False, fontsize=13)
            lgnd.legendHandles[0]._legmarker.set_markersize(10)
            lgnd.legendHandles[1]._legmarker.set_markersize(10)

        # Hide the right and top spines, color others grey
        ax.spines["right"].set_visible(False)
        ax.spines["top"].set_visible(False)
        ax.spines["bottom"].set_color("#7B7B7B")
        ax.spines["left"].set_color("#7B7B7B")

        # Only show ticks on the left and bottom spines
        # ax.yaxis.set_ticks_position("left")
        # ax.xaxis.set_ticks_position("bottom")
        plt.yticks(pos[0:num_of_features], feature_labels[0:num_of_features])
        plt.tick_params(axis="x", which="minor", bottom="off", top="off",  labelbottom="off")
        plt.title("Standardized Coef. Magnitudes: H2O GLM", fontsize=20)
        # plt.axis("tight")
        # show plot
        if not server: plt.show()


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
        if cvmodels is None: return None
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
        if preds is None: return None
        m = []
        for p in preds: m.append(h2o.get_frame(p["name"]))
        return m


    def cross_validation_holdout_predictions(self):
        """
        Obtain the (out-of-sample) holdout predictions of all cross-validation models on the training data.

        This is equivalent to summing up all H2OFrames returned by cross_validation_predictions.

        :returns: H2OFrame
        """
        preds = self._model_json["output"]["cross_validation_holdout_predictions_frame_id"]
        if preds is None: return None
        return h2o.get_frame(preds["name"])


    def cross_validation_fold_assignment(self):
        """
        Obtain the cross-validation fold assignment for all rows in the training data.

        :returns: H2OFrame
        """
        fid = self._model_json["output"]["cross_validation_fold_assignment_frame_id"]
        if fid is None: return None
        return h2o.get_frame(fid["name"])

    def rotation(self):
        """
        Obtain the rotations (eigenvectors) for a PCA model

        :return: H2OFrame
        """
        if self._model_json["algo"] != "pca":
            raise H2OValueError("This function is available for PCA models only")
        return self._model_json["output"]["eigenvectors"]

    def score_history(self):
        """DEPRECATED. Use :meth:`scoring_history` instead."""
        return self.scoring_history()


    # Deprecated functions; left here for backward compatibility
    _bcim = {
        "giniCoef": lambda self, *args, **kwargs: self.gini(*args, **kwargs),
    }




def _get_matplotlib_pyplot(server):
    try:
        # noinspection PyUnresolvedReferences
        import matplotlib
        if server: matplotlib.use("Agg", warn=False)
        # noinspection PyUnresolvedReferences
        import matplotlib.pyplot as plt
        return plt
    except ImportError:
        print("`matplotlib` library is required for this function!")
        return None
