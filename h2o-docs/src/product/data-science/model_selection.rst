ModelSelection
--------------

Introduction
~~~~~~~~~~~~

We implemented the ModelSelection toolbox based on GLM at H2O to help users select the best predictor subsets from their dataset for model building. We have currently implemented four modes to select the predictor subsets:

1. ``mode = "allsubsets"`` where all possible combinations of predictor subsets are generated for a given subset size. A model is built for each subset and the one with the highest :math:`R^2` is returned. The best subsets are also returned for subset size :math:`1, 2, ..., n`. This mode guarantees to return the predictor subset with the highest :math:`R^2` value at the cost of computation complexity.
2. ``mode = "maxr"`` where a sequential replacement method is used to find the best subsets for subset size of :math:`1, 2, ..., n`. However, the predictor subsets are not guaranteed to have the highest :math:`R^2`` value.
3. ``mode = "backward"`` where a model is built starting with all predictors. The predictor with the smallest absolute z-value (or z-score) is dropped after each model is built. This process repeats until only one predictor remains or until the number of predictors equal to ``min_predictor_number`` is reached. The model build can also be stopped using ``p_values_threshold``. 
4. ``mode = "maxrsweep"`` where the model runs similar to ``mode = "maxr"`` except that instead of calling our GLM toolbox to build models, we use the sweep operator [:ref:`3<ref4>`] plus our own incremental sweep operation using sweep vectors. This change speeds up the execution of finding the best predictor subset for each subset size and is essential in dropping the build time of the model. 

The fastest mode for ModelSelection is ``mode = "maxrsweep"`` with ``build_glm_model = False``. This skips the GLM model process while still generating the predictor subsets, the coefficients, and the coefficient values.

This model only supports GLM regression families. 

MOJO Support
''''''''''''

ModelSelection currently does not support `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Defining a ModelSelection Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Parameters are optional unless specified as *required*. ModelSelection shares many `GLM parameters <glm.html#shared-glm-family-parameters>`__.

Algorithm-specific parameters
'''''''''''''''''''''''''''''

- **build_glm_model**: (Applicable for ``mode = "maxrsweep"`` only) If enabled, this option will return full GLM models with the desired predictor subsets. If disabled, only the predictor subsets and predictor coefficients are returned. Disabling this parameter speeds up the model selection process. You can also choose to build the GLM models themselves by using the returned predictor subsets. This option defaults to ``True`` (enabled).

- **max_predictor_number**: Maximum number of predictors to be considered when building GLM models. This option defaults to ``1``.

- **min_predictor_number**: (Applicable for ``mode = "backward"`` only) Minimum number of predictors to be considered when building GLM models starting with all predictors to be included. This option defaults to ``1``.

- **mode**: *Required* Specify the model selection algorithm to use. One of:
   
   - ``"maxr"`` (default)
   - ``"allsubsets"``
   - ``"backward"``
   - ``"maxrsweep"``

- **nparallelism**: Number of models to be built in parallel. This option defaults to ``0.0`` (which is adaptive to the system's capabilities).

- **p_values_threshold**: (Applicable for ``mode = "backward"`` only) If specified, this option will stop the model building process when all coefficient p-values drop to or below this threshold. This option defaults to ``0.0``.

- **score_iteration_interval**: Perform scoring for every ``score_iteration_interval`` iteration. This option defaults to ``-1``.

Common parameters
'''''''''''''''''

-  `custom_metric_func <algo-params/custom_metric_func.html>`__: Optionally specify a custom evaluation function.

-  `early_stopping <algo-params/early_stopping.html>`__: Specify whether to stop early when there is no more relative improvement on the training  or validation set. This option is set to ``True`` (enabled) by default.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for ``nfolds`` is specified and ``fold_column`` is not specified) Specify the cross-validation fold assignment scheme. One of:

    - ``AUTO`` (default; uses ``Random``)
    - ``Random``
    - ``Modulo`` (`read more about Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__)
    - ``Stratified`` (which will stratify the folds based on the response variable for classification problems)

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Enable this option to ignore constant training columns since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the number of training iterations. This option defaults to ``-1``.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. This option defaults to ``0`` (unlimited).

-  `missing_values_handling <algo-params/missing_values_handling.html>`__: Specify how to handle missing values (one of: ``Skip``, ``MeanImputation`` (default), or ``PlugValues``). 

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation. The value can be set to ``0`` (default) to disable or to :math:`\geq` ``2``. 

-  `offset_column <algo-params/offset_column.html>`__: Specify a column to use as the offset; the value cannot be the same as the value for the ``weights_column``. This wll be added to the combination of columns before applying the link function.
   
     **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (``y``) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. 

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: Enable this option to score during each iteration of the model training. This option defaults to ``False`` (disabled).

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to ``-1`` (time-based random number).

-  `standardize <algo-params/standardize.html>`__: Specify whether to standardize the numeric columns to have a mean of zero and unit variance. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option defaults to ``True`` (enabled).

- `stopping_metric <algo-params/stopping_metric.html>`__: Specify the metric to use for early stopping. The available options are:

  - ``AUTO`` (default): (This defaults to ``logloss`` for classification and ``deviance`` for regression)
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

- `stopping_rounds <algo-params/stopping_rounds.html>`__: Stops training when the option selected for ``stopping_metric`` doesn't improve for the specified number of training rounds, based on a simple moving average. To disable this feature, specify ``0`` (default). 

    **Note:** If cross-validation is enabled:
  
    - All cross-validation models stop training when the validation metric doesn't improve.
    - The main model runs for the mean number of epochs.
    - N+1 models may be off by the number specified for ``stopping_rounds`` from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs).

- `stopping_tolerance <algo-params/stopping_tolerance.html>`__: Specify the relative tolerance for the metric-based stopping to stop training if the improvement is less than this value. This option defaults to ``0.001``.

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
   
    **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset used to evaluate the accuracy of the model.

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. 
    
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable.

    -  For a regression model only, this column must be numeric (**Real** or **Int**).

Understanding ModelSelection ``mode = allsubsets``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Setting the H2O ModelSelection ``mode = allsubsets`` guarantees the return of the model with the best :math:`R^2` value. 

For each predictor subset size :math:`x`:

- For :math:`n` predictors and using :math:`x` predictors, first generate all possible combinations of :math:`x` predictors out of the :math:`n` predictors;
- for each element in the combination of :math:`x` predictors: generate the training frame, build the model, and look at the :math:`R^2` value of the model;
- the best :math:`R^2` value, the predictor names, and the ``model_id`` of the best models are stored in arrays as well as H2OFrame;
- access functions are written in Java/R/Python to extract coefficients associated with the models with the best :math:`R^2` values.

The main disadvantage of this mode is the long computation time.

Understanding ModelSelection ``mode = maxr``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The H2O ModelSelection ``mode = maxr`` is implemented using the sequential replacement method [:ref:`1<ref4>`]. This consists of a forward step and a replacement step. The sequential replacement method goes like this (where the predictors are denoted by *A, B, C, ..., Z*):

1. Start with the current *subset = {}* (empty)
2. Forward step for 1 predictor subset:

	a. add each available predictor (from *A* to *Z*) to the current empty subset and build a GLM model with each predictor subset;
	b. save the model with the highest :math:`R^2` for all models built with predictor *A, B, ..., Z*;
	c. set the new current *subset = {predictor with highest* :math:`R^2` *}* (for example, predictor *A*).

3. Forward step for 2 predictor subset (starting with current *subset = {A}* ):

	a. add each available predictor (from B to Z) to the current subset and build a GLM model;
	b. save the model with the highest :math:`R^2` for all models with predictor subsets *AB, AC, ..., AZ*;
	c. set the new current *subset = {model with highest* :math:`R^2` *}* and save the best subset (for example, *{AB}*).

4. Replacement for 2 predictor subset from best subset chosen from forward step for 2 predictor subsets (i.e. starting from best *subset {AB}* from previous step):

  a. fixing the second predictor, choose a different predictor for the first predictor from the remaining predictors *C, D, ..., Z* (skipping predictor *A* as it was chosen already by forward step; *B* is taken as the second predictor). Then, build a GLM model for each new subset of (*CB, DB, EB, ..., ZB*). Save the model with the highest :math:`R^2` (for example, {*DB*}) from all models built with predictor subsets (*CB, DB, EB, ..., ZB*);
  b. fixing the first predictor, choose a different second predictor from the remaining predictor subset. Then, build a GLM model for each new subset generated. Save the model with the highest :math:`R^2` from all models built;
  c. compare the :math:`R^2` value from the models built with forward step, step 4(a), and step 4(b) and choose the subset with the highest :math:`R^2`. If the best model is built with {*AB*}, proceed to step 5 because steps 4(a) and 4(b) generated no improvement. If the best model is built with {*DB*}, repeat steps 4(a), 4(b), and 4(c) until no improvement is found. For the two predictor case, the first 4(b) can be skipped since it is already done in the forward step.  

5. Start with the best :math:`n` predictor subset and forward step for :math:`n` predictor subsets:

  a. add each predictor available to the :math:`n` predictor subset and build a GLM model;
  b. save the model with the highest :math:`R^2` for all models built with :math:`n+1` predictor subsets;

6. Replacement for :math:`n+1` predictor subsets:
  
  a. Repeat for predictor in location *0,1,2,...,n*:

    - keep all predictors fixed except in location *k* (*k* will be from *0,1,2,...,n*) and switch out the predictor at location *k* with one predictor from the available predictors. If there are *m* predictors in the available predictor subset, *m* GLM models will be built and the model with the best :math:`R^2` value will be saved;

  b. from all the *n* best models found from step 6(a), if the best :math:`R^2` value has improved from the forward step or the previous 6(a), return to 6(a). If no improvement is found, break and just take the best :math:`R^2` model as the one to save.

Again, the best :math:`R^2` value, the predictor names, and the ``model_id`` of the best models are stored in arrays as well as H2OFrame. Additionally, coefficients associated with the models built with all the predictor subset sizes are available and accessible as well.

Understanding ModelSelection ``mode = backward``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. A model with all predictors is built;
2. the z-values of all coefficients (except ``intercept``) are considered. The coefficient with the smallest z-value magnitude is eliminated; 
3. a new model is built with the remaining predictors;
4. steps 2 and 3 are repeated until 

    a. no predictors are left, 
    b. ``min_predictor_number - 1`` predictors are left, or 
    c. ``p_values_threshold`` condition is satisfied.

To increase flexibility in the model building process, you can stop the model building process by specifying a ``p_values_threshold``. When the ``p_values`` of all predictors (except ``intercept``) are :math:`\leq` ``p_values_threshold``, the model building process will stop as well.

Interpreting a ModelSelection Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Result Frame
''''''''''''

To help you understand your model, a result frame is generated at the end of the building process. For ``maxr`` and ``allsubsets`` modes, the result frame will contain:

- **model_name**: string describing how many predictors are used to build the model
- **model_id**: model ID of the GLM model built. You can use this model ID to obtain the original GLM model and perform scoring or anything else you want to do with an H2O model
- **best_r2_value**: the highest :math:`R^2` value from the predictor subsets of a fixed size
- **predictor_names**: names of the predictors used to build the model

For ``backward`` mode, the result frame will contain:

- **model_name**: string describing how many predictors are used to build the model
- **model_id**: model ID of the GLM model built. You can use this model ID to obtain the original GLM model and perform scoring or anything else you want to do with an H2O model
- **z_values**: z-values of all coefficients of the GLM model
- **p_values**: p-values of all coefficients of the GLM model
- **coefficient_names**: coefficients (including ``intercept``) of the GLM model

Model Coefficients
''''''''''''''''''

The coefficients of each model built for each predictor size are available. You can see how to access the coefficients in the `Examples <#examples>`__ section.

Cross-Validation
''''''''''''''''

ModelSelection supports cross-validation and the use of the validation dataset for ``mode = "maxr"`` and ``mode = "allsubsets"``. Only ``family = gaussian`` is supported.

For ``mode = "backward"``, cross-validation is not supported as the model selection process depends on training z-values and p-values. All GLM families are supported except for ``ordinal`` and ``multinomial``. 

Model Scoring
'''''''''''''

The model IDs of all models built for each predictor subset size are stored in the result frame. These IDs can be used to obtain the original models. They can be used for scoring just like any returned H2O models.


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
      allsubsetsModel <- h2o.modelSelection(x = predictors, 
                                            y = response, 
                                            training_frame = prostate, 
                                            seed = 12345, 
                                            max_predictor_number = 7, 
                                            mode = "allsubsets")
      |======================================================================| 100%

      # Retrieve the results (H2OFrame containing best model_ids, best_r2_value, & predictor subsets):
      results <- h2o.result(allsubsetsModel)
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
      coeff <- h2o.coef(allsubsetsModel)
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

      # Retrieve the list of coefficients for a subset size of 3:
      coeff3 <- h2o.coef(allsubsetsModel, 3)
      print(coeff3)
      [[3]]
      Intercept    CAPSULE      DCAPS        PSA
      5.34902149 0.75750144 0.47979555 0.01289096

      # Retrieve the list of standardized coefficients:
      coeff_norm <- h2o.coef_norm(allsubsetsModel)
      print(coeff_norm)
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

      # Retrieve the list of standardized coefficients for a subset size of 3:
      coeff_norm3 <- h2o.coef_norm(allsubsetsModel)
      print(coeff_norm3)
      [[3]]
      Intercept   CAPSULE     DCAPS       PSA
      6.3842105 0.3719895 0.1490516 0.2577879

      # Check the variables that were added during this process:
      h2o.get_predictors_added_per_step(allsubsetsModel)
           [,1]     
      [1,] "CAPSULE"
      [2,] "PSA"    
      [3,] "DCAPS"  
      [4,] "DPROS"  
      [5,] "AGE"    
      [6,] "RACE"   
      [7,] "VOL"

      # To find out which variables get removed, build a new model with ``mode = "backward``
      # using the above training information:
      bwModel <- h2o.modelSelection(x = predictors,
                                   y = response, 
                                   training_frame = prostate, 
                                   seed = 12345, 
                                   max_predictor_number = 7, 
                                   mode = "backward")
      h2o.get_predictors_removed_per_step(bwModel)
           [,1]
      [1,] "CAPSULE"  
      [2,] "PSA"  
      [3,] "DCAPS"  
      [4,] "DPROS"  
      [5,] "AGE"  
      [6,] "RACE"  
      [7,] "VOL" 

      # To build the fastest model with ModelSelection, use ``mode = "maxrsweep"``:
      sweepModel <- h2o.modelSelection(x = predictors, 
                                       y = response, 
                                       training_frame = prostate, 
                                       mode = "maxrsweep", 
                                       build_glm_model = FALSE, 
                                       max_predictor_number = 3, 
                                       seed = 12345)
      |======================================================================| 100%

      # Retrieve the results to view the best predictor subsets:
      h2o.result(sweepModel)
        model_name                  best_r2_value          coefficient_names     predictor_names predictors_removed predictors_added
      1 best 1 predictors model     0.2058873             CAPSULE, Intercept             CAPSULE                             CAPSULE
      2 best 2 predictors model     0.2695684        CAPSULE, PSA, Intercept        CAPSULE, PSA                                 PSA
      3 best 3 predictors model     0.2862536 CAPSULE, PSA, DCAPS, Intercept CAPSULE, PSA, DCAPS                               DCAPS

   .. code-tab:: python

      import h2o
      from h2o.estimators import H2OModelSelectionEstimator
      h2o.init()

      # Import the prostate dataset:
      prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv")
      Parse progress: =======================================  (done)| 100%

      # Set the predictors & response:
      predictors = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
      response = "GLEASON"

      # Build & train the model:
      maxrModel = H2OModelSelectionEstimator(max_predictor_number=7, 
                                             seed=12345, 
                                             mode="maxr")
      maxrModel.train(x=predictors, y=response, training_frame=prostate)
      maxr Model Build progress: ======================================= (done)| 100%

      # Retrieve the results (H2OFrame containing best model_ids, best_r2_value, & predictor subsets):
      results = maxrModel.result()
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
      coeff = maxrModel.coef()
      print(coeff)
      # [{‘Intercept’: 5.978584176203302, ‘CAPSULE’: 1.0074379937434323}, 
      # {‘Intercept’: 5.83309940166519, ‘CAPSULE’: 0.8107305373380133, ‘PSA’: 0.01458178860012023}, 
      # {‘Intercept’: 5.349021488372978, ‘CAPSULE’: 0.757501440465183, ‘DCAPS’: 0.47979554935185015, ‘PSA’: 0.012890961277678725}, 
      # {‘Intercept’: 5.239249580225221, ‘CAPSULE’: 0.7184586144005665, ‘DPROS’: 0.07616613714619831, ‘DCAPS’: 0.4425789341205361, ‘PSA’: 0.012485121785672872}, 
      # {‘Intercept’: 4.785482292681689, ‘CAPSULE’: 0.7207023955198935, ‘AGE’: 0.006873599969264931, ‘DPROS’: 0.07827698214607832, ‘DCAPS’: 0.4377770966619996, ‘PSA’: 0.012450143759298283}, 
      # {‘Intercept’: 4.853286962151182, ‘CAPSULE’: 0.7173933092205801, ‘AGE’: 0.00679089119920351, ‘RACE’: -0.06068692599374028, ‘DPROS’: 0.07928808123744804, ‘DCAPS’: 0.4384709133624667, ‘PSA’: 0.012572275831333262}, 
      # {‘Intercept’: 4.852663604264297, ‘CAPSULE’: 0.7153633277776693, ‘AGE’: 0.006948797960002643, ‘RACE’: -0.05843440305164041, ‘DPROS’: 0.07918100130777159, ‘DCAPS’: 0.43531498557623927, ‘PSA’: 0.012606061059188276, ‘VOL’: -0.0005196059470357373}]

      # Retrieve the list of coefficients for a subset size of 3:
      coeff3 = maxrModel.coef(3)
      print(coeff3)
      # {'Intercept': 5.349021488372978, 'CAPSULE': 0.757501440465183, 'DCAPS': 0.47979554935185015, 'PSA': 0.012890961277678725}
      
      # Retrieve the list of standardized coefficients:
      coeff_norm = maxrModel.coef_norm()
      print(coeff_norm)
      # [{‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.49472694682382257}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.39812896270042736, ‘PSA’: 0.29160037716849074}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.37198951914000183, ‘DCAPS’: 0.1490515817762952, ‘PSA’: 0.25778793491797924}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.3528165891390707, ‘DPROS’: 0.07617433400499243, ‘DCAPS’: 0.13749000023165447, ‘PSA’: 0.24967213018482057}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.353918452469022, ‘AGE’: 0.04486447687517968, ‘DPROS’: 0.07828540617010687, ‘DCAPS’: 0.1359982784564225, ‘PSA’: 0.2489726545605919}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.352293445102015, ‘AGE’: 0.044324630838403115, ‘RACE’: -0.018738499858626197, ‘DPROS’: 0.07929661407409055, ‘DCAPS’: 0.1362138170890904, ‘PSA’: 0.2514149995462732}, 
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.35129657330683034, ‘AGE’: 0.04535529952002336, ‘RACE’: -0.018042981011017332, ‘DPROS’: 0.07918952262067014, ‘DCAPS’: 0.13523340776861126, ‘PSA’: 0.25209062209542776, ‘VOL’: -0.009533532448945743}]

      # Retrieve the list of standardized coefficients for a subset size of 3:
      coeff_norm3 = maxrModel.coef_norm(3)
      print(coeff_norm3)
      # {‘Intercept’: 6.38421052631579, ‘CAPSULE’: 0.37198951914000183, ‘DCAPS’: 0.1490515817762952, ‘PSA’: 0.25778793491797924}

      # Check the variables that were added during this process:
      maxrModel.get_predictors_added_per_step()
      [['CAPSULE'], ['PSA'], ['DCAPS'], ['DPROS'], ['AGE'], ['RACE'], ['VOL']]

      # Using the above training information, build a model using ``mode = "backward"``:
      bwModel = H2OModelSelectionEstimator(max_predictor_number=3, 
                                           seed=12345, 
                                           mode="backward")
      bwModel.train(x=predictors, y=response, training_frame=prostate)
      ModelSelection Model Summary: summary
                        coefficient_names               z_values                                                                     p_values
      ----------------- ------------------------------- ---------------------------------------------------------------------------  ----------------------------------------------------------------------------------------
      with 1 predictors CAPSULE, Intercept              9.899643676508614, 92.43746760936982                                         1.070331637158796E-20, 1.3321139829486397E-261
      with 2 predictors CAPSULE, PSA, Intercept         7.825700947986458, 5.733056921838707, 86.91622746127426                      5.144662722557474E-14, 2.023486352710146E-8, 1.7241718600984578E-251
      with 3 predictors CAPSULE, DCAPS, PSA, Intercept  7.275417885570092, 2.964750742738588, 4.992785143892783, 30.274880599946904  2.0273323955515335E-12, 0.0032224082063575395, 9.124834372427609E-7, 7.417923313036E-103

      # Check the variables that were removed during this process:
      bwModel.get_predictors_removed_per_step()
      [['CAPSULE'], ['PSA'], ['DCAPS'], ['DPROS'], ['AGE'], ['RACE'], ['VOL']]

      # To build the fastest model with ModelSelection, use ``mode="maxrsweep"``:
      sweepModel = H2OModelSelectionEstimator(mode="maxrsweep", 
                                              build_glm_model=False, 
                                              max_predictor_number=3, 
                                              seed=12345)
      sweepModel.train(x=predictors, y=response, training_frame=prostate)
      modelselection Model Build progress: ======================================= (done)| 100%

      # Retrieve the results to view the best predictor subsets:
      print(sweepModel.results())
      model_name                 best_r2_value  coefficient_names               predictor_names      predictors_removed    predictors_added
      best 1 predictors model         0.205887  CAPSULE, Intercept              CAPSULE                                    CAPSULE
      best 2 predictors model         0.269568  CAPSULE, PSA, Intercept         CAPSULE, PSA                               PSA
      best 3 predictors model         0.286254  CAPSULE, PSA, DCAPS, Intercept  CAPSULE, PSA, DCAPS                        DCAPS
      [3 rows x 6 columns]


References
~~~~~~~~~~

.. _ref4:

1. Alan Miller, Subset Selection in Regression, section 3.5, Second Edition, 2002 Chapman & Hall/CRC.

2. Trevor Hastie, Robert Tibshirani, Jerome Friedman, The Elements of Statistical Learning, Section 3.3.2, Second Edition, Springer, 2008.

3. M. Schatzoff, R. Tsao, S. Fierberg, “Efficient Calculation of All Possible Regressions”, TECHNOMETRICS, Vol. 10, No. 4, NOVEMBER 1968.
