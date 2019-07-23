# -*- encoding: utf-8 -*-
import functools as ft

import h2o
from h2o.base import Keyed
from h2o.exceptions import H2OValueError
from h2o.frame import H2OFrame
from h2o.job import H2OJob
from h2o.model.model_base import ModelBase
from h2o.utils.typechecks import assert_is_type, is_type


class H2OAutoML(Keyed):
    """
    Automatic Machine Learning

    The Automatic Machine Learning (AutoML) function automates the supervised machine learning model training process.
    The current version of AutoML trains and cross-validates 
    a Random Forest (DRF), 
    an Extremely-Randomized Forest (DRF/XRT),
    a random grid of Generalized Linear Models (GLM)
    a random grid of XGBoost (XGBoost),
    a random grid of Gradient Boosting Machines (GBM), 
    a random grid of Deep Neural Nets (DeepLearning), 
    and 2 Stacked Ensembles, one of all the models, and one of only the best models of each kind.

    :examples:
    >>> import h2o
    >>> from h2o.automl import H2OAutoML
    >>> h2o.init()
    >>> # Import a sample binary outcome train/test set into H2O
    >>> train = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
    >>> test = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")
    >>> # Identify the response and set of predictors
    >>> y = "response"
    >>> x = list(train.columns)  #if x is defined as all columns except the response, then x is not required
    >>> x.remove(y)
    >>> # For binary classification, response should be a factor
    >>> train[y] = train[y].asfactor()
    >>> test[y] = test[y].asfactor()
    >>> # Run AutoML for 30 seconds
    >>> aml = H2OAutoML(max_runtime_secs = 30)
    >>> aml.train(x = x, y = y, training_frame = train)
    >>> # Print Leaderboard (ranked by xval metrics)
    >>> aml.leaderboard
    >>> # (Optional) Evaluate performance on a test set
    >>> perf = aml.leader.model_performance(test)
    >>> perf.auc()
    """
    
    def __init__(self,
                 nfolds=5,
                 balance_classes=False,
                 class_sampling_factors=None,
                 max_after_balance_size=5.0,
                 max_runtime_secs=3600,
                 max_runtime_secs_per_model=None,
                 max_models=None,
                 stopping_metric="AUTO",
                 stopping_tolerance=None,
                 stopping_rounds=3,
                 seed=None,
                 project_name=None,
                 exclude_algos=None,
                 include_algos=None,
                 keep_cross_validation_predictions=False,
                 keep_cross_validation_models=False,
                 keep_cross_validation_fold_assignment=False,
                 sort_metric="AUTO",
                 export_checkpoints_dir=None,
                 verbosity=None):
        """
        Create a new H2OAutoML instance.
        
        :param int nfolds: Number of folds for k-fold cross-validation. Defaults to ``5``. Use ``0`` to disable cross-validation; this will also 
          disable Stacked Ensemble (thus decreasing the overall model performance).
        :param bool balance_classes: Balance training data class counts via over/under-sampling (for imbalanced data).  Defaults to ``False``.
        :param class_sampling_factors: Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling
          factors will be automatically computed to obtain class balance during training. Requires ``balance_classes``.
        :param float max_after_balance_size: Maximum relative size of the training data after balancing class counts (can be less than 1.0).
          Requires ``balance_classes``. Defaults to ``5.0``.
        :param int max_runtime_secs: This argument controls how long the AutoML run will execute. Defaults to ``3600`` seconds (1 hour).
        :param int max_runtime_secs_per_model: This argument controls the max time the AutoML run will dedicate to each individual model. Defaults to `0` (disabled).
        :param int max_models: Specify the maximum number of models to build in an AutoML run. (Does not include the Stacked Ensemble models.)
        :param str stopping_metric: Specifies the metric to use for early stopping. Defaults to ``"AUTO"``.
          The available options are:
          ``"AUTO"`` (This defaults to ``"logloss"`` for classification, ``"deviance"`` for regression),
          ``"deviance"``, ``"logloss"``, ``"mse"``, ``"rmse"``, ``"mae"``, ``"rmsle"``, ``"auc"``, ``"lift_top_group"``,
          ``"misclassification"``, ``"mean_per_class_error"``, ``"r2"``.
        :param float stopping_tolerance: This option specifies the relative tolerance for the metric-based stopping
          to stop the AutoML run if the improvement is less than this value. This value defaults to ``0.001``
          if the dataset is at least 1 million rows; otherwise it defaults to a value determined by the size of the dataset
          and the non-NA-rate.  In that case, the value is computed as 1/sqrt(nrows * non-NA-rate).
        :param int stopping_rounds: This argument stops training new models in the AutoML run when the option selected for
          stopping_metric doesn't improve for the specified number of models, based on a simple moving average.
          To disable this feature, set it to ``0``. Defaults to ``3`` and must be an non-negative integer.
        :param int seed: Set a seed for reproducibility. AutoML can only guarantee reproducibility if ``max_models`` or
          early stopping is used because ``max_runtime_secs`` is resource limited, meaning that if the resources are
          not the same between runs, AutoML may be able to train more models on one run vs another.  Defaults to ``None``.
        :param str project_name: Character string to identify an AutoML project. Defaults to ``None``, which means
          a project name will be auto-generated based on the training frame ID.  More models can be trained on an
          existing AutoML project by specifying the same project name in muliple calls to the AutoML function
          (as long as the same training frame is used in subsequent runs).
        :param exclude_algos: List of character strings naming the algorithms to skip during the model-building phase. 
          An example use is ``exclude_algos = ["GLM", "DeepLearning", "DRF"]``, and the full list of options is: ``"DRF"`` 
          (Random Forest and Extremely-Randomized Trees), ``"GLM"``, ``"XGBoost"``, ``"GBM"``, ``"DeepLearning"`` and ``"StackedEnsemble"``. 
          Defaults to ``None``, which means that all appropriate H2O algorithms will be used, if the search stopping criteria allow. Optional.
        :param include_algos: List of character strings naming the algorithms to restrict to during the model-building phase.
          This can't be used in combination with `exclude_algos` param.
          Defaults to ``None``, which means that all appropriate H2O algorithms will be used, if the search stopping criteria allow. Optional.
        :param keep_cross_validation_predictions: Whether to keep the predictions of the cross-validation predictions.
          This needs to be set to ``True`` if running the same AutoML object for repeated runs because CV predictions are required to build 
          additional Stacked Ensemble models in AutoML. This option defaults to ``False``.
        :param keep_cross_validation_models: Whether to keep the cross-validated models. Keeping cross-validation models may consume 
          significantly more memory in the H2O cluster. Defaults to ``False``.
        :param keep_cross_validation_fold_assignment: Whether to keep fold assignments in the models. Deleting them will save memory 
          in the H2O cluster. This option defaults to ``False``.
        :param sort_metric: Metric to sort the leaderboard by. Defaults to ``"AUTO"`` (This defaults to ``auc`` for binomial classification, 
          ``mean_per_class_error`` for multinomial classification, ``deviance`` for regression). For binomial classification choose between 
          ``auc``, ``"logloss"``, ``"mean_per_class_error"``, ``"rmse"``, ``"mse"``.  For regression choose between ``"deviance"``, ``"rmse"``, 
          ``"mse"``, ``"mae"``, ``"rmlse"``. For multinomial classification choose between ``"mean_per_class_error"``, ``"logloss"``, ``"rmse"``, ``"mse"``.
        :param export_checkpoints_dir: Path to a directory where every model will be stored in binary form.
        :param verbosity: Verbosity of the backend messages printed during training.
            Available options are 'debug', 'info' or 'warn'. Defaults to None (disable live log).
        """
        # Check if H2O jar contains AutoML
        try:
            h2o.api("GET /3/Metadata/schemas/AutoMLV99")
        except h2o.exceptions.H2OResponseError as e:
            print(e)
            print("*******************************************************************\n" \
                  "*Please verify that your H2O jar has the proper AutoML extensions.*\n" \
                  "*******************************************************************\n" \
                  "\nVerbose Error Message:")

        
        # Make bare minimum build_control (if max_runtimes_secs is an invalid value, it will catch below)
        self.build_control = {
            'stopping_criteria': {
                'max_runtime_secs': max_runtime_secs,
            }
        }

        # Make bare minimum build_models
        self.build_models = {
            'exclude_algos': None
        }

        # nfolds must be an non-negative integer and not equal to 1:
        if nfolds is not 5:
            assert_is_type(nfolds,int)
        assert nfolds >= 0, "nfolds set to " + str(nfolds) + "; nfolds cannot be negative. Use nfolds >=2 if you want cross-valiated metrics and Stacked Ensembles or use nfolds = 0 to disable."
        assert nfolds is not 1, "nfolds set to " + str(nfolds) + "; nfolds = 1 is an invalid value. Use nfolds >=2 if you want cross-valiated metrics and Stacked Ensembles or use nfolds = 0 to disable."           
        self.build_control["nfolds"] = nfolds
        self.nfolds = nfolds

        # Pass through to all algorithms
        if balance_classes is True:
            self.build_control["balance_classes"] = balance_classes
            self.balance_classes = balance_classes
        if class_sampling_factors is not None:
            self.build_control["class_sampling_factors"] = class_sampling_factors
            self.class_sampling_factors = class_sampling_factors
        if max_after_balance_size != 5.0:
            assert_is_type(max_after_balance_size, float)
            self.build_control["max_after_balance_size"] = max_after_balance_size
            self.max_after_balance_size = max_after_balance_size

        # If max_runtime_secs is not provided, then it is set to default (3600 secs)
        if max_runtime_secs is not 3600:
            assert_is_type(max_runtime_secs, int)
        self.max_runtime_secs = max_runtime_secs

        assert_is_type(max_runtime_secs_per_model, None, int)
        self.max_runtime_secs_per_model = max_runtime_secs_per_model
        if self.max_runtime_secs_per_model is not None:
            self.build_control["stopping_criteria"]["max_runtime_secs_per_model"] = self.max_runtime_secs_per_model

        # Add other parameters to build_control if available
        if max_models is not None:
            assert_is_type(max_models, int)
            self.build_control["stopping_criteria"]["max_models"] = max_models
        self.max_models = max_models

        if stopping_metric is not "AUTO":
            assert_is_type(stopping_metric, str)
        self.build_control["stopping_criteria"]["stopping_metric"] = stopping_metric
        self.stopping_metric = stopping_metric

        if stopping_tolerance is not None:
            assert_is_type(stopping_tolerance, float)
            self.build_control["stopping_criteria"]["stopping_tolerance"] = stopping_tolerance
        self.stopping_tolerence = stopping_tolerance

        if stopping_rounds is not 3:
            assert_is_type(stopping_rounds, int)
        self.build_control["stopping_criteria"]["stopping_rounds"] = stopping_rounds
        self.stopping_rounds = stopping_rounds    

        if seed is not None:
            assert_is_type(seed, int)
            self.build_control["stopping_criteria"]["seed"] = seed
            self.seed = seed

        # Set project name if provided. If None, then we set in .train() to "automl_" + training_frame.frame_id
        if project_name is not None:
            assert_is_type(project_name, str)
            self.build_control["project_name"] = project_name
            self.project_name = project_name
        else:
            self.project_name = None

        if exclude_algos is not None:
            assert_is_type(exclude_algos, list)
            for elem in exclude_algos:
                assert_is_type(elem, str)
            self.build_models['exclude_algos'] = exclude_algos

        if include_algos is not None:
            assert exclude_algos is None, "Use either include_algos or exclude_algos, not both."
            assert_is_type(include_algos, list)
            for elem in include_algos:
                assert_is_type(elem, str)
            self.build_models['include_algos'] = include_algos

        assert_is_type(keep_cross_validation_predictions, bool)
        self.build_control["keep_cross_validation_predictions"] = keep_cross_validation_predictions

        assert_is_type(keep_cross_validation_models, bool)
        self.build_control["keep_cross_validation_models"] = keep_cross_validation_models

        assert_is_type(keep_cross_validation_fold_assignment, bool)
        self.build_control["keep_cross_validation_fold_assignment"] = self.nfolds != 0 and keep_cross_validation_fold_assignment

        self._job = None
        self._leader_id = None
        self._leaderboard = None
        self._verbosity = verbosity
        self._event_log = None
        self._training_info = None
        self._state_json = None
        if sort_metric == "AUTO":
            self.sort_metric = None
        else:
            self.sort_metric = sort_metric

        if export_checkpoints_dir is not None:
            assert_is_type(export_checkpoints_dir, str)
            self.build_control["export_checkpoints_dir"] = export_checkpoints_dir


    #---------------------------------------------------------------------------
    # Basic properties
    #---------------------------------------------------------------------------
    @property
    def key(self):
        return self.project_name

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
        """
        return None if self._leader_id is None else h2o.get_model(self._leader_id)

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
        """
        return H2OFrame([]) if self._leaderboard is None else self._leaderboard

    @property
    def event_log(self):
        """
        retrieve the backend event log from an H2OAutoML object

        :return: an H2OFrame with detailed events occurred during the AutoML training.
        """
        return H2OFrame([]) if self._event_log is None else self._event_log

    @property
    def training_info(self):
        """
        expose the name/value columns of `event_log` as a simple dictionary, for example `start_epoch`, `stop_epoch`, ...
        See :func:`event_log` to obtain a description of those key/value pairs.

        :return: a dictionary with event_log['name'] column as keys and event_log['value'] column as values.
        """
        return dict() if self._training_info is None else self._training_info

    #---------------------------------------------------------------------------
    # Training AutoML
    #---------------------------------------------------------------------------
    def train(self, x=None, y=None, training_frame=None, fold_column=None,
              weights_column=None, validation_frame=None, leaderboard_frame=None, blending_frame=None):
        """
        Begins an AutoML task, a background task that automatically builds a number of models
        with various algorithms and tracks their performance in a leaderboard. At any point 
        in the process you may use H2O's performance or prediction functions on the resulting 
        models.

        :param x: A list of column names or indices indicating the predictor columns.
        :param y: An index or a column name indicating the response column.
        :param fold_column: The name or index of the column in training_frame that holds per-row fold
            assignments.
        :param weights_column: The name or index of the column in training_frame that holds per-row weights.
        :param training_frame: The H2OFrame having the columns indicated by x and y (as well as any
            additional columns specified by fold_column or weights_column).
        :param validation_frame: H2OFrame with validation data. This argument is ignored unless the user sets 
            nfolds = 0. If cross-validation is turned off, then a validation frame can be specified and used 
            for early stopping of individual models and early stopping of the grid searches.  By default and 
            when nfolds > 1, cross-validation metrics will be used for early stopping and thus validation_frame will be ignored.
        :param leaderboard_frame: H2OFrame with test data for scoring the leaderboard.  This is optional and
            if this is set to None (the default), then cross-validation metrics will be used to generate the leaderboard 
            rankings instead.
        :param blending_frame: H2OFrame used to train the the metalearning algorithm in Stacked Ensembles (instead of relying on cross-validated predicted values).
            This is optional, but when provided, it is also recommended to disable cross validation 
            by setting `nfolds=0` and to provide a leaderboard frame for scoring purposes.

        :returns: An H2OAutoML object.

        :examples:
        >>> # Set up an H2OAutoML object
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> # Launch an AutoML run
        >>> aml.train(y=y, training_frame=train)
        """
        training_frame = H2OFrame._validate(training_frame, 'training_frame', required=True)
        ncols = training_frame.ncols
        names = training_frame.names

        #Set project name if None
        if self.project_name is None:
            self.project_name = "automl_" + training_frame.frame_id
            self.build_control["project_name"] = self.project_name

        # Minimal required arguments are training_frame and y (response)
        if y is None:
            raise ValueError('The response column (y) is not set; please set it to the name of the column that you are trying to predict in your data.')
        else:
            assert_is_type(y,int,str)
            if is_type(y, int):
                if not (-ncols <= y < ncols):
                    raise H2OValueError("Column %d does not exist in the training frame" % y)
                y = names[y]
            else:
                if y not in names:
                    raise H2OValueError("Column %s does not exist in the training frame" % y)
            input_spec = {
                'response_column': y,
            }

        input_spec['training_frame'] = training_frame.frame_id

        if fold_column is not None:
            assert_is_type(fold_column,int,str)
            input_spec['fold_column'] = fold_column

        if weights_column is not None:
            assert_is_type(weights_column,int,str)
            input_spec['weights_column'] = weights_column

        if validation_frame is not None:
            validation_frame = H2OFrame._validate(validation_frame, 'validation_frame')
            input_spec['validation_frame'] = validation_frame.frame_id

        if leaderboard_frame is not None:
            leaderboard_frame = H2OFrame._validate(leaderboard_frame, 'leaderboard_frame')
            input_spec['leaderboard_frame'] = leaderboard_frame.frame_id

        if blending_frame is not None:
            blending_frame = H2OFrame._validate(blending_frame, 'blending_frame')
            input_spec['blending_frame'] = blending_frame.frame_id

        if self.sort_metric is not None:
            assert_is_type(self.sort_metric, str)
            sort_metric = self.sort_metric.lower()
            # Changed the API to use "deviance" to be consistent with stopping_metric values
            # TO DO: let's change the backend to use "deviance" since we use the term "deviance"
            # After that we can take this `if` statement out
            if sort_metric == "deviance":
                sort_metric = "mean_residual_deviance"
            input_spec['sort_metric'] = sort_metric

        if x is not None:
            assert_is_type(x,list)
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
            ignored_columns = set(names) - {y} - set(x)
            if fold_column is not None and fold_column in ignored_columns:
                ignored_columns.remove(fold_column)
            if weights_column is not None and weights_column in ignored_columns:
                ignored_columns.remove(weights_column)
            if ignored_columns is not None:
                input_spec['ignored_columns'] = list(ignored_columns)

        automl_build_params = dict(input_spec=input_spec)

        # NOTE: if the user hasn't specified some block of parameters don't send them!
        # This lets the back end use the defaults.
        automl_build_params['build_control'] = self.build_control
        automl_build_params['build_models'] = self.build_models

        resp = h2o.api('POST /99/AutoMLBuilder', json=automl_build_params)
        if 'job' not in resp:
            print("Exception from the back end: ")
            print(resp)
            return

        self._job = H2OJob(resp['job'], "AutoML")
        poll_updates = ft.partial(self._poll_training_updates, verbosity=self._verbosity, state={})
        try:
            self._job.poll(poll_updates=poll_updates)
        finally:
            poll_updates(self._job, 1)

        self._fetch()

    #---------------------------------------------------------------------------
    # Predict with AutoML
    #---------------------------------------------------------------------------
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

        """
        leader = self.leader
        if leader is None:
            self._fetch()
            leader = self.leader
        if leader is not None:
            return leader.predict(test_data)
        print("No model built yet...")

    #---------------------------------------------------------------------------
    # Download POJO/MOJO with AutoML
    #---------------------------------------------------------------------------

    def download_pojo(self, path="", get_genmodel_jar=False, genmodel_name=""):
        """
        Download the POJO for the leader model in AutoML to the directory specified by path.

        If path is an empty string, then dump the output to screen.

        :param path:  An absolute path to the directory where POJO should be saved.
        :param get_genmodel_jar: if True, then also download h2o-genmodel.jar and store it in folder ``path``.
        :param genmodel_name Custom name of genmodel jar
        :returns: name of the POJO file written.
        """

        return h2o.download_pojo(self.leader, path, get_jar=get_genmodel_jar, jar_name=genmodel_name)

    def download_mojo(self, path=".", get_genmodel_jar=False, genmodel_name=""):
        """
        Download the leader model in AutoML in MOJO format.

        :param path: the path where MOJO file should be saved.
        :param get_genmodel_jar: if True, then also download h2o-genmodel.jar and store it in folder ``path``.
        :param genmodel_name Custom name of genmodel jar
        :returns: name of the MOJO file written.
        """

        return ModelBase.download_mojo(self.leader, path, get_genmodel_jar, genmodel_name)

    #-------------------------------------------------------------------------------------------------------------------
    # Overrides
    #-------------------------------------------------------------------------------------------------------------------
    def detach(self):
        self.project_name = None
        h2o.remove(self.leaderboard)
        h2o.remove(self.event_log)

    #-------------------------------------------------------------------------------------------------------------------
    # Private
    #-------------------------------------------------------------------------------------------------------------------
    def _fetch(self):
        state = H2OAutoML._fetch_state(self.project_name)
        self._leader_id = state['leader_id']
        self._leaderboard = state['leaderboard']
        self._event_log = el = state['event_log']
        self._training_info = { r[0]: r[1]
                                for r in el[el['name'] != '', ['name', 'value']]
                                           .as_data_frame(use_pandas=False, header=False)
                              }
        self._state_json = state['json']
        return self._leader_id is not None

    def _poll_training_updates(self, job, bar_progress=0, verbosity=None, state=None):
        """
        the callback function used to print verbose info when polling AutoML job.
        """
        levels = ['Debug', 'Info', 'Warn']
        if verbosity is None or verbosity.capitalize() not in levels:
            return

        levels = levels[levels.index(verbosity.capitalize()):]
        try:
            if job.progress > state.get('last_job_progress', 0):
                # print("\nbar_progress={}, job_progress={}".format(bar_progress, job.progress))
                events = H2OAutoML._fetch_state(self.project_name, properties=['event_log'])['event_log']
                events = events[events['level'].isin(levels), :]
                last_nrows = state.get('last_events_nrows', 0)
                if events.nrows > last_nrows:
                    fr = events[last_nrows:, ['timestamp', 'message']].as_data_frame(use_pandas=False, header=False)
                    print('')
                    for r in fr:
                        print("{}: {}".format(r[0], r[1]))
                    print('')
                    state['last_events_nrows'] = events.nrows
            state['last_job_progress'] = job.progress
        except Exception as e:
            print("Failed polling AutoML progress log: {}".format(e))


    @staticmethod
    def _fetch_table(table, key=None, progress_bar=True):
        try:
            # Intentionally mask the progress bar here since showing multiple progress bars is confusing to users.
            # If any failure happens, revert back to user's original setting for progress and display the error message.
            ori_progress_state = H2OJob.__PROGRESS_BAR__
            H2OJob.__PROGRESS_BAR__ = progress_bar
            # Parse leaderboard H2OTwoDimTable & return as an H2OFrame
            return h2o.H2OFrame(table.cell_values, destination_frame=key, column_names=table.col_header, column_types=table.col_types)
        finally:
            H2OJob.__PROGRESS_BAR__ = ori_progress_state

    @staticmethod
    def _fetch_state(project_name, properties=None):
        state_json = h2o.api("GET /99/AutoML/%s" % project_name)
        project_name = state_json["project_name"]

        leaderboard_list = [key["name"] for key in state_json['leaderboard']['models']]
        leader_id = leaderboard_list[0] if (leaderboard_list is not None and len(leaderboard_list) > 0) else None

        should_fetch = lambda prop: properties is None or prop in properties

        leader = None
        if should_fetch('leader'):
            leader = h2o.get_model(leader_id) if leader_id is not None else None

        leaderboard = None
        if should_fetch('leaderboard'):
            leaderboard = H2OAutoML._fetch_table(state_json['leaderboard_table'], key=project_name+"_leaderboard", progress_bar=False)
            leaderboard = h2o.assign(leaderboard[1:], project_name+"_leaderboard")  # removing index and reassign id to ensure persistence on backend

        event_log = None
        if should_fetch('event_log'):
            event_log = H2OAutoML._fetch_table(state_json['event_log_table'], key=project_name+"_eventlog", progress_bar=False)
            event_log = h2o.assign(event_log[1:], project_name+"_eventlog")  # removing index and reassign id to ensure persistence on backend

        return dict(
            project_name=project_name,
            json=state_json,
            leader_id=leader_id,
            leader=leader,
            leaderboard=leaderboard,
            event_log=event_log,
        )


def get_automl(project_name):
    """
    Retrieve information about an AutoML instance.

    :param str project_name:  A string indicating the project_name of the automl instance to retrieve.
    :returns: A dictionary containing the project_name, leader model, leaderboard, event_log.
    """
    return H2OAutoML._fetch_state(project_name)
