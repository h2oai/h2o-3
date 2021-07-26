# -*- encoding: utf-8 -*-
import functools as ft
from inspect import getdoc
import re

import h2o
from h2o.automl._base import H2OAutoMLBaseMixin
from h2o.automl._h2o_automl_output import H2OAutoMLOutput
from h2o.base import Keyed
from h2o.estimators import H2OEstimator
from h2o.exceptions import H2OResponseError, H2OValueError
from h2o.frame import H2OFrame
from h2o.job import H2OJob
from h2o.utils.shared_utils import check_id
from h2o.utils.typechecks import assert_is_type, is_type, numeric


_params_doc_ = dict()  # holds the doc per param extracted from H2OAutoML constructor


def _extract_params_doc(docstr):
    pat = re.compile(r"^:param (\w+ )?(?P<name>\w+):\s?(?P<doc>.*)")  # match param doc-start in Sphinx format ":param type name: description"
    lines = docstr.splitlines()
    param, doc = None, None
    for l in lines:
        m = pat.match(l)
        if m:
            if param:
                _params_doc_[param] = "\n".join(doc)
            param = m.group('name')
            doc = [m.group('doc')]
        elif param:
            doc.append(l)


def _aml_property(param_path, name=None, types=None, validate_fn=None, freezable=False, set_input=True):
    path = param_path.split('.')
    name = name or path[-1]

    def attr_name(self, attr):
        return ("_"+self.__class__.__name__+attr) if attr.startswith('__') and not attr.endswith('__') else attr
    
    def _fget(self):
        _input = getattr(self, attr_name(self, '__input'))
        return _input.get(name)

    def _fset(self, value):
        if freezable and getattr(self, attr_name(self, '__frozen'), False):
            raise H2OValueError("Param ``%s`` can not be modified after the first call to ``train``." % name, name)
        if types is not None:
            assert_is_type(value, *types)
        input_val = value
        if validate_fn:
            value = validate_fn(self, value)
        _input = getattr(self, attr_name(self, '__input'))
        _input[name] = input_val if set_input else value
        group = getattr(self, attr_name(self, path[0]))
        if group is None:
            group = {}
            setattr(self, attr_name(self, path[0]), group)
        obj = group
        for t in path[1:-1]:
            tmp = obj.get(t)
            if tmp is None:
                tmp = obj[t] = {}
            obj = tmp
        obj[path[-1]] = value

    return property(fget=_fget, fset=_fset, doc=_params_doc_.get(name, None))


class H2OAutoML(H2OAutoMLBaseMixin, Keyed):
    """
    Automatic Machine Learning

    The Automatic Machine Learning (AutoML) function automates the supervised machine learning model training process.
    The current version of AutoML trains and cross-validates the following algorithms (in the following order):
    three pre-specified XGBoost GBM (Gradient Boosting Machine) models,
    a fixed grid of GLMs,
    a default Random Forest (DRF),
    five pre-specified H2O GBMs,
    a near-default Deep Neural Net,
    an Extremely Randomized Forest (XRT),
    a random grid of XGBoost GBMs,
    a random grid of H2O GBMs,
    and a random grid of Deep Neural Nets.
    In some cases, there will not be enough time to complete all the algorithms, so some may be missing from the
    leaderboard. AutoML then trains two Stacked Ensemble models, one of all the models, and one of only the best
    models of each kind.

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
                 max_runtime_secs=None,
                 max_runtime_secs_per_model=None,
                 max_models=None,
                 stopping_metric="AUTO",
                 stopping_tolerance=None,
                 stopping_rounds=3,
                 seed=None,
                 project_name=None,
                 exclude_algos=None,
                 include_algos=None,
                 exploitation_ratio=0,
                 modeling_plan=None,
                 preprocessing=None,
                 monotone_constraints=None,
                 keep_cross_validation_predictions=False,
                 keep_cross_validation_models=False,
                 keep_cross_validation_fold_assignment=False,
                 sort_metric="AUTO",
                 export_checkpoints_dir=None,
                 verbosity="warn",
                 **kwargs):
        """
        Create a new H2OAutoML instance.
        
        :param int nfolds: Number of folds for k-fold cross-validation.
            Use ``0`` to disable cross-validation; this will also disable Stacked Ensemble (thus decreasing the overall model performance).
            Defaults to ``5``.

        :param bool balance_classes: Balance training data class counts via over/under-sampling (for imbalanced data).
            Defaults to ``False``.
        :param class_sampling_factors: Desired over/under-sampling ratios per class (in lexicographic order).
            If not specified, sampling factors will be automatically computed to obtain class balance during training.
        :param float max_after_balance_size: Maximum relative size of the training data after balancing class counts (can be less than 1.0).
            Requires ``balance_classes``.
            Defaults to ``5.0``.
        :param int max_runtime_secs: Specify the maximum time that the AutoML process will run for.
            If neither ``max_runtime_secs`` nor ``max_models`` are specified by the user, then ``max_runtime_secs``.
            Defaults to 3600 seconds (1 hour).
        :param int max_runtime_secs_per_model: Controls the max time the AutoML run will dedicate to each individual model.
            Defaults to ``0`` (disabled: no time limit).
        :param int max_models: Specify the maximum number of models to build in an AutoML run, excluding the Stacked Ensemble models.
            Defaults to ``0`` (disabled: no limitation).
        :param str stopping_metric: Specifies the metric to use for early stopping. 
            The available options are:
            ``"AUTO"`` (This defaults to ``"logloss"`` for classification, ``"deviance"`` for regression),
            ``"deviance"``, ``"logloss"``, ``"mse"``, ``"rmse"``, ``"mae"``, ``"rmsle"``, ``"auc"``, ``aucpr``, ``"lift_top_group"``,
            ``"misclassification"``, ``"mean_per_class_error"``, ``"r2"``.
            Defaults to ``"AUTO"``.
        :param float stopping_tolerance: Specify the relative tolerance for the metric-based stopping to stop the AutoML run if the improvement is less than this value. 
            Defaults to ``0.001`` if the dataset is at least 1 million rows;
            otherwise it defaults to a value determined by the size of the dataset and the non-NA-rate, in which case the value is computed as 1/sqrt(nrows * non-NA-rate).
        :param int stopping_rounds: Stop training new models in the AutoML run when the option selected for
            stopping_metric doesn't improve for the specified number of models, based on a simple moving average.
            To disable this feature, set it to ``0``.
            Defaults to ``3`` and must be an non-negative integer.
        :param int seed: Set a seed for reproducibility. 
            AutoML can only guarantee reproducibility if ``max_models`` or early stopping is used because ``max_runtime_secs`` is resource limited, 
            meaning that if the resources are not the same between runs, AutoML may be able to train more models on one run vs another. 
            Defaults to ``None``.
        :param str project_name: Character string to identify an AutoML project.
            Defaults to ``None``, which means a project name will be auto-generated based on the training frame ID.
            More models can be trained on an existing AutoML project by specifying the same project name in multiple calls to the AutoML function
            (as long as the same training frame, or a sample, is used in subsequent runs).
        :param exclude_algos: List the algorithms to skip during the model-building phase. 
            The full list of options is: 
                ``"DRF"`` (Random Forest and Extremely-Randomized Trees),
                ``"GLM"``,
                ``"XGBoost"``,
                ``"GBM"``,
                ``"DeepLearning"``,
                ``"StackedEnsemble"``. 
            Defaults to ``None``, which means that all appropriate H2O algorithms will be used, if the search stopping criteria allow. Optional.
            Usage example: ``exclude_algos = ["GLM", "DeepLearning", "DRF"]``.
        :param include_algos: List the algorithms to restrict to during the model-building phase.
            This can't be used in combination with `exclude_algos` param.
            Defaults to ``None``, which means that all appropriate H2O algorithms will be used, if the search stopping criteria allow. Optional.
        :param exploitation_ratio: The budget ratio (between 0 and 1) dedicated to the exploitation (vs exploration) phase.
            By default, the exploitation phase is disabled (exploitation_ratio=0) as this is still experimental;
            to activate it, it is recommended to try a ratio around 0.1.
            Note that the current exploitation phase only tries to fine-tune the best XGBoost and the best GBM found during exploration.
        :param modeling_plan: List of modeling steps to be used by the AutoML engine (they may not all get executed, depending on other constraints).
            Defaults to None (Expert usage only).
        :param preprocessing: List of preprocessing steps to run. Only 'target_encoding' is currently supported.
        :param monotone_constraints: Dict representing monotonic constraints.
            Use +1 to enforce an increasing constraint and -1 to specify a decreasing constraint.
        :param keep_cross_validation_predictions: Whether to keep the predictions of the cross-validation predictions.
            This needs to be set to ``True`` if running the same AutoML object for repeated runs because CV predictions are required to build 
            additional Stacked Ensemble models in AutoML. 
            Defaults to ``False``.
        :param keep_cross_validation_models: Whether to keep the cross-validated models.
            Keeping cross-validation models may consume significantly more memory in the H2O cluster.
            Defaults to ``False``.
        :param keep_cross_validation_fold_assignment: Whether to keep fold assignments in the models.
            Deleting them will save memory in the H2O cluster. 
            Defaults to ``False``.
        :param sort_metric: Metric to sort the leaderboard by. 
            For binomial classification choose between ``"auc"``, ``"aucpr"``, ``"logloss"``, ``"mean_per_class_error"``, ``"rmse"``, ``"mse"``.
            For multinomial classification choose between ``"mean_per_class_error"``, ``"logloss"``, ``"rmse"``, ``"mse"``.
            For regression choose between ``"deviance"``, ``"rmse"``, ``"mse"``, ``"mae"``, ``"rmlse"``.
            Defaults to ``"AUTO"`` (This translates to ``"auc"`` for binomial classification, ``"mean_per_class_error"`` for multinomial classification, ``"deviance"`` for regression).
        :param export_checkpoints_dir: Path to a directory where every model will be stored in binary form.
        :param verbosity: Verbosity of the backend messages printed during training.
            Available options are None (live log disabled), ``"debug"``, ``"info"`` or ``"warn"``.
            Defaults to ``"warn"``.
        """
        
        # early validate kwargs, extracting hidden parameters:
        algo_parameters = {}
        for k in kwargs:
            if k == 'algo_parameters':
                algo_parameters = kwargs[k] or {}
            else:
                raise TypeError("H2OAutoML got an unexpected keyword argument '%s'" % k)
        
        # Check if H2O jar contains AutoML
        try:
            h2o.api("GET /3/Metadata/schemas/AutoMLV99")
        except h2o.exceptions.H2OResponseError as e:
            print(e)
            print("*******************************************************************\n" \
                  "*Please verify that your H2O jar has the proper AutoML extensions.*\n" \
                  "*******************************************************************\n" \
                  "\nVerbose Error Message:")

        self._job = None
        self._leader_id = None
        self._leaderboard = None
        self._verbosity = verbosity
        self._event_log = None
        self._training_info = None
        self._state_json = None
        self._build_resp = None  # contains all the actual parameters used on backend

        self.__frozen = False
        self.__input = dict()  # contains all the input params as entered by the user
        
        # Make bare minimum params containers
        self.build_control = dict()
        self.build_models = dict()
        self.input_spec = dict()

        self.project_name = project_name
        self.nfolds = nfolds
        self.balance_classes = balance_classes
        self.class_sampling_factors = class_sampling_factors
        self.max_after_balance_size = max_after_balance_size
        self.keep_cross_validation_models = keep_cross_validation_models
        self.keep_cross_validation_fold_assignment = keep_cross_validation_fold_assignment
        self.keep_cross_validation_predictions = keep_cross_validation_predictions
        self.export_checkpoints_dir = export_checkpoints_dir

        self.max_runtime_secs = max_runtime_secs
        self.max_runtime_secs_per_model = max_runtime_secs_per_model
        self.max_models = max_models
        self.stopping_metric = stopping_metric
        self.stopping_tolerance = stopping_tolerance
        self.stopping_rounds = stopping_rounds
        self.seed = seed
        self.exclude_algos = exclude_algos
        self.include_algos = include_algos
        self.exploitation_ratio = exploitation_ratio
        self.modeling_plan = modeling_plan
        self.preprocessing = preprocessing
        if monotone_constraints is not None:
            algo_parameters['monotone_constraints'] = monotone_constraints
        self._algo_parameters = algo_parameters

        self.sort_metric = sort_metric
        
    #---------------------------------------------------------------------------
    # AutoML params
    #---------------------------------------------------------------------------
    
    def __validate_not_set(self, val, prop=None, message=None):
        assert val is None or getattr(self, prop, None) is None, message
        return val
    
    def __validate_project_name(self, project_name):
        check_id(project_name, "H2OAutoML")
        return project_name
    
    def __validate_nfolds(self, nfolds):
        assert nfolds == 0 or nfolds > 1, "nfolds set to %s; use nfolds >=2 if you want cross-validated metrics and Stacked Ensembles or use nfolds = 0 to disable." % nfolds
        return nfolds
        
    def __validate_modeling_plan(self, modeling_plan):
        if modeling_plan is None:
            return None
            
        supported_aliases = ['all', 'defaults', 'grids']

        def assert_is_step_def(sd):
            assert 'name' in sd, "each definition must have a 'name' key"
            assert 0 < len(sd) < 3, "each definition must have only 1 or 2 keys: name, name+alias or name+steps"
            assert len(sd) == 1 or 'alias' in sd or 'steps' in sd, "steps definitions support only the following keys: name, alias, steps"
            assert 'alias' not in sd or sd['alias'] in supported_aliases, "alias must be one of %s" % supported_aliases
            assert 'steps' not in sd or (is_type(sd['steps'], list) and all(assert_is_step(s) for s in sd['steps']))

        def assert_is_step(s):
            assert is_type(s, dict), "each step must be a dict with an 'id' key and an optional 'weight' key"
            assert 'id' in s, "each step must have an 'id' key"
            assert len(s) == 1 or ('weight' in s and is_type(s['weight'], int)), "weight must be an integer"
            return True

        plan = []
        for step_def in modeling_plan:
            assert_is_type(step_def, dict, tuple, str)
            if is_type(step_def, dict):
                assert_is_step_def(step_def)
                plan.append(step_def)
            elif is_type(step_def, str):
                plan.append(dict(name=step_def))
            else:
                assert 0 < len(step_def) < 3
                assert_is_type(step_def[0], str)
                name = step_def[0]
                if len(step_def) == 1:
                    plan.append(dict(name=name))
                else:
                    assert_is_type(step_def[1], str, list)
                    ids = step_def[1]
                    if is_type(ids, str):
                        assert_is_type(ids, *supported_aliases)
                        plan.append(dict(name=name, alias=ids))
                    else:
                        plan.append(dict(name=name, steps=[dict(id=i) for i in ids]))
        return plan
    
    def __validate_preprocessing(self, preprocessing):
        if preprocessing is None:
            return 
        assert all(p in ['target_encoding'] for p in preprocessing)
        return [dict(type=p.replace("_", "")) for p in preprocessing]
    
    def __validate_monotone_constraints(self, monotone_constraints):
        if monotone_constraints is None:
            self._algo_parameters.pop('monotone_constraints', None)
        else:
            self._algo_parameters['monotone_constraints'] = monotone_constraints
        return self.__validate_algo_parameters(self._algo_parameters)
    
    def __validate_algo_parameters(self, algo_parameters):
        if algo_parameters is None:
            return None
        algo_parameters_json = []
        for k, v in algo_parameters.items():
            scope, __, name = k.partition('__')
            if len(name) == 0:
                name, scope = scope, 'any'
            value = [dict(key=k, value=v) for k, v in v.items()] if isinstance(v, dict) else v   # we can't use stringify_dict here as this will be converted into a JSON string
            algo_parameters_json.append(dict(scope=scope, name=name, value=value))
        return algo_parameters_json

    def __validate_frame(self, fr, name=None, required=False):
        return H2OFrame._validate(fr, name, required=required)

    _extract_params_doc(getdoc(__init__))
    project_name = _aml_property('build_control.project_name', types=(None, str), freezable=True,
                                  validate_fn=__validate_project_name)
    nfolds = _aml_property('build_control.nfolds', types=(int,), freezable=True,
                           validate_fn=__validate_nfolds)
    balance_classes = _aml_property('build_control.balance_classes', types=(bool,), freezable=True)
    class_sampling_factors = _aml_property('build_control.class_sampling_factors', types=(None, [numeric]), freezable=True)
    max_after_balance_size = _aml_property('build_control.max_after_balance_size', types=(None, numeric), freezable=True)
    keep_cross_validation_models = _aml_property('build_control.keep_cross_validation_models', types=(bool,), freezable=True)
    keep_cross_validation_fold_assignment = _aml_property('build_control.keep_cross_validation_fold_assignment', types=(bool,), freezable=True)
    keep_cross_validation_predictions = _aml_property('build_control.keep_cross_validation_predictions', types=(bool,), freezable=True)
    export_checkpoints_dir = _aml_property('build_control.export_checkpoints_dir', types=(None, str), freezable=True)
    max_runtime_secs = _aml_property('build_control.stopping_criteria.max_runtime_secs', types=(None, int), freezable=True)
    max_runtime_secs_per_model = _aml_property('build_control.stopping_criteria.max_runtime_secs_per_model', types=(None, int), freezable=True)
    max_models = _aml_property('build_control.stopping_criteria.max_models', types=(None, int), freezable=True)
    stopping_metric = _aml_property('build_control.stopping_criteria.stopping_metric', types=(None, str), freezable=True)
    stopping_tolerance = _aml_property('build_control.stopping_criteria.stopping_tolerance', types=(None, numeric), freezable=True)
    stopping_rounds = _aml_property('build_control.stopping_criteria.stopping_rounds', types=(None, int), freezable=True)
    seed = _aml_property('build_control.stopping_criteria.seed', types=(None, int), freezable=True)
    exclude_algos = _aml_property('build_models.exclude_algos', types=(None, [str]), freezable=True,
                                  validate_fn=ft.partial(__validate_not_set, prop='include_algos',
                                                         message="Use either `exclude_algos` or `include_algos`, not both."))
    include_algos = _aml_property('build_models.include_algos', types=(None, [str]), freezable=True,
                                  validate_fn=ft.partial(__validate_not_set, prop='exclude_algos',
                                                         message="Use either `exclude_algos` or `include_algos`, not both."))
    exploitation_ratio = _aml_property('build_models.exploitation_ratio', types=(None, numeric), freezable=True)
    modeling_plan = _aml_property('build_models.modeling_plan', types=(None, list), freezable=True,
                                  validate_fn=__validate_modeling_plan)
    preprocessing = _aml_property('build_models.preprocessing', types=(None, [str]), freezable=True,
                                  validate_fn=__validate_preprocessing)
    monotone_constraints = _aml_property('build_models.algo_parameters', name='monotone_constraints', types=(None, dict), freezable=True, 
                                         validate_fn=__validate_monotone_constraints)
    _algo_parameters = _aml_property('build_models.algo_parameters', types=(None, dict), freezable=True,
                                     validate_fn=__validate_algo_parameters)

    sort_metric = _aml_property('input_spec.sort_metric', types=(None, str))
    fold_column = _aml_property('input_spec.fold_column', types=(None, int, str))
    weights_column = _aml_property('input_spec.weights_column', types=(None, int, str))

    training_frame = _aml_property('input_spec.training_frame', set_input=False,
                                   validate_fn=ft.partial(__validate_frame, name='training_frame', required=True))
    validation_frame = _aml_property('input_spec.validation_frame', set_input=False,
                                     validate_fn=ft.partial(__validate_frame, name='validation_frame'))
    leaderboard_frame = _aml_property('input_spec.leaderboard_frame', set_input=False,
                                      validate_fn=ft.partial(__validate_frame, name='leaderboard_frame'))
    blending_frame = _aml_property('input_spec.blending_frame', set_input=False,
                                   validate_fn=ft.partial(__validate_frame, name='blending_frame'))
    response_column = _aml_property('input_spec.response_column', types=(str,))

    #---------------------------------------------------------------------------
    # Basic properties
    #---------------------------------------------------------------------------
    
    @property
    def key(self):
        return self._job.dest_key if self._job else self.project_name

    @property
    def leader(self):
        return None if self._leader_id is None else h2o.get_model(self._leader_id)

    @property
    def leaderboard(self):
        return H2OFrame([]) if self._leaderboard is None else self._leaderboard

    @property
    def event_log(self):
        return H2OFrame([]) if self._event_log is None else self._event_log

    @property
    def training_info(self):
        return dict() if self._training_info is None else self._training_info

    @property
    def modeling_steps(self):
        """
        expose the modeling steps effectively used by the AutoML run.
        This executed plan can be directly reinjected as the `modeling_plan` property of a new AutoML instance
         to improve reproducibility across AutoML versions.

        :return: a list of dictionaries representing the effective modeling plan.
        """
        # removing alias key to be able to reinject result to a new AutoML instance
        return list(map(lambda sdef: dict(name=sdef['name'], steps=sdef['steps']), self._state_json['modeling_steps']))

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
        # Minimal required arguments are training_frame and y (response)
        self.training_frame = training_frame

        ncols = self.training_frame.ncols
        names = self.training_frame.names

        if y is None and self.response_column is None:
            raise H2OValueError('The response column (y) is not set; please set it to the name of the column that you are trying to predict in your data.')
        elif y is not None:
            assert_is_type(y, int, str)
            if is_type(y, int):
                if not (-ncols <= y < ncols):
                    raise H2OValueError("Column %d does not exist in the training frame" % y)
                y = names[y]
            else:
                if y not in names:
                    raise H2OValueError("Column %s does not exist in the training frame" % y)
            self.response_column = y

        self.fold_column = fold_column
        self.weights_column = weights_column

        self.validation_frame = validation_frame
        self.leaderboard_frame = leaderboard_frame
        self.blending_frame = blending_frame

        if x is not None:
            assert_is_type(x, list)
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
            ignored_columns = set(names) - xset
            for col in [y, fold_column, weights_column]:
                if col is not None and col in ignored_columns:
                    ignored_columns.remove(col)
            if ignored_columns is not None:
                self.input_spec['ignored_columns'] = list(ignored_columns)

        def clean_params(params):
            return ({k: clean_params(v) for k, v in params.items() if v is not None} if isinstance(params, dict) 
                    else H2OEstimator._keyify(params))

        automl_build_params = clean_params(dict(
            build_control=self.build_control,
            build_models=self.build_models,
            input_spec=self.input_spec,
        ))

        resp = self._build_resp = h2o.api('POST /99/AutoMLBuilder', json=automl_build_params)
        if 'job' not in resp:
            raise H2OResponseError("Backend failed to build the AutoML job: {}".format(resp))

        if not self.project_name:
            self.project_name = resp['build_control']['project_name']
        self.__frozen = True
        
        self._job = H2OJob(resp['job'], "AutoML")
        poll_updates = ft.partial(self._poll_training_updates, verbosity=self._verbosity, state={})
        try:
            self._job.poll(poll_updates=poll_updates)
        finally:
            poll_updates(self._job, 1)

        self._fetch()
        return self.leader

    #---------------------------------------------------------------------------
    # Predict with AutoML
    #---------------------------------------------------------------------------
    def predict(self, test_data):
        leader = self.leader
        if leader is None:
            self._fetch()
            leader = self.leader
        if leader is not None:
            return leader.predict(test_data)
        print("No model built yet...")

    #-------------------------------------------------------------------------------------------------------------------
    # Overrides
    #-------------------------------------------------------------------------------------------------------------------
    def detach(self):
        self.__frozen = False
        self.project_name = None
        h2o.remove(self.leaderboard)
        h2o.remove(self.event_log)

    #-------------------------------------------------------------------------------------------------------------------
    # Private
    #-------------------------------------------------------------------------------------------------------------------

    def _fetch(self):
        state = H2OAutoML._fetch_state(self.key)
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
                events = H2OAutoML._fetch_state(job.dest_key, properties=['event_log'])['event_log']
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
    def _fetch_leaderboard(aml_id, extensions=None):
        assert_is_type(extensions, None, str, [str])
        extensions = ([] if extensions is None
                      else [extensions] if is_type(extensions, str)
                      else extensions)
        resp = h2o.api("GET /99/Leaderboards/%s" % aml_id, data=dict(extensions=extensions))
        dest_key = resp['project_name'].split('@', 1)[0]+"_custom_leaderboard"
        lb = H2OAutoML._fetch_table(resp['table'], key=dest_key, progress_bar=False)
        return h2o.assign(lb[1:], dest_key)


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
    def _fetch_state(aml_id, properties=None):
        state_json = h2o.api("GET /99/AutoML/%s" % aml_id)
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
    state = H2OAutoML._fetch_state(project_name)
    return H2OAutoMLOutput(state)


def get_leaderboard(aml, extra_columns=None):
    """
    Retrieve the leaderboard from the AutoML instance.
    Contrary to the default leaderboard attached to the automl instance, this one can return columns other than the metrics.
    :param H2OAutoML aml: the instance for which to return the leaderboard.
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
    >>> lb_all = h2o.automl.get_leaderboard(aml, 'ALL')
    >>> lb_custom = h2o.automl.get_leaderboard(aml, ['predict_time_per_row_ms', 'training_time_ms'])
    >>> lb_custom_sorted = lb_custom.sort(by='predict_time_per_row_ms')
    """
    assert_is_type(aml, H2OAutoML, H2OAutoMLOutput)
    return H2OAutoML._fetch_leaderboard(aml.key, extra_columns)
