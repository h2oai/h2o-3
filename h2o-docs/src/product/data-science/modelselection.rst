Model Selection
------------------------------------

Introduction
~~~~~~~~~~~~

The conventional maximum :math:`R^2` improvement technique does not guarantee to find the model with the largest :math:`R^2` for each predictor subset size. However, the H2O maximum :math:`R^2` improvement technique does guarantee to find the model with the largest :math:`R^2` for each predictor subset size. 

Defining a MAXR Model
~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the
   ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation. This value defaults to 0.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to -1 (time-based random number).

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the dependent variable.

   -  For a regression model only, this column must be numeric (**Real** or **Int**).

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for **nfolds** is specified and **fold_column** is not specified) Specify the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems). This option defaults to AUTO.

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Enable this option to ignore constant
   training columns, since no information can be gained from them. This
   option is enabled by default.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Enable this option to score during each iteration of the model training. This option is disabled by default.

- **score_iteration_interval**: Perform scoring for every ``score_iteration_interval`` iteration. Defaults to ``-1``.

-  `offset_column <algo-params/offset_column.html>`__: Specify a column to use as the offset; the value cannot be the same as the value for the ``weights_column``.
   
     **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. 

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `solver <algo-params/solver.html>`__: Specify the solver to use (AUTO, IRLSM, L_BFGS, COORDINATE_DESCENT_NAIVE, COORDINATE_DESCENT, GRADIENT_DESCENT_LH, or GRADIENT_DESCENT_SQERR). IRLSM is fast on problems with a small number of predictors and for lambda search with L1 penalty, while `L_BFGS <http://cran.r-project.org/web/packages/lbfgs/vignettes/Vignette.pdf>`__ scales better for datasets with many columns. COORDINATE_DESCENT is IRLSM with the covariance updates version of cyclical coordinate descent in the innermost loop. COORDINATE_DESCENT_NAIVE is IRLSM with the naive updates version of cyclical coordinate descent in the innermost loop. GRADIENT_DESCENT_LH and GRADIENT_DESCENT_SQERR can only be used with the Ordinal family. AUTO (default) will set the solver based on the given data and other parameters.

-  `alpha <algo-params/alpha.html>`__: Specify the regularization distribution between L1 and L2. The default value of alpha is 0 when SOLVER = 'L-BFGS'; otherwise it is 0.5.

-  `lambda <algo-params/lambda.html>`__: Specify the regularization strength.

-  `lambda_search <algo-params/lambda_search.html>`__: Specify whether to enable lambda search, starting with lambda max (the smallest :math:`\lambda` that drives all coefficients to zero). If you also specify a value for ``lambda_min_ratio``, then this value is interpreted as lambda min. If you do not specify a value for ``lambda_min_ratio``, then GLM will calculate the minimum lambda. This option is disabled by default.

-  `early_stopping <algo-params/early_stopping.html>`__: Specify whether to stop early when there is no more relative improvement on the training  or validation set. This option is enabled by default.

- `stopping_rounds <algo-params/stopping_rounds.html>`__: Stops training when the option selected for **stopping_metric** doesn't improve for the specified number of training rounds, based on a simple moving average. To disable this feature, specify ``0`` (default). 

    **Note:** If cross-validation is enabled:
  
    - All cross-validation models stop training when the validation metric doesn't improve.
    - The main model runs for the mean number of epochs.
    - N+1 models may be off by the number specified for **stopping_rounds** from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs).

- `stopping_metric <algo-params/stopping_metric.html>`__: Specify the metric to use for early stopping. The available options are:

  - ``AUTO``: This defaults to ``logloss`` for classification, ``deviance`` for regression, and ``anomaly_score`` for Isolation Forest. Note that ``custom`` and ``custom_increasing`` can only be used in GBM and DRF with the Python Client. Must be one of: ``AUTO``, ``anomaly_score``. Defaults to ``AUTO``.
  - ``anomaly_score`` (Isolation Forest only)
  - ``deviance``
  - ``logloss``
  - ``MSE``
  - ``RMSE``
  - ``MAE``
  - ``RMSLE``
  - ``AUC`` (area under the ROC curve)
  - ``AUCPR`` (area under the Precision-Recall curve)
  - ``lift_top_group``
  - ``misclassification``
  - ``mean_per_class_error``
  - ``custom`` (GBM/DRF Python client only)
  - ``custom_increasing`` (GBM/DRF Python client only)

- `stopping_tolerance <algo-params/stopping_tolerance.html>`__: Specify the relative tolerance for the metric-based stopping to stop training if the improvement is less than this value. Defaults to ``0.001``.

-  `nlambdas <algo-params/nlambdas.html>`__: (Applicable only if **lambda_search** is enabled) Specify the number of lambdas to use in the search. When ``alpha`` > 0, the default value for ``lambda_min_ratio`` is :math:`1e^{-4}`, then the default value for ``nlambdas`` is 100. This gives a ratio of 0.912. (For best results when using strong rules, keep the ratio close to this default.) When ``alpha=0``, the default value for ``nlamdas`` is set to 30 because fewer lambdas are needed for ridge regression. This value defaults to -1.

-  `standardize <algo-params/standardize.html>`__: Specify whether to standardize the numeric columns to have a mean of zero and unit variance. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option is enabled by default.

-  `missing_values_handling <algo-params/missing_values_handling.html>`__: Specify how to handle missing values (Skip, MeanImputation, or PlugValues). This value defaults to MeanImputation.

-  `plug_values <algo-params/plug_values.html>`__: When ``missing_values_handling="PlugValues"``, specify a single row frame containing values that will be used to impute missing values of the training/validation frame.

-  `compute_p_values <algo-params/compute_p_values.html>`__: Request computation of p-values. Only applicable with no penalty (lambda = 0 and no beta constraints). Setting remove_collinear_columns is recommended. H2O will return an error if p-values are requested and there are collinear columns and remove_collinear_columns flag is not enabled. Note that this option is not available for ``family="multinomial"`` or ``family="ordinal"``. This option is disabled by default.

-  `remove_collinear_columns <algo-params/remove_collinear_columns.html>`__: Specify whether to automatically remove collinear columns during model-building. When enabled, collinear columns will be dropped from the model and will have 0 coefficient in the returned model. This can only be set if there is no regularization (lambda=0). This option is disabled by default.

-  `intercept <algo-params/intercept.html>`__: Specify whether to include a constant term in the model. This option is enabled by default. 

-  `non_negative <algo-params/non_negative.html>`__: Specify whether to force coefficients to have non-negative values (defaults to false). 

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the number of training iterations (defaults to -1).

-  `objective_epsilon <algo-params/objective_epsilon.html>`__: If the objective value is less than this threshold, then the model is converged. If ``lambda_search=True``, then this value defaults to .0001. If ``lambda_search=False`` and lambda is equal to zero, then this value defaults to .000001. For any other value of lambda, the default value of objective_epsilon is set to .0001. The default value is -1.

-  `beta_epsilon <algo-params/beta_epsilon.html>`__: Converge if beta changes less than this value (using L-infinity norm). This only applies to IRLSM solver, and the value defaults to 0.0001.

-  `gradient_epsilon <algo-params/gradient_epsilon.html>`__: (For L-BFGS only) Specify a threshold for convergence. If the objective value (using the L-infinity norm) is less than this threshold, the model is converged. If ``lambda_search=True``, then this value defaults to .0001. If ``lambda_search=False`` and lambda is equal to zero, then this value defaults to .000001. For any other value of lambda, this value defaults to .0001. This value defaults to -1.

-  **startval**: The initial starting values for fixed and randomized coefficients in HGLM specified as a double array. 

-  `prior <algo-params/prior.html>`__: Specify prior probability for p(y==1). Use this parameter for logistic regression if the data has been sampled and the mean of response does not reflect reality. This value defaults to -1 and must be a value in the range (0,1).
   
     **Note**: This is a simple method affecting only the intercept. You may want to use weights and offset for a better fit.

- **cold_start**: Specify whether the model should be built from scratch. This parameter is only applicable when building a GLM model with multiple alpha/lambda values. If false and for a fixed alpha value, the next model with the next lambda value out of the lambda array will be built using the coefficients and the GLM state values of the current model. If true, the next GLM model will be built from scratch. The default value is false.

  **Note:** If an alpha array is specified and for a brand new alpha, the model will be built from scratch regardless of the value of ``cold_start``.

-  `lambda_min_ratio <algo-params/lambda_min_ratio.html>`__: Specify the minimum lambda to use for lambda search (specified as a ratio of **lambda_max**, which is the smallest :math:`\lambda` for which the solution is all zeros). This value defaults to -1.

-  `beta_constraints <algo-params/beta_constraints.html>`__: Specify a dataset to use beta constraints. The selected frame is used to constrain the coefficient vector to provide upper and lower bounds. The dataset must contain a names column with valid coefficient names.

-  `max_active_predictors <algo-params/max_active_predictors.html>`__: Specify the maximum number of active
   predictors during computation. This value is used as a stopping
   criterium to prevent expensive model building with many predictors. This value defaults to -1.

-  **obj_reg**: Specifies the likelihood divider in objective value computation. This defaults to 1/nobs.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model
   training.  This defaults to 0 (unlimited).

-  `custom_metric_func <algo-params/custom_metric_func.html>`__: Optionally specify a custom evaluation function.

- **nparallelism**: Number of models to be built in parallel. Defaults to 0.0 (which is adaptive to the system's capabilities).

- **max_predictor_number**: Maximum number of predictors to be considered when building GLM models. Defaults to 1.


H2O MaxRGLM
~~~~~~~~~~~

The H2O MaxRGLM algorithm is guaranteed to return the model with the best :math:`R^2` value for each predictor subset size. To guarantee we find the model with the best :math:`R^2` value for a particular number of predictors we will:

- generate all possible combinations of x predictors out of the n predictors (if there are n predictors and we are looking at using x predictors),
- generate the training frame for each element in the combination of x predictors,
- build the model and look at the :math:`R^2` value,
- chose the combination with the best :math:`R^2` value,
- store the predictor names and :math:`R^2` values in an array.

Examples
~~~~~~~~

.. tabs::
   .. code-tab:: r R

      library(h2o)
      h2o.init()

      # Import the prostate dataset:
      prostate <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv")
      |======================================================================| 100%

      # Set the predictors & response:
      predictors <- c("AGE", "RACE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS")
      response <- "GLEASON"

      # Build & train the model:
      maxrglmModel <- h2o.modelSelection(x = predictors, 
                                  y = response, 
                                  training_frame = prostate, 
                                  seed = 12345, 
                                  max_predictor_number = 7)
      |======================================================================| 100%

      # Retrieve the results (H2OFrame containing best model_ids, best_r2_value, & predictor subsets):
      results <- h2o.result(maxrglmModel)
      print(results)
      model_name                    model_id best_r2_value                   predictor_names
      1 best 1 predictor(s) model  GLM_model_1637788524625_26     0.2058868  1 CAPSULE
      2 best 2 predictor(s) model  GLM_model_1637788524625_37     0.2695678  2 CAPSULE, PSA
      3 best 3 predictor(s) model  GLM_model_1637788524625_66     0.2862530  3 CAPSULE, DCAPS, PSA
      4 best 4 predictor(s) model GLM_model_1637788524625_105     0.2904461  4 CAPSULE, DPROS, DCAPS, PSA
      5 best 5 predictor(s) model GLM_model_1637788524625_130     0.2921695  5 CAPSULE, AGE, DPROS, DCAPS, PSA
      6 best 6 predictor(s) model GLM_model_1637788524625_145     0.2924758  6 CAPSULE, AGE, RACE, DPROS, DCAPS, PSA
      7 best 7 predictor(s) model GLM_model_1637788524625_152     0.2925563  7 CAPSULE, AGE, RACE, DPROS, DCAPS, PSA, VOL

      # Retrieve the list of coefficients:
      coeff <- h2o.coef(maxrglmModel)
      print(coeff)
      [[1]]
      Intercept   CAPSULE
      5.978584  1.007438
      [[2]]
      Intercept    CAPSULE        PSA
      5.83309940 0.81073054 0.01458179
      [[3]]
      Intercept    CAPSULE      DCAPS        PSA
      5.34902149 0.75750144 0.47979555 0.01289096
      [[4]]
      Intercept    CAPSULE      DPROS      DCAPS        PSA
      5.23924958 0.71845861 0.07616614 0.44257893 0.01248512
      [[5]]
      Intercept    CAPSULE        AGE      DPROS      DCAPS        PSA
      4.78548229 0.72070240 0.00687360 0.07827698 0.43777710 0.01245014
      [[6]]
      Intercept      CAPSULE          AGE         RACE        DPROS        DCAPS          PSA
      4.853286962  0.717393309  0.006790891 -0.060686926  0.079288081  0.438470913  0.012572276
      [[7]]
      Intercept       CAPSULE           AGE          RACE         DPROS         DCAPS           PSA           VOL
      4.8526636043  0.7153633278  0.0069487980 -0.0584344031  0.0791810013  0.4353149856  0.0126060611  -0.0005196059

      # Retrieve the list of standardized coefficients:
      coeff_norm <- h2o.coef_norm(maxrglmModel)
      [[1]]
      Intercept   CAPSULE
      6.3842105 0.4947269
      [[2]]
      Intercept   CAPSULE       PSA
      6.3842105 0.3981290 0.2916004
      [[3]]
      Intercept   CAPSULE     DCAPS       PSA
      6.3842105 0.3719895 0.1490516 0.2577879
      [[4]]
      Intercept    CAPSULE      DPROS      DCAPS        PSA
      6.38421053 0.35281659 0.07617433 0.13749000 0.24967213
      [[5]]
      Intercept    CAPSULE        AGE      DPROS      DCAPS        PSA
      6.38421053 0.35391845 0.04486448 0.07828541 0.13599828 0.24897265
      [[6]]
      Intercept     CAPSULE         AGE        RACE       DPROS       DCAPS         PSA
      6.38421053  0.35229345  0.04432463 -0.01873850  0.07929661  0.13621382  0.25141500
      [[7]]
      Intercept      CAPSULE          AGE         RACE        DPROS        DCAPS          PSA          VOL
      6.384210526  0.351296573  0.045355300 -0.018042981  0.079189523  0.135233408  0.252090622 -0.009533532

   .. code-tab:: python

      import h2o
      from h2o.estimators import H2OMaxRGLMEstimator
      h2o.init()

      # Import the prostate dataset:
      prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv")
      Parse progress: =======================================  (done)| 100%

      # Set the predictors & response:
      predictors = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
      response = "GLEASON"

      # Build & train the model:
      maxrglmModel = H2OModelSelectionEstimator(max_predictor_number=7, seed=12345)
      maxrglmModel.train(x=predictors, y=response, training_frame=prostate)
      maxrglm Model Build progress: ======================================= (done)| 100%

      # Retrieve the results (H2OFrame containing best model_ids, best_r2_value, & predictor subsets):
      results = maxrglmModel.result()
      print(results)
      model_name                 model_id                       best_r2_value  predictor_names
      -------------------------  ---------------------------  ---------------  ------------------------------------------
      best 1 predictor(s) model  GLM_model_1638380984255_2           0.205887  CAPSULE
      best 2 predictor(s) model  GLM_model_1638380984255_13          0.269568  CAPSULE, PSA
      best 3 predictor(s) model  GLM_model_1638380984255_42          0.286253  CAPSULE, DCAPS, PSA
      best 4 predictor(s) model  GLM_model_1638380984255_81          0.290446  CAPSULE, DPROS, DCAPS, PSA
      best 5 predictor(s) model  GLM_model_1638380984255_106         0.29217   CAPSULE, AGE, DPROS, DCAPS, PSA
      best 6 predictor(s) model  GLM_model_1638380984255_121         0.292476  CAPSULE, AGE, RACE, DPROS, DCAPS, PSA
      best 7 predictor(s) model  GLM_model_1638380984255_128         0.292556  CAPSULE, AGE, RACE, DPROS, DCAPS, PSA, VOL

      [7 rows x 4 columns]

      # Retrieve the list of coefficients:
      coeff = maxrglmModel.coef()
      print(coeff)
      # [{‘Intercept’: 5.978584176203302, ‘CAPSULE’: 1.0074379937434323}, 
      # {‘Intercept’: 5.83309940166519, ‘CAPSULE’: 0.8107305373380133, ‘PSA’: 0.01458178860012023}, 
      # {‘Intercept’: 5.349021488372978, ‘CAPSULE’: 0.757501440465183, ‘DCAPS’: 0.47979554935185015, ‘PSA’: 0.012890961277678725}, 
      # {‘Intercept’: 5.239249580225221, ‘CAPSULE’: 0.7184586144005665, ‘DPROS’: 0.07616613714619831, ‘DCAPS’: 0.4425789341205361, ‘PSA’: 0.012485121785672872}, 
      # {‘Intercept’: 4.785482292681689, ‘CAPSULE’: 0.7207023955198935, ‘AGE’: 0.006873599969264931, ‘DPROS’: 0.07827698214607832, ‘DCAPS’: 0.4377770966619996, ‘PSA’: 0.012450143759298283}, 
      # {‘Intercept’: 4.853286962151182, ‘CAPSULE’: 0.7173933092205801, ‘AGE’: 0.00679089119920351, ‘RACE’: -0.06068692599374028, ‘DPROS’: 0.07928808123744804, ‘DCAPS’: 0.4384709133624667, ‘PSA’: 0.012572275831333262}, 
      # {‘Intercept’: 4.852663604264297, ‘CAPSULE’: 0.7153633277776693, ‘AGE’: 0.006948797960002643, ‘RACE’: -0.05843440305164041, ‘DPROS’: 0.07918100130777159, ‘DCAPS’: 0.43531498557623927, ‘PSA’: 0.012606061059188276, ‘VOL’: -0.0005196059470357373}]
      
      # Retrieve the list of standardized coefficients:
      coeff_norm = maxrglmModel.coef_norm()
      print(coeff_norm)
      # [{‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.49472694682382257}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.39812896270042736, ‘PSA’: 0.29160037716849074}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.37198951914000183, ‘DCAPS’: 0.1490515817762952, ‘PSA’: 0.25778793491797924}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.3528165891390707, ‘DPROS’: 0.07617433400499243, ‘DCAPS’: 0.13749000023165447, ‘PSA’: 0.24967213018482057}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.353918452469022, ‘AGE’: 0.04486447687517968, ‘DPROS’: 0.07828540617010687, ‘DCAPS’: 0.1359982784564225, ‘PSA’: 0.2489726545605919}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.352293445102015, ‘AGE’: 0.044324630838403115, ‘RACE’: -0.018738499858626197, ‘DPROS’: 0.07929661407409055, ‘DCAPS’: 0.1362138170890904, ‘PSA’: 0.2514149995462732}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.35129657330683034, ‘AGE’: 0.04535529952002336, ‘RACE’: -0.018042981011017332, ‘DPROS’: 0.07918952262067014, ‘DCAPS’: 0.13523340776861126, ‘PSA’: 0.25209062209542776, ‘VOL’: -0.009533532448945743}]


