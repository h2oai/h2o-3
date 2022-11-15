#!/usr/bin/env python
# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

from datetime import datetime
import inspect
import warnings

import h2o
from h2o.base import Keyed
from h2o.exceptions import H2OValueError, H2OResponseError
from h2o.frame import H2OFrame
from h2o.job import H2OJob
from h2o.utils.mixin import assign, load_ext, mixin
from h2o.utils.shared_utils import quoted, LookupSeq
from h2o.utils.typechecks import assert_is_type, is_type, numeric, FunctionType
from h2o.model import ModelBase, H2OSegmentModels
from h2o.model.models import *
from h2o.model.metrics import *


class EstimatorAttributeError(AttributeError):
    def __init__(self, obj, method):
        super(AttributeError, self).__init__("No {} method for {}".format(method, obj.__class__.__name__))


class H2OEstimator(ModelBase):
    """
    Base class for H2O Estimators.

    H2O Estimators implement the following methods for model construction:

        - ``start()`` - Top-level user-facing API for asynchronous model build
        - ``join()``  - Top-level user-facing API for blocking on async model build
        - ``train()`` - Top-level user-facing API for model building.
        - ``fit()`` - Used by scikit-learn.

    Because H2OEstimator instances are instances of ModelBase, these objects can use the H2O model API.
    """
    
    supervised_learning = None  # overridden in implementation
    __default_params = None     # mangled/hidden: use `_default_param_value` to access default param values internally.

    def __init__(self):
        super(H2OEstimator, self).__init__()
        self._model = self

    def start(self, x, y=None, training_frame=None, offset_column=None, fold_column=None,
              weights_column=None, validation_frame=None, **params):
        """
        Train the model asynchronously (to block for results call :meth:`join`).

        :param x: A list of column names or indices indicating the predictor columns.
        :param y: An index or a column name indicating the response column.
        :param H2OFrame training_frame: The H2OFrame having the columns indicated by x and y (as well as any
            additional columns specified by fold, offset, and weights).
        :param offset_column: The name or index of the column in training_frame that holds the offsets.
        :param fold_column: The name or index of the column in training_frame that holds the per-row fold
            assignments.
        :param weights_column: The name or index of the column in training_frame that holds the per-row weights.
        :param validation_frame: H2OFrame with validation data to be scored on while training.
        """
        self._future = True
        self.train(x=x,
                   y=y,
                   training_frame=training_frame,
                   offset_column=offset_column,
                   fold_column=fold_column,
                   weights_column=weights_column,
                   validation_frame=validation_frame,
                   **params)

    def join(self):
        """Wait until job's completion."""
        self._future = False
        self._job.poll()
        model_key = self._job.dest_key
        self._job = None
        model_json = h2o.api("GET /%d/Models/%s" % (self._rest_version, model_key))["models"][0]
        self._resolve_model(model_key, model_json)
    
    def train(self, x=None, y=None, training_frame=None, offset_column=None, fold_column=None,
              weights_column=None, validation_frame=None, max_runtime_secs=None, ignored_columns=None,
              model_id=None, verbose=False):
        """
        Train the H2O model.

        :param x: A list of column names or indices indicating the predictor columns.
        :param y: An index or a column name indicating the response column.
        :param H2OFrame training_frame: The H2OFrame having the columns indicated by x and y (as well as any
            additional columns specified by fold, offset, and weights).
        :param offset_column: The name or index of the column in training_frame that holds the offsets.
        :param fold_column: The name or index of the column in training_frame that holds the per-row fold
            assignments.
        :param weights_column: The name or index of the column in training_frame that holds the per-row weights.
        :param validation_frame: H2OFrame with validation data to be scored on while training.
        :param float max_runtime_secs: Maximum allowed runtime in seconds for model training. Use 0 to disable.
        :param bool verbose: Print scoring history to stdout. Defaults to False.
        """
        parms = self._make_parms(x=x, y=y, training_frame=training_frame, offset_column=offset_column, 
                                 fold_column=fold_column, weights_column=weights_column, 
                                 validation_frame=validation_frame, max_runtime_secs=max_runtime_secs, 
                                 ignored_columns=ignored_columns, model_id=model_id, verbose=verbose)
        self._train(parms, verbose=verbose)
        return self

    def train_segments(self, x=None, y=None, training_frame=None, offset_column=None, fold_column=None,
                       weights_column=None, validation_frame=None, max_runtime_secs=None, ignored_columns=None,
                       segments=None, segment_models_id=None, parallelism=1, verbose=False):
        """
        Trains H2O model for each segment (subpopulation) of the training dataset.

        :param x: A list of column names or indices indicating the predictor columns.
        :param y: An index or a column name indicating the response column.
        :param H2OFrame training_frame: The H2OFrame having the columns indicated by x and y (as well as any
            additional columns specified by fold, offset, and weights).
        :param offset_column: The name or index of the column in training_frame that holds the offsets.
        :param fold_column: The name or index of the column in training_frame that holds the per-row fold
            assignments.
        :param weights_column: The name or index of the column in training_frame that holds the per-row weights.
        :param validation_frame: H2OFrame with validation data to be scored on while training.
        :param float max_runtime_secs: Maximum allowed runtime in seconds for each model training. Use 0 to disable.
            Please note that regardless of how this parameter is set, a model will be built for each input segment.
            This parameter only affects individual model training.
        :param segments: A list of columns to segment-by. H2O will group the training (and validation) dataset
            by the segment-by columns and train a separate model for each segment (group of rows).
            As an alternative to providing a list of columns, users can also supply an explicit enumeration of
            segments to build the models for. This enumeration needs to be represented as H2OFrame.
        :param segment_models_id: Identifier for the returned collection of Segment Models. If not specified
            it will be automatically generated.
        :param parallelism: Level of parallelism of the bulk segment models building, it is the maximum number 
            of models each H2O node will be building in parallel.
        :param bool verbose: Enable to print additional information during model building. Defaults to False.

        :examples:

        >>> response = "survived"
        >>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
        >>> titanic[response] = titanic[response].asfactor()
        >>> predictors = ["survived","name","sex","age","sibsp","parch","ticket","fare","cabin"]
        >>> train, valid = titanic.split_frame(ratios=[.8], seed=1234)
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> titanic_gbm = H2OGradientBoostingEstimator(seed=1234)
        >>> titanic_models = titanic_gbm.train_segments(segments=["pclass"],
        ...                                             x=predictors,
        ...                                             y=response,
        ...                                             training_frame=train,
        ...                                             validation_frame=valid)
        >>> titanic_models.as_frame()
        """
        assert_is_type(segments, None, H2OFrame, [str])
        assert_is_type(verbose, bool)
        assert_is_type(segment_models_id, None, str)
        assert_is_type(parallelism, int)

        if segments is None:
            raise H2OValueError("Parameter segments was not specified. Please provide either a list of columns to "
                                "segment-by or an explicit list of segments to build models for.")

        parms = self._make_parms(x=x, y=y, training_frame=training_frame, offset_column=offset_column,
                                 fold_column=fold_column, weights_column=weights_column,
                                 validation_frame=validation_frame, max_runtime_secs=max_runtime_secs,
                                 ignored_columns=ignored_columns, model_id=None, verbose=verbose)

        if isinstance(segments, H2OFrame):
            parms["segments"] = H2OEstimator._keyify(segments)
        else:
            parms["segment_columns"] = segments
        if segment_models_id:
            parms["segment_models_id"] = segment_models_id
        parms["parallelism"] = parallelism

        rest_ver = self._get_rest_version(parms)
        train_segments_response = h2o.api("POST /%d/SegmentModelsBuilders/%s" % (rest_ver, self.algo), data=parms)
        job = H2OJob(train_segments_response, job_type=(self.algo + " Segment Models Build"))
        job.poll()
        return H2OSegmentModels(job.dest_key)

    def _train(self, parms, verbose=False):
        assert_is_type(verbose, bool)

        rest_ver = self._get_rest_version(parms)
        model_builder_json = h2o.api("POST /%d/ModelBuilders/%s" % (rest_ver, self.algo), data=parms)
        job = H2OJob(model_builder_json, job_type=(self.algo + " Model Build"))

        if model_builder_json["messages"] is not None:
            for mesg in model_builder_json["messages"]:
                if mesg["message_type"] == "WARN":
                    warnings.warn(mesg["message"], RuntimeWarning)

        if self._future:
            self._job = job
            self._rest_version = rest_ver
            return

        job.poll(poll_updates=self._print_model_scoring_history if verbose else None)
        model_json = h2o.api("GET /%d/Models/%s" % (rest_ver, job.dest_key))["models"][0]
        self._resolve_model(job.dest_key, model_json)

    def _make_parms(self, x=None, y=None, training_frame=None, offset_column=None, fold_column=None,
                    weights_column=None, validation_frame=None, max_runtime_secs=None, ignored_columns=None,
                    model_id=None, verbose=False, extend_parms_fn=None):
        has_default_training_frame = hasattr(self, 'training_frame') and self.training_frame is not None
        training_frame = H2OFrame._validate(training_frame, 'training_frame',
                                            required=self._options_.get('requires_training_frame', True) and not has_default_training_frame)
        validation_frame = H2OFrame._validate(validation_frame, 'validation_frame')
        assert_is_type(y, None, int, str)
        assert_is_type(x, None, int, str, ModelBase, [str, int], {str, int})
        assert_is_type(ignored_columns, None, [str, int], {str, int})
        assert_is_type(offset_column, None, int, str)
        assert_is_type(fold_column, None, int, str)
        assert_is_type(weights_column, None, int, str)
        assert_is_type(max_runtime_secs, None, numeric)
        assert_is_type(model_id, None, str)
        assert_is_type(verbose, bool)
        assert_is_type(extend_parms_fn, None, FunctionType)
    
        override_default_training_frame = training_frame is not None
        if not override_default_training_frame:
            self._verify_training_frame_params(offset_column, fold_column, weights_column, validation_frame)
            training_frame = self.training_frame if has_default_training_frame else None
    
        if verbose and not self._options_.get('verbose', False):
            raise H2OValueError("Verbose mode is not available for %s" % self.__class__.__name__)
        parms = self._parms.copy()
        names = LookupSeq(training_frame.names if training_frame is not None else [])
        ncols = training_frame.ncols if training_frame is not None else 0
        types = training_frame.types if training_frame is not None else {}
    
        if self.supervised_learning:
            if y is None: y = "response"
            if is_type(y, int):
                if not (-ncols <= y < ncols):
                    raise H2OValueError("Column %d does not exist in the training frame" % y)
                y = names[y]
            else:
                if y not in names:
                    raise H2OValueError("Column %s does not exist in the training frame" % y)
            self._estimator_type = "classifier" if types[y] == "enum" else "regressor"
        else:
            # If `y` is provided for an unsupervised model we'll simply ignore
            # it. This way an unsupervised model can be used as a step in
            # sklearn's pipeline.
            y = None
            self._estimator_type = "unsupervised"
    
        if override_default_training_frame:
            assert_is_type(y, str, None)
            ignored_columns_set = set()
            if ignored_columns is None and "ignored_columns" in parms:
                ignored_columns = parms['ignored_columns']
            if ignored_columns is not None:
                if x is not None:
                    raise H2OValueError("Properties x and ignored_columns cannot be specified simultaneously")
                for ic in ignored_columns:
                    if is_type(ic, int):
                        if not (-ncols <= ic < ncols):
                            raise H2OValueError("Column %d does not exist in the training frame" % ic)
                        ignored_columns_set.add(names[ic])
                    else:
                        if ic not in names:
                            raise H2OValueError("Column %s not in the training frame" % ic)
                        ignored_columns_set.add(ic)
            if x is None:
                xset = names.set() - {y} - ignored_columns_set
            elif isinstance(x, ModelBase) and hasattr(x, '_extract_x_from_model'):
                xset = x._extract_x_from_model()
            else:
                xset = set()
                if is_type(x, int, str): x = [x]
                for xi in x:
                    if is_type(xi, int):
                        if not (-ncols <= xi < ncols):
                            raise H2OValueError("Column %d does not exist in the training frame" % xi)
                        xset.add(names[xi])
                    else:
                        if xi not in names:
                            raise H2OValueError("Column %s not in the training frame" % xi)
                        xset.add(xi)
            x = list(xset)
            self._check_and_save_parm(parms, "offset_column", offset_column)
            self._check_and_save_parm(parms, "weights_column", weights_column)
            self._check_and_save_parm(parms, "fold_column", fold_column)
    
        if max_runtime_secs is not None: parms["max_runtime_secs"] = max_runtime_secs
    
        # Overwrites the model_id parameter only if model_id is passed
        if model_id is not None:
            parms["model_id"] = model_id
        if override_default_training_frame:
            parms["training_frame"] = training_frame
            offset = parms["offset_column"]
            folds = parms["fold_column"]
            weights = parms["weights_column"]
    
        if validation_frame is not None:
            parms["validation_frame"] = validation_frame
    
        if is_type(y, int):
            y = names[y]
        if y is not None:
            parms["response_column"] = y
        if not isinstance(x, (list, tuple)):
            x = [x]
        if len(x) > 0 and is_type(x[0], int):
            x = [names[i] for i in x]
        if override_default_training_frame:
            ignored_columns = list(names.set() - set(x + [y, offset, folds, weights]))
            parms["ignored_columns"] = None if len(ignored_columns) == 0 else [quoted(col) for col in ignored_columns]
        parms["interactions"] = (None if "interactions" not in parms or parms["interactions"] is None
                                 else [quoted(col) for col in parms["interactions"]])
        parms["interaction_pairs"] = (None if "interaction_pairs" not in parms or parms["interaction_pairs"] is None
                                      else [tuple(map(quoted, ip)) for ip in parms["interaction_pairs"]])
    
        # internal hook allowing subclasses to extend train parms 
        if extend_parms_fn is not None:
            extend_parms_fn(parms)
    
        parms = {k: H2OEstimator._keyify(v) for k, v in parms.items()}
        if "r2" in (parms.get('stopping_metric') or []):
            raise H2OValueError("r2 cannot be used as an early stopping_metric yet.  Check this JIRA https://0xdata.atlassian.net/browse/PUBDEV-5381 for progress.")
        return parms

    def _get_rest_version(self, parms):
        return parms.pop("_rest_version") if "_rest_version" in parms else 3

    def _print_model_scoring_history(self, job, bar_progress=0):
        """
        the callback function used to poll/print updates during model training.
        """
        if int(bar_progress * 10) % 5 > 0:
            return
        try:
            model = h2o.get_model(job.job['dest']['name'])
            print("\nScoring History for Model " + str(model.model_id) + " at " + str(datetime.now()))
            print("Model Build is {0:.0f}% done...".format(job.progress*100))
            print(model.scoring_history().tail())
            print("\n")
        except H2OResponseError:  # To catch 400 error
            print("Model build is starting now...")
        except AttributeError:  # To catch NoneType error if scoring history is not available
            print("Scoring History is not available yet...")

    @staticmethod
    def _keyify(item):
        if isinstance(item, Keyed):
            return item.key
        elif isinstance(item, list) and any(isinstance(i, Keyed) for i in item):
            return [quoted(H2OEstimator._keyify(i)) for i in item]
        else:
            return item

    def _resolve_model(self, model_id, model_json):
        metrics_class, model_class, metrics_class_valid = H2OEstimator._metrics_class(model_json)
        m = model_class() if model_class else self._model
        m._id = model_id
        m._model_json = model_json
        m._have_pojo = model_json.get('have_pojo', True)
        m._have_mojo = model_json.get('have_mojo', True)
        m._start_time = model_json.get('output', {}).get('start_time', None)
        m._end_time = model_json.get('output', {}).get('end_time', None)
        m._run_time = model_json.get('output', {}).get('run_time', None)
        m._metrics_class = metrics_class
        m._metrics_class_valid = metrics_class_valid
        m._parms = self._parms
        m._estimator_type = self._estimator_type
        m._options_ = self._options_

        if model_id is not None and model_json is not None and metrics_class is not None:
            # build Metric objects out of each metrics
            for metric in ["training_metrics", "validation_metrics", "cross_validation_metrics"]:
                metrics = model_json["output"].get(metric, None)
                if metrics is not None:
                    if metric == "cross_validation_metrics":
                        m._is_xvalidated = True
                    mc = metrics_class_valid if metric == "validation_metrics" else metrics_class  
                    # fixme: never ever ever modify the original payload!!! put the metric object somewhere else
                    model_json["output"][metric] = mc(metrics, metric, model_json["algo"])
            # if m._is_xvalidated:
            if m._is_xvalidated and model_json["output"]["cross_validation_models"] is not None:
                m._xval_keys = [i["name"] for i in model_json["output"]["cross_validation_models"]]

            # build a useful dict of the params
            for p in m._model_json["parameters"]:
                m.parms[p["name"]] = p
                
        extensions = [load_ext(ext) for ext in self._options_.get('model_extensions', [])]
        mixin(self._model, model_class, *extensions)
        assign(self._model, m)

    # ----- Scikit-learn Interface Methods -------

    def fit(self, X, y=None, **params):
        """
        Fit an H2O model as part of a scikit-learn pipeline or grid search.

        A warning will be issued if a caller other than sklearn attempts to use this method.

        :param H2OFrame X: An H2OFrame consisting of the predictor variables.
        :param H2OFrame y: An H2OFrame consisting of the response variable.
        :param params: Extra arguments.
        :returns: The current instance of H2OEstimator for method chaining.
        """
        stk = inspect.stack()[1:]
        warn = True
        for s in stk:
            mod = inspect.getmodule(s[0])
            if mod:
                warn = "sklearn" not in mod.__name__
                if not warn: break
        if warn:
            warnings.warn("\n\n\t`fit` is not recommended outside of the sklearn framework. Use `train` instead.",
                          UserWarning, stacklevel=2)
        training_frame = X.cbind(y) if y is not None else X
        x = X.names
        y = y.names[0] if y is not None else None
        self.train(x, y, training_frame, **params)
        return self

    def get_params(self, deep=True):
        """
        Obtain parameters for this estimator.

        Used primarily for sklearn Pipelines and sklearn grid search.

        :param deep: If True, return parameters of all sub-objects that are estimators.

        :returns: A dict of parameters
        """
        out = dict()
        for key, value in self._parms.items():
            if key.startswith('_'): continue  # skip internal params
            if deep and isinstance(value, H2OEstimator):
                deep_items = list(value.get_params().items())
                out.update((key + "__" + k, val) for k, val in deep_items)
            out[key] = value
        return out

    def set_params(self, **parms):
        """
        Used by sklearn for updating parameters during grid search.

        :param parms: A dictionary of parameters that will be set on this model.
        :returns: self, the current estimator object with the parameters all set as desired.
        """
        self._parms.update(parms)
        return self

    def _verify_training_frame_params(self, *args):
        for param in args:
            if param is not None:
                raise H2OValueError("No training frame defined, yet the parameter %d is has been specified.", param)

    @staticmethod
    def _metrics_class(model_json):
        model_type = model_json["output"]["model_category"]
        valid_metrics_class = None
        if model_type == "Binomial":
            metrics_class = H2OBinomialModelMetrics
            model_class = H2OBinomialModel
        elif model_type == "BinomialUplift":
            metrics_class = H2OBinomialUpliftModelMetrics
            model_class = H2OBinomialUpliftModel
        elif model_type == "Clustering":
            metrics_class = H2OClusteringModelMetrics
            model_class = H2OClusteringModel
        elif model_type == "Regression":
            metrics_class = H2ORegressionModelMetrics
            model_class = H2ORegressionModel
        elif model_type == "Multinomial":
            metrics_class = H2OMultinomialModelMetrics
            model_class = H2OMultinomialModel
        elif model_type == "Ordinal":
            metrics_class = H2OOrdinalModelMetrics
            model_class = H2OOrdinalModel
        elif model_type == "AutoEncoder":
            metrics_class = H2ODefaultModelMetrics
            model_class = H2OAutoEncoderModel
        elif model_type == "DimReduction":
            metrics_class = H2ODimReductionModelMetrics
            model_class = H2ODimReductionModel
        elif model_type == "WordEmbedding":
            metrics_class = H2ODefaultModelMetrics
            model_class = H2OWordEmbeddingModel
        elif model_type == "AnomalyDetection":
            metrics_class = H2OAnomalyDetectionModelMetrics
            valid_metrics_class = H2OBinomialModelMetrics
            model_class = H2OAnomalyDetectionModel
        elif model_type == "CoxPH":
            metrics_class = H2ORegressionCoxPHModelMetrics
            model_class = H2OCoxPHModel if model_json["algo"] != "generic" else H2OCoxPHMojoModel
        elif model_type == "TargetEncoder":
            metrics_class = H2ODefaultModelMetrics 
            model_class = None
        elif model_type == "Unknown":
            metrics_class = None
            model_class = None
        else:
            raise NotImplementedError(model_type)
        if valid_metrics_class is None:
            valid_metrics_class = metrics_class
        return [metrics_class, model_class, valid_metrics_class]

    def _check_and_save_parm(self, parms, parameter_name, parameter_value):
        """
        If a parameter is not stored in parms dict save it there (even though the value is None).
        Else check if the parameter has been already set during initialization of estimator. 
            If yes, check the new value is the same or not.
            If the values are different, set the last passed value to params dict and throw UserWarning.
        """
        if parameter_name not in parms:
            parms[parameter_name] = parameter_value
        elif parameter_value is not None and parms[parameter_name] != parameter_value:
            prev_value = parms[parameter_name]
            parms[parameter_name] = parameter_value
            if prev_value != self._default_param_value(parameter_name):
                warnings.warn("\n\n\t`%s` parameter has been already set and had a different value in `train` method."
                              " The last passed value \"%s\" is used." % (parameter_name, parameter_value), 
                              UserWarning, 
                              stacklevel=4)  # warning should refer to the original call to `train`, `train_xxx`

    @classmethod
    def _default_param_value(cls, param_name):
        if cls.__default_params is None:
            cls.__default_params = cls()
        return getattr(cls.__default_params, param_name)
