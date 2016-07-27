# -*- encoding: utf-8 -*-
"""
This module implements the base model class.  All model things inherit from this class.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import imp
import traceback
import warnings

import h2o
from h2o.job import H2OJob
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.shared_utils import can_use_pandas


class ModelBase(object):

    def __init__(self):
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


    @property
    def model_id(self):
        """Model identifier."""
        return self._id

    @model_id.setter
    def model_id(self, newid):
        oldid = self._id
        self._id = newid
        h2o.rapids('(rename "%s" "%s")' % (oldid, newid))


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
    def full_parameters(self):
        """
        Get the full specification of all parameters.

        :returns: a dictionary of parameters used to build this model.
        """
        return self.parms


    @property
    def type(self):
        """Get the type of model built as a string.

        :returns: "classifier" or "regressor" or "unsupervised"
        """
        return self._estimator_type


    def __repr__(self):
        # PUBDEV-2278: using <method>? from IPython caused everything to dump
        stk = traceback.extract_stack()
        if not ("IPython" in stk[-2][0] and "info" == stk[-2][2]):
            self.show()
        return ""


    def predict_leaf_node_assignment(self, test_data):
        """
        Predict on a dataset and return the leaf node assignment (only for tree-based models).

        :param H2OFrame test_data: Data on which to make predictions.

        :returns: A new H2OFrame of predictions.
        """
        if not isinstance(test_data, h2o.H2OFrame): raise ValueError("test_data must be an instance of H2OFrame")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"leaf_node_assignment": True})
        return h2o.get_frame(j["predictions_frame"]["name"])


    def predict(self, test_data):
        """
        Predict on a dataset.

        :param H2OFrame test_data: Data on which to make predictions.

        :returns: A new H2OFrame of predictions.
        """
        if not isinstance(test_data, h2o.H2OFrame): raise ValueError("test_data must be an instance of H2OFrame")
        j = H2OJob(h2o.api("POST /4/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id)),
                   self._model_json['algo'] + " prediction")
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

        :returns: A list of models
        """
        return self.get_xval_models()


    def deepfeatures(self, test_data, layer):
        """
        Return hidden layer details.

        :param test_data: Data to create a feature space on
        :param layer: 0 index hidden layer
        """
        if test_data is None: raise ValueError("Must specify test data")
        j = H2OJob(h2o.api("POST /4/Predictions/models/%s/frames/%s" % (self._id, test_data.frame_id),
                           data={"deep_features_hidden_layer": layer}), "deepfeatures")
        j.poll()
        return h2o.get_frame(j.dest_key)


    def weights(self, matrix_id=0):
        """
        Return the frame for the respective weight matrix.

        :param: matrix_id: an integer, ranging from 0 to number of layers, that specifies the weight matrix to return.

        :returns: an H2OFrame which represents the weight matrix identified by matrix_id
        """
        num_weight_matrices = len(self._model_json['output']['weights'])
        if matrix_id not in list(range(num_weight_matrices)):
            raise ValueError(
                "Weight matrix does not exist. Model has {0} weight matrices (0-based indexing), but matrix {1} "
                "was requested.".format(num_weight_matrices, matrix_id))
        return h2o.get_frame(self._model_json['output']['weights'][matrix_id]['URL'].split('/')[3])


    def biases(self, vector_id=0):
        """
        Return the frame for the respective bias vector.

        :param: vector_id: an integer, ranging from 0 to number of layers, that specifies the bias vector to return.

        :returns: an H2OFrame which represents the bias vector identified by vector_id
        """
        num_bias_vectors = len(self._model_json['output']['biases'])
        if vector_id not in list(range(num_bias_vectors)):
            raise ValueError(
                "Bias vector does not exist. Model has {0} bias vectors (0-based indexing), but vector {1} "
                "was requested.".format(num_bias_vectors, vector_id))
        return h2o.get_frame(self._model_json['output']['biases'][vector_id]['URL'].split('/')[3])


    def normmul(self):
        """Normalization/Standardization multipliers for numeric predictors."""
        return self._model_json['output']['normmul']


    def normsub(self):
        """Normalization/Standardization offsets for numeric predictors."""
        return self._model_json['output']['normsub']


    def respmul(self):
        """Normalization/Standardization multipliers for numeric response."""
        return self._model_json['output']['normrespmul']


    def respsub(self):
        """Normalization/Standardization offsets for numeric response."""
        return self._model_json['output']['normrespsub']


    def catoffsets(self):
        """Categorical offsets for one-hot encoding."""
        return self._model_json['output']['catoffsets']


    def model_performance(self, test_data=None, train=False, valid=False, xval=False):
        """
        Generate model metrics for this model on test_data.

        Parameters
        ----------
        test_data: H2OFrame, optional
          Data set for which model metrics shall be computed against. All three of train, valid and xval arguments are
          ignored if test_data is not None.
        train: boolean, optional
          Report the training metrics for the model.
        valid: boolean, optional
          Report the validation metrics for the model.
        xval: boolean, optional
          Report the cross-validation metrics for the model. If train and valid are True, then it defaults to True.

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
        if model["model_summary"]:
            model["model_summary"].show()  # H2OTwoDimTable object


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


    def residual_deviance(self, train=False, valid=False, xval=False):
        """
        Retreive the residual deviance if this model has the attribute, or None otherwise.

        :param train: Get the residual deviance for the training set. If both train and valid are False, then
            train is selected by default.
        :param valid: Get the residual deviance for the validation set. If both train and valid are True, then
            train is selected by default.

        :returns: Return the residual deviance, or None if it is not present.
        """
        if xval: raise ValueError("Cross-validation metrics are not available.")
        if not train and not valid: train = True
        if train and valid:  train = True
        if train:
            return self._model_json["output"]["training_metrics"].residual_deviance()
        else:
            return self._model_json["output"]["validation_metrics"].residual_deviance()


    def residual_degrees_of_freedom(self, train=False, valid=False, xval=False):
        """
        Retreive the residual degress of freedom if this model has the attribute, or None otherwise.

        :param train: Get the residual dof for the training set. If both train and valid are False, then train
            is selected by default.
        :param valid: Get the residual dof for the validation set. If both train and valid are True, then train
            is selected by default.

        :returns: Return the residual dof, or None if it is not present.
        """
        if xval: raise ValueError("Cross-validation metrics are not available.")
        if not train and not valid: train = True
        if train and valid:         train = True
        if train:
            return self._model_json["output"]["training_metrics"].residual_degrees_of_freedom()
        else:
            return self._model_json["output"]["validation_metrics"].residual_degrees_of_freedom()


    def null_deviance(self, train=False, valid=False, xval=False):
        """
        Retreive the null deviance if this model has the attribute, or None otherwise.

        :param train: Get the null deviance for the training set. If both train and valid are False, then train
            is selected by default.
        :param valid: Get the null deviance for the validation set. If both train and valid are True, then train
            is selected by default.

        :returns: Return the null deviance, or None if it is not present.
        """
        if xval: raise ValueError("Cross-validation metrics are not available.")
        if not train and not valid: train = True
        if train and valid:         train = True
        if train:
            return self._model_json["output"]["training_metrics"].null_deviance()
        else:
            return self._model_json["output"]["validation_metrics"].null_deviance()


    def null_degrees_of_freedom(self, train=False, valid=False, xval=False):
        """
        Retreive the null degress of freedom if this model has the attribute, or None otherwise.

        :param train: Get the null dof for the training set. If both train and valid are False, then train is
            selected by default.
        :param valid: Get the null dof for the validation set. If both train and valid are True, then train is
            selected by default.

        :returns: Return the null dof, or None if it is not present.
        """
        if xval: raise ValueError("Cross-validation metrics are not available.")
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
        """Return the coefficients for this model."""
        tbl = self._model_json["output"]["coefficients_table"]
        if tbl is None: return None
        tbl = tbl.cell_values
        return {a[0]: a[1] for a in tbl}

    def coef_norm(self):
        """Return the normalized coefficients."""
        tbl = self._model_json["output"]["coefficients_table"]
        if tbl is None: return None
        tbl = tbl.cell_values
        return {a[0]: a[2] for a in tbl}


    def r2(self, train=False, valid=False, xval=False):
        """
        Return the R^2 for this regression model.

        Will return R^2 for GLM Models and will return NaN otherwise.

        The R^2 value is defined to be 1 - MSE/var, where var is computed as sigma*sigma.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param train: If train is True, then return the R^2 value for the training data.
        :param valid: If valid is True, then return the R^2 value for the validation data.
        :param xval:  If xval is True, then return the R^2 value for the cross validation data.

        :returns: The R^2 for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.r2()
        return list(m.values())[0] if len(m) == 1 else m


    def mse(self, train=False, valid=False, xval=False):
        """
        Get the MSE.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        Parameters
        ----------
        train : bool, default=True
          If train is True, then return the MSE value for the training data.
        valid : bool, default=True
          If valid is True, then return the MSE value for the validation data.
        xval : bool, default=True
          If xval is True, then return the MSE value for the cross validation data.

        :returns: The MSE for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.mse()
        return list(m.values())[0] if len(m) == 1 else m


    def rmse(self, train=False, valid=False, xval=False):
        """
        Get the RMSE.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        Parameters
        ----------
        train : bool, default=True
          If train is True, then return the RMSE value for the training data.
        valid : bool, default=True
          If valid is True, then return the RMSE value for the validation data.
        xval : bool, default=True
          If xval is True, then return the RMSE value for the cross validation data.

        Returns
        -------
          The RMSE for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.rmse()
        return list(m.values())[0] if len(m) == 1 else m


    def mae(self, train=False, valid=False, xval=False):
        """
        Get the MAE.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        Parameters
        ----------
        train : bool, default=True
          If train is True, then return the MAE value for the training data.
        valid : bool, default=True
          If valid is True, then return the MAE value for the validation data.
        xval : bool, default=True
          If xval is True, then return the MAE value for the cross validation data.

        Returns
        -------
          The MAE for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.mae()
        return list(m.values())[0] if len(m) == 1 else m


    def logloss(self, train=False, valid=False, xval=False):
        """
        Get the Log Loss.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param train: If train is True, then return the Log Loss value for the training data.
        :param valid: If valid is True, then return the Log Loss value for the validation data.
        :param xval:  If xval is True, then return the Log Loss value for the cross validation data.

        :returns: The Log Loss for this binomial model.
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

        :param train: If train is True, then return the Mean Residual Deviance value for the training data.
        :param valid: If valid is True, then return the Mean Residual Deviance value for the validation data.
        :param xval:  If xval is True, then return the Mean Residual Deviance value for the cross validation data.

        :returns: The Mean Residual Deviance for this regression model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.mean_residual_deviance()
        return list(m.values())[0] if len(m) == 1 else m


    def auc(self, train=False, valid=False, xval=False):
        """
        Get the AUC.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param train: If train is True, then return the AUC value for the training data.
        :param valid: If valid is True, then return the AUC value for the validation data.
        :param xval:  If xval is True, then return the AUC value for the validation data.

        :returns: The AUC.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.auc()
        return list(m.values())[0] if len(m) == 1 else m


    def aic(self, train=False, valid=False, xval=False):
        """
        Get the AIC(s).

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval"

        :param train: If train is True, then return the AIC value for the training data.
        :param valid: If valid is True, then return the AIC value for the validation data.
        :param xval:  If xval is True, then return the AIC value for the validation data.

        :returns: The AIC.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.aic()
        return list(m.values())[0] if len(m) == 1 else m


    def giniCoef(self, train=False, valid=False, xval=False):
        """
        Get the Gini coefficient.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval"

        :param train: If train is True, then return the Gini Coefficient value for the training data.
        :param valid: If valid is True, then return the Gini Coefficient value for the validation data.
        :param xval:  If xval is True, then return the Gini Coefficient value for the cross validation data.

        :returns: The Gini Coefficient for this binomial model.
        """
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm): m[k] = None if v is None else v.giniCoef()
        return list(m.values())[0] if len(m) == 1 else m


    def download_pojo(self, path=""):
        """
        Download the POJO for this model to the directory specified by path (no trailing slash!).

        If path is "", then dump to screen.

        :param model: Retrieve this model's scoring POJO.
        :param path:  An absolute path to the directory where POJO should be saved.

        :returns: None
        """
        h2o.download_pojo(self, path)  # call the "package" function


    @staticmethod
    def _get_metrics(o, train, valid, xval):
        metrics = {}
        if train: metrics["train"] = o._model_json["output"]["training_metrics"]
        if valid: metrics["valid"] = o._model_json["output"]["validation_metrics"]
        if xval: metrics["xval"] = o._model_json["output"]["cross_validation_metrics"]
        if len(metrics) == 0: metrics["train"] = o._model_json["output"]["training_metrics"]
        return metrics


    # Delete from cluster as model goes out of scope
    # def __del__(self):
    #   h2o.remove(self._id)

    def _plot(self, timestep, metric, **kwargs):

        # check for matplotlib. exit if absent
        try:
            imp.find_module('matplotlib')
            import matplotlib
            if 'server' in kwargs and kwargs['server']: matplotlib.use('Agg', warn=False)
            import matplotlib.pyplot as plt
        except ImportError:
            print("matplotlib is required for this function!")
            return

        scoring_history = self.scoring_history()
        # Separate functionality for GLM since its output is different from other algos
        if self._model_json["algo"] == "glm":
            # GLM has only one timestep option, which is `iteration`
            timestep = "iteration"
            if metric == "AUTO":
                metric = "log_likelihood"
            elif metric not in ("log_likelihood", "objective"):
                raise ValueError("for GLM, metric must be one of: log_likelihood, objective")
            plt.xlabel(timestep)
            plt.ylabel(metric)
            plt.title("Validation Scoring History")
            plt.plot(scoring_history[timestep], scoring_history[metric])

        elif self._model_json["algo"] in ("deeplearning", "drf", "gbm"):
            # Set timestep
            if self._model_json["algo"] in ("gbm", "drf"):
                if timestep == "AUTO":
                    timestep = "number_of_trees"
                elif timestep not in ("duration", "number_of_trees"):
                    raise ValueError("timestep for gbm or drf must be one of: duration, number_of_trees")
            else:  # self._model_json["algo"] == "deeplearning":
                # Delete first row of DL scoring history since it contains NAs & NaNs
                if scoring_history["samples"][0] == 0:
                    scoring_history = scoring_history[1:]
                if timestep == "AUTO":
                    timestep = "epochs"
                elif timestep not in ("epochs", "samples", "duration"):
                    raise ValueError("timestep for deeplearning must be one of: epochs, samples, duration")

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

        else:  # algo is not glm, deeplearning, drf, gbm
            raise ValueError("Plotting not implemented for this type of model")
        if "server" not in list(kwargs.keys()) or not kwargs["server"]: plt.show()


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

        :returns: list of H2OModel objects
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

        :returns: list of H2OFrame objects
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


    def score_history(self):
        """[DEPRECATED]."""
        warnings.warn("`score_history` is deprecated. Use `scoring_history`", category=DeprecationWarning, stacklevel=2)
        return self.scoring_history()
