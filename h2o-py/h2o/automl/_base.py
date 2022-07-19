from __future__ import absolute_import, division, print_function, unicode_literals

import h2o
from h2o.base import Keyed
from h2o.exceptions import H2OValueError
from h2o.job import H2OJob
from h2o.model import ModelBase
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type, is_type


class H2OAutoMLBaseMixin:
    
    def predict(self, test_data):
        """
        Predict on a dataset.

        :param H2OFrame test_data: Data on which to make predictions.

        :returns: A new H2OFrame of predictions.

        :examples:
        
        >>> # Set up an H2OAutoML object
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> # Launch an H2OAutoML run
        >>> aml.train(y=y, training_frame=train)
        >>> # Predict with top model from AutoML Leaderboard on a H2OFrame called 'test'
        >>> aml.predict(test)
        >>>
        >>> # Get AutoML object by `project_name`
        >>> get_aml = h2o.automl.get_automl(aml.project_name)
        >>> # Predict with top model from AutoML Leaderboard on a H2OFrame called 'test'
        >>> get_aml.predict(test)
        """
        return self.leader.predict(test_data)

    # ---------------------------------------------------------------------------
    # Download POJO/MOJO with AutoML
    # ---------------------------------------------------------------------------
    def download_pojo(self, path="", get_genmodel_jar=False, genmodel_name=""):
        """
        Download the POJO for the leader model in AutoML to the directory specified by path.

        If path is an empty string, then dump the output to screen.

        :param path:  An absolute path to the directory where POJO should be saved.
        :param get_genmodel_jar: if True, then also download h2o-genmodel.jar and store it in folder ``path``.
        :param genmodel_name: Custom name of genmodel jar
        :returns: name of the POJO file written.
        """
        return h2o.download_pojo(self.leader, path, get_jar=get_genmodel_jar, jar_name=genmodel_name)

    def download_mojo(self, path=".", get_genmodel_jar=False, genmodel_name=""):
        """
        Download the leader model in AutoML in MOJO format.

        :param path: the path where MOJO file should be saved.
        :param get_genmodel_jar: if True, then also download h2o-genmodel.jar and store it in folder ``path``.
        :param genmodel_name: Custom name of genmodel jar
        :returns: name of the MOJO file written.
        """
        return ModelBase.download_mojo(self.leader, path, get_genmodel_jar, genmodel_name)

    @property
    def project_name(self):
        """
        Retrieve a string indicating the project_name of the automl instance to retrieve.

        :return: a string containing the project_name
        """
        pass

    @property
    def leader(self):
        """
        Retrieve the top model from an H2OAutoML object

        :return: an H2O model

        :examples:
        
        >>> # Set up an H2OAutoML object
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> # Launch an AutoML run
        >>> aml.train(y=y, training_frame=train)
        >>> # Get the best model in the AutoML Leaderboard
        >>> aml.leader
        >>>
        >>> # Get AutoML object by `project_name`
        >>> get_aml = h2o.automl.get_automl(aml.project_name)
        >>> # Get the best model in the AutoML Leaderboard
        >>> get_aml.leader
        """
        pass

    @property
    def leaderboard(self):
        """
        Retrieve the leaderboard from an H2OAutoML object

        :return: an H2OFrame with model ids in the first column and evaluation metric in the second column sorted
                 by the evaluation metric

        :examples:
        
        >>> # Set up an H2OAutoML object
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> # Launch an AutoML run
        >>> aml.train(y=y, training_frame=train)
        >>> # Get the AutoML Leaderboard
        >>> aml.leaderboard
        >>>
        >>> # Get AutoML object by `project_name`
        >>> get_aml = h2o.automl.get_automl(aml.project_name)
        >>> # Get the AutoML Leaderboard
        >>> get_aml.leaderboard
        """
        pass

    @property
    def training_info(self):
        """
        Expose the name/value columns of `event_log` as a simple dictionary, for example `start_epoch`, `stop_epoch`, ...
        See :func:`event_log` to obtain a description of those key/value pairs.

        :return: a dictionary with event_log['name'] column as keys and event_log['value'] column as values.
        """
        pass

    @property
    def event_log(self):
        """
        Retrieve the backend event log from an H2OAutoML object

        :return: an H2OFrame with detailed events occurred during the AutoML training.
        """
        pass
    
    def get_leaderboard(self, extra_columns=None):
        """
        Retrieve the leaderboard.
        Contrary to the default leaderboard attached to the instance, this one can return columns other than the metrics.

        :param extra_columns: a string or a list of string specifying which optional columns should be added to the leaderboard. Defaults to None.
            Currently supported extensions are:
            - 'ALL': adds all columns below.
            - 'training_time_ms': column providing the training time of each model in milliseconds (doesn't include the training of cross validation models).
            - 'predict_time_per_row_ms`: column providing the average prediction time by the model for a single row.
            - 'algo': column providing the algorithm name for each model.
        :return: An H2OFrame representing the leaderboard.
        
        :examples:
        
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> aml.train(y=y, training_frame=train)
        >>> lb_all = aml.get_leaderboard('ALL')
        >>> lb_custom = aml.get_leaderboard(['predict_time_per_row_ms', 'training_time_ms'])
        >>> lb_custom_sorted = lb_custom.sort(by='predict_time_per_row_ms')
        """
        assert isinstance(self, Keyed)
        return _fetch_leaderboard(self.key, extra_columns)

    def get_best_model(self, algorithm=None, criterion=None):
        """
        Get best model of a given family/algorithm for a given criterion from an AutoML object.

        :param algorithm: One of "basemodel", "deeplearning", "drf", "gbm", "glm", "stackedensemble", "xgboost".
                          If None, pick the best model regardless of the algorithm.
        :param criterion: Criterion can be one of the metrics reported in leaderboard. If set to None, the same ordering
                          as in the leaderboard will be used.
                          Avaliable criteria:
                            - Regression metrics: deviance, rmse, mse, mae, rmsle
                            - Binomial metrics: auc, logloss, aucpr, mean_per_class_error, rmse, mse
                            - Multinomial metrics: mean_per_class_error, logloss, rmse, mse
                          The following additional leaderboard information can be also used as a criterion:
                            - 'training_time_ms': column providing the training time of each model in milliseconds (doesn't include the training of cross validation models).
                            - 'predict_time_per_row_ms`: column providing the average prediction time by the model for a single row.
        :return: An H2OModel or None if no model of a given family is present
        
        :examples:
        
        >>> # Set up an H2OAutoML object
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> # Launch an AutoML run
        >>> aml.train(y=y, training_frame=train)
        >>> gbm = aml.get_best_model("gbm")
        """
        from h2o.exceptions import H2OValueError
        
        def _get_models(leaderboard):
            return [m[0] for m in
                    leaderboard["model_id"].as_data_frame(use_pandas=False, header=False)]

        higher_is_better = ["auc", "aucpr"]

        assert_is_type(algorithm, None, str)
        assert_is_type(criterion, None, str)

        if criterion is not None:
            criterion = criterion.lower()

        if "deviance" == criterion:
            criterion = "mean_residual_deviance"

        if algorithm is not None:
            if algorithm.lower() not in ("basemodel", "deeplearning", "drf", "gbm",
                                         "glm", "stackedensemble", "xgboost"):
                raise H2OValueError("Algorithm \"{}\" is not supported!".format(algorithm))
            algorithm = algorithm.lower()

        extra_cols = ["algo"]
        if criterion in ("training_time_ms", "predict_time_per_row_ms"):
            extra_cols.append(criterion)

        leaderboard = h2o.automl.get_leaderboard(self, extra_columns=extra_cols)
        leaderboard = leaderboard if algorithm is None else (
            leaderboard[leaderboard["algo"].tolower() == algorithm, :] if algorithm != "basemodel"
            else leaderboard[leaderboard["algo"].tolower() != "stackedensemble", :])

        if leaderboard.nrow == 0:
            return None

        if criterion is None:
            return h2o.get_model(leaderboard[0, "model_id"])

        if criterion not in leaderboard.columns:
            raise H2OValueError("Criterion \"{}\" is not present in the leaderboard!".format(criterion))

        models_in_default_order = _get_models(leaderboard)
        sorted_lb = leaderboard.sort(by=criterion, ascending=criterion not in higher_is_better)
        selected_models = _get_models(sorted_lb[sorted_lb[criterion] == sorted_lb[0, criterion]])
        picked_model = [model for model in models_in_default_order if model in selected_models][0]

        return h2o.get_model(picked_model)

    def pareto_front(self,
                     test_frame=None,  # type: Optional[H2OFrame]
                     x_metric=None,  # type: Optional[str]
                     y_metric=None,  # type: Optional[str]
                     **kwargs
                     ):
        """
        Create Pareto front and plot it. Pareto front contains models that are optimal in a sense that for each model in the
        Pareto front there isn't a model that would be better in both criteria. For example, this can be useful in picking
        models that are fast to predict and at the same time have high accuracy. For generic data.frames/H2OFrames input
        the task is assumed to be minimization for both metrics.

        :param test_frame: a frame used to generate the metrics
        :param x_metric: metric present in the leaderboard
        :param y_metric: metric present in the leaderboard
        :param kwargs: key, value mappings
                       Other keyword arguments are passed through to
                       :meth:`h2o.explanation.pareto_front`.
        :return: object that contains the resulting figure (can be accessed using ``result.figure()``)

        :examples:
        >>> import h2o
        >>> from h2o.automl import H2OAutoML
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> from h2o.grid import H2OGridSearch
        >>>
        >>> h2o.connect()
        >>>
        >>> # Import the wine dataset into H2O:
        >>> df = h2o.import_file("h2o://prostate.csv")
        >>>
        >>> # Set the response
        >>> response = "CAPSULE"
        >>> df[response] = df[response].asfactor()
        >>>
        >>> # Split the dataset into a train and test set:
        >>> train, test = df.split_frame([0.8])
        >>>
        >>> # Train an H2OAutoML
        >>> aml = H2OAutoML(max_models=10)
        >>> aml.train(y=response, training_frame=train)
        >>>
        >>> # Create the Pareto front
        >>> pf = aml.pareto_front()
        >>> pf.figure() # get the Pareto front plot
        >>> pf # H2OFrame containing the Pareto front subset of the leaderboard
        """
        if test_frame is None:
            leaderboard = self.get_leaderboard("ALL")
        else:
            leaderboard = h2o.make_leaderboard(self, test_frame, extra_columns="ALL")

        if x_metric is None:
            x_metric = "predict_time_per_row_ms"
        if y_metric is None:
            y_metric = leaderboard.columns[1]

        higher_is_better = ("auc", "aucpr")
        optimum = "{} {}".format(
            "top"  if y_metric.lower() in higher_is_better else "bottom",
            "right" if x_metric.lower() in higher_is_better else "left"
        )

        if kwargs.get("title") is None:
            kwargs["title"] = "Pareto Front for {}".format(self.project_name)

        return h2o.explanation.pareto_front(frame=leaderboard,
                                            x_metric=x_metric,
                                            y_metric=y_metric,
                                            optimum=optimum,
                                            **kwargs)


def _fetch_leaderboard(aml_id, extensions=None):
    assert_is_type(extensions, None, str, [str])
    extensions = ([] if extensions is None
                  else [extensions] if is_type(extensions, str)
    else extensions)
    resp = h2o.api("GET /99/Leaderboards/%s" % aml_id, data=dict(extensions=extensions))
    dest_key = resp['project_name'].split('@', 1)[0]+"_custom_leaderboard"
    return _fetch_table(resp['table'], key=dest_key, progress_bar=False)


def _fetch_table(table, key=None, progress_bar=True):
    try:
        # Intentionally mask the progress bar here since showing multiple progress bars is confusing to users.
        # If any failure happens, revert back to user's original setting for progress and display the error message.
        ori_progress_state = H2OJob.__PROGRESS_BAR__
        H2OJob.__PROGRESS_BAR__ = progress_bar
        # Parse leaderboard H2OTwoDimTable & return as an H2OFrame
        fr = h2o.H2OFrame(table.cell_values, destination_frame=key, column_names=table.col_header, column_types=table.col_types)
        return h2o.assign(fr[1:], key) # removing index and reassign id to ensure persistence on backend
    finally:
        H2OJob.__PROGRESS_BAR__ = ori_progress_state


def _fetch_state(aml_id, properties=None, verbosity=None):
    state_json = h2o.api("GET /99/AutoML/%s" % aml_id, data=dict(verbosity=verbosity))
    project_name = state_json["project_name"]
    if project_name is None:
        raise H2OValueError("No AutoML instance with id {}.".format(aml_id))

    leaderboard_list = [key["name"] for key in state_json['leaderboard']['models']]
    leader_id = leaderboard_list[0] if (leaderboard_list is not None and len(leaderboard_list) > 0) else None

    should_fetch = lambda prop: properties is None or prop in properties

    leader = None
    if should_fetch('leader'):
        leader = h2o.get_model(leader_id) if leader_id is not None else None

    leaderboard = None
    if should_fetch('leaderboard'):
        leaderboard = _fetch_table(state_json['leaderboard_table'], key=project_name+"_leaderboard", progress_bar=False)

    event_log = None
    if should_fetch('event_log'):
        event_log = _fetch_table(state_json['event_log_table'], key=project_name+"_eventlog", progress_bar=False)

    return dict(
        project_name=project_name,
        json=state_json,
        leader_id=leader_id,
        leader=leader,
        leaderboard=leaderboard,
        event_log=event_log,
    )

