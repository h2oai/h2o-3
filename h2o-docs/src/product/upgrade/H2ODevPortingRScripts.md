#Porting R Scripts from H2O to H2O-Dev

This document outlines how to port R scripts written in H2O for compatibility with the new H2O-Dev API. When upgrading from H2O to H2O-Dev, most functions are the same. However, there are some differences that will need to be resolved when porting any scripts that were originally created using H2O to H2O-Dev. 

The original R script for H2O is listed first, followed by the updated script for H2O-Dev. 

Some of the parameters have been renamed for consistency. For each algorithm, a table that describes the differences is provided. 

For additional assistance within R, enter a question mark before the command (for example, `?h2o.glm`). 

**Table of Contents**

- [GBM](#GBM)
- [GLM](#GLM)
- [K-Means](#Kmeans)
- [Deep Learning](#DL)
- [Distributed Random Forest](#DRF)


##Changes from H2O to H2O-Dev

###`h2o.exec`
The `h2o.exec` command is no longer supported. Any workflows using `h2o.exec` must be revised to remove this command.  If the H2O-Dev workflow contains any parameters or commands from H2O, errors will result and the workflow will fail. 

The purpose of `h2o.exec` was to wrap expressions so that they could be evaluated in a single `\Exec2` call. For example, 
 `h2o.exec(fr[,1] + 2/fr[,3])`
 and 
 `fr[,1] + 2/fr[,3]`
produced the same results in H2O. However, the first example makes a single REST call and uses a single temp object, while the second makes several REST calls and uses several temp objects. 

Due to the improved architecture in H2O-Dev, the need to use `h2o.exec` has been eliminated, as the expression can be processed by R as an "unwrapped" typical R expression. 

Currently, the only known exception is when `factor` is used in conjunction with `h2o.exec`. For example, `h2o.exec(fr$myIntCol <- factor(fr$myIntCol))` would become `fr$myIntCol <- as.factor(fr$myIntCol)`

<a name="h2operf"></a>
###`h2o.performance`

To access any exclusively binomial output, use `h2o.performance`, optionally with the corresponding accessor. The accessor can only use the model metrics object created by `h2o.performance`. Each accessor is named for its corresponding field (for example, `h2o.AUC`, `h2o.gini`, `h2o.F1`). `h2o.performance` supports all current algorithms except for K-Means. 

If you specify a data frame as a second parameter, H2O will use the specified data frame for scoring. If you do not specify a second parameter, the training metrics for the model metrics object are used. 

###`xval` and `validation` slots

The `xval` slot has been removed, as `nfolds` is not currently supported. 

The `validation` slot has been merged with the `model` slot. 

###Principal Components Regression (PCR)

Principal Components Regression (PCR) has also been deprecated. To obtain PCR values, create a Principal Components Analysis (PCA) model, then create a GLM model from the scored data from the PCA model. 


<a name="GBM"></a>
##GBM

N-fold cross-validation and grid search will be supported in a future version of H2O-Dev. 

###Renamed GBM Parameters

The following parameters have been renamed, but retain the same functions: 

H2O Parameter Name | H2O-Dev Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `destination_key`
`distribution` | `loss`
`n.trees` | `ntrees`
`interaction.depth` | `max_depth`
`n.minobsinnode` | `min_rows`
`shrinkage` | `learn_rate`
`n.bins` | `nbins`
`validation` | `validation_frame`
`balance.classes` | `balance_classes`
`max.after.balance.size` | `max_after_balance_size`
`class.sampling.factors` | `class_sampling_factors`

###Deprecated GBM Parameters

The following parameters have been removed: 

- `group_split`: Bit-set group splitting of categorical variables is now the default. 
- `importance`: Variable importances are now computed automatically and displayed in the model output. 
- `holdout.fraction`: The fraction of the training data to hold out for validation is no longer supported. 
- `grid.parallelism`: Specifying the number of parallel threads to run during a grid search is no longer supported. Grid search will be supported in a future version of H2O-Dev. 

###New GBM Parameters

The following parameters have been added: 

- `seed`: A random number to control sampling and initialization when `balance_classes` is enabled. 
- `score_each_iteration`: Display error rate information after each tree in the requested set is built. 

###GBM Algorithm Comparison

H2O  | H2O-Dev
------------- | -------------
`h2o.gbm <- function(` | `h2o.gbm <- function(` 
`x,` |`x,`
`y,` |`y,` 
`data,` | `training_frame,`
`key = "",` | `destination_key,` 
`distribution = 'multinomial',` | `loss = c("bernoulli", "multinomial", "gaussian"),` 
`n.trees = 10,` | `ntrees = 50`
`interaction.depth = 5,` | `max_depth = 5,` 
`n.minobsinnode = 10,` | `min_rows = 10,` 
`shrinkage = 0.1,` | `learn_rate = 0.1,` 
`n.bins = 20,`| `nbins = 20,` 
`validation,` | `validation_frame = NULL,` 
`balance.classes = FALSE` | `balance_classes = FALSE,` 
`max.after.balance.size = 5,` | `max_after_balance_size = 1,` 
 &nbsp; | `seed,` 
 &nbsp; | `score_each_iteration)`
`group_split = TRUE,` | 
`importance = FALSE,` | 
`nfolds = 0,` | 
`holdout.fraction = 0,` | 
`class.sampling.factors = NULL,` | 
`grid.parallelism = 1)` | 


###Output

The following table provides the component name in H2O, the corresponding component name in H2O-Dev (if supported), and the model type (binomial, multinomial, or all). Many components are now included in `h2o.performance`; for more information, refer to [(`h2o.performance`)](#h2operf).

H2O  | H2O-Dev  | Model Type
------------- | ------------- | -------------
`@model$priorDistribution`| &nbsp;  | `all`
`@model$params` | `@allparameters` | `all`
`@model$err` | `@model$scoring_history` | `all`
`@model$classification` | &nbsp;  | `all`
`@model$varimp` | `@model$variable_importances` | `all`
`@model$confusion` | `@model$training_metrics$cm$table`  | `binomial` and `multinomial`
`@model$auc` | `@model$training_metrics$AUC`  | `binomial`
`@model$gini` | `@model$training_metrics$Gini`  | `binomial`
`@model$best_cutoff` | &nbsp;  | `binomial`
`@model$F1` | `@model$training_metrics$thresholds_and_metric_scores$f1`  | `binomial`
`@model$F2` | `@model$training_metrics$thresholds_and_metric_scores$f2`  | `binomial`
`@model$accuracy` | `@model$training_metrics$thresholds_and_metric_scores$accuracy`  | `binomial`
`@model$error` | &nbsp;  | `binomial`
`@model$precision` | `@model$training_metrics$thresholds_and_metric_scores$precision`  | `binomial`
`@model$recall` | `@model$training_metrics$thresholds_and_metric_scores$recall`  | `binomial`
`@model$mcc` | `@model$training_metrics$thresholds_and_metric_scores$absolute_MCC`  | `binomial`
`@model$max_per_class_err` | currently replaced by `@model$training_metrics$thresholds_and_metric_scores$min_per_class_correct`  | `binomial`





---

<a name="GLM"></a>
##GLM

 N-fold cross-validation and grid search will be supported in a future version of H2O-Dev. 

###Renamed GLM Parameters

The following parameters have been renamed, but retain the same functions:

H2O Parameter Name | H2O-Dev Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `destination_key`
`nlambda` | `nlambdas`
`lambda.min.ratio` | `lambda_min_ratio`
 `iter.max` | `max_iterations`
 `epsilon` | `beta_epsilon`

###Deprecated GLM Parameters
 
The following parameters have been removed: 
 
 - `return_all_lambda`: A logical value indicating whether to return every model built during the lambda search. (may be re-added)
 - `higher_accuracy`: For improved accuracy, adjust the `beta_epsilon` value. 
 - `strong_rules`: Discards predictors likely to have 0 coefficients prior to model building. (may be re-added as enabled by default)
 - `intercept`: Defines factor columns in the model. (may be re-added)
 - `non_negative`: Specify a non-negative response. (may be re-added)
 - `variable_importances`: Variable importances are now computed automatically and displayed in the model output. They have been renamed to *Normalized Coefficient Magnitudes*. 
 - `disable_line_search`: This parameter has been deprecated, as it was mainly used for testing purposes. 
 - `offset`: Specify a column as an offset. (may be re-added)
 - `max_predictors`: Stops training the algorithm if the number of predictors exceeds the specified value. (may be re-added)
 - `n_folds`: Number of folds for cross-validation (will be re-added)

###New GLM Parameters
 
 The following parameters have been added: 
 
 - `class_sampling_factors`: Specify an array containing real numbers to define how much each class should be over- or under-sampled.
 - `validation_frame`: Specify the validation dataset. 
 - `max_after_balance_size`: If classes are balanced, limit the resulting dataset size to the specified multiple of the original dataset size.
 - `solver`: Select ADMM or LBFGS. 

###GLM Algorithm Comparison


H2O  | H2O-Dev
------------- | -------------
`h2o.glm <- function(` | `h2o.startGLMJob <- function(`
`x,` | `x,`
`y,` | `y,` 
`data,` |`training_frame,` 
`key = "",` | `destination_key,` 
 &nbsp; | `validation_frame`
`iter.max = 100,` |  `max_iterations = 50,` 
`epsilon = 1e-4` | `beta_epsilon = 0` 
`strong_rules = TRUE,` | 
`return_all_lambda = FALSE,` | 
&nbsp; | `class_sampling_factors,`
`intercept = TRUE,` | 
&nbsp; | `max_after_balance_size = 5,`
`non_negative = FALSE,` | 
&nbsp; | `solver = c("ADMM", "L_BFGS"),`
`standardize = TRUE,` | `standardize = TRUE,` 
`family,` | `family = c("gaussian", "binomial", "poisson", "gamma"),` 
`link,` | `link = c("family_default", "identity", "logit", "log", "inverse"),`
`tweedie.p = ifelse(family == "tweedie",1.5, NA_real_)` |  
`alpha = 0.5,` | `alpha = 0.5,` 
`prior = NULL` | `prior = 0.0,` 
`lambda = 1e-5,` | `lambda = 1e-05,` 
`lambda_search = FALSE,` | `lambda_search = FALSE,` 
`nlambda = -1,` | `nlambdas = -1,` 
`lambda.min.ratio = -1,` | `lambda_min_ratio = 1.0,` 
`use_all_factor_levels = FALSE` | `use_all_factor_levels = FALSE,` 
`nfolds = 0,` |  
`beta_constraints = NULL,` | `beta_constraint = NULL)` 
`higher_accuracy = FALSE,` |  
`variable_importances = FALSE,` | 
`disable_line_search = FALSE,` | 
`offset = NULL,` | 
`max_predictors = -1)` |


###Output


The following table provides the component name in H2O, the corresponding component name in H2O-Dev (if supported), and the model type (binomial, multinomial, or all). Many components are now included in `h2o.performance`; for more information, refer to [(`h2o.performance`)](#h2operf).

H2O  | H2O-Dev  | Model Type
------------- | ------------- | -------------
`@model$params` | `@allparameters` | `all`
`@model$coefficients` | `@model$coefficients` | `all`
`@model$nomalized_coefficients` | `@model$coefficients_table$norm_coefficients` | `all`
`@model$rank` | `@model$rank` | `all`
`@model$iter` |`@model$iter` | `all`
`@model$lambda` | &nbsp;  | `all`
`@model$deviance` | `@model$residual_deviance` | `all`
`@model$null.deviance` | `@model$null_deviance` | `all`
`@model$df.residual` | `@model$residual_degrees_of_freedom` | `all`
`@model$df.null` | `@model$null_degrees_of_freedom` | `all`
`@model$aic` | `@model$AIC`| `all`
`@model$train.err` |  &nbsp; | `binomial`
`@model$prior` | &nbsp;  | `binomial`
`@model$thresholds` | `@model$threshold` | `binomial`
`@model$best_threshold` | &nbsp;  | `binomial`
`@model$auc` | `@model$AUC` | `binomial`
`@model$confusion` | &nbsp;  | `binomial`

<a name="Kmeans"></a>
##K-Means

###Renamed K-Means Parameters

The following parameters have been renamed, but retain the same functions: 

H2O Parameter Name | H2O-Dev Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `destination_key`
`centers` | `k`
`cols` | `x`
`iter.max` | `max_iterations`
`normalize` | `standardize`

**Note** In H2O, the `normalize` parameter was disabled by default.The `standardize` parameter is enabled by default in H2O-Dev to provide more accurate results for datasets containing columns with large values. 

###Deprecated K-Means Parameters

The following parameters have been removed: 

- `dropNACols`:   Drop columns with more than 20% missing values. (may be re-added)

###New K-Means Parameters

The following parameters have been added:

- `user` has been added as an additional option for the `init` parameter. Using this parameter forces the K-Means algorithm to start at the user-specified points. 
- `user_points`: Specify starting points for the K-Means algorithm. 

###K-Means Algorithm Comparison

H2O  | H2O-Dev
------------- | -------------
`h2o.kmeans <- function(` | `h2o.kmeans <- function(`
`data,` | `training_frame,` 
`cols = '',` | `x,`
`centers,` | `k,`
`key = "",` | `destination_key,`
`iter.max = 10,` | `max_iterations = 1000,`
`normalize = FALSE,`  | `standardize = TRUE,`
`init = "none",` | `init = c("Furthest","Random", "PlusPlus"),`
`seed = 0,` | `seed)`
`dropNACols = FALSE)` |

###Output


The following table provides the component name in H2O and the corresponding component name in H2O-Dev (if supported).

H2O  | H2O-Dev
------------- | -------------
`@model$params` | `@allparameters`
`@model$centers` | `@model$centers`
`@model$withinss` | `@model$within_mse`
`@model$tot.withinss` | `@model$avg_within_ss`
`@model$size` | `@model$size`
`@model$iter` | `@model$iterations`
&nbsp; | `@model$_scoring_history`
&nbsp; | `@model$_model_summary`

---

<a name="DL"></a>
##Deep Learning

N-fold cross-validation and grid search will be supported in a future version of H2O-Dev. 

###Renamed Deep Learning Parameters

The following parameters have been renamed, but retain the same functions: 

H2O Parameter Name | H2O-Dev Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `destination_key`
`validation` | `validation_frame`
`class.sampling.factors` | `class_sampling_factors`


###Deprecated DL Parameters

The following parameters have been removed:

- `classification`: Classification is now inferred from the data type.
- `holdout_fraction`: Fraction of the training data to hold out for validation.
- `n_folds`:Number of folds for cross-validation (will be re-added).

###New DL Parameters

The following parameters have been added: 

- `export_weights_and_biases`: An additional option allowing users to export the raw weights and biases as H2O frames. 

The following options for the `loss` parameter have been added:

- `absolute`: Provides strong penalties for mispredictions 
- `huber`: Can improve results for regression 

###DL Algorithm Comparison

H2O  | H2O-Dev
------------- | -------------
`h2o.deeplearning <- function(x,` | `h2o.deeplearning <- function(x, `
`y,` | `y,`
`data,` | `training_frame,` 
`key = "",` | `destination_key = "",`
`override_with_best_model,` | `_override_with_best_model = true,` 
`classification = TRUE,` | 
`nfolds = 0,` |  
`validation,` | `validation_frame,` 
`holdout_fraction = 0,` |  
`checkpoint = " "` | `_checkpoint,` 
`autoencoder,` | `_autoencoder = false,` 
`use_all_factor_levels,` | `_use_all_factor_levels = true`
`activation,` | `_activation = c("Rectifier", "Tanh", "TanhWithDropout", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"),`
`hidden,` | `_hidden= c(200, 200),`
`epochs,` | `_epochs = 10.0,`
`train_samples_per_iteration,` |`_train_samples_per_iteration = -2,`
&nbsp; | `_target_ratio_comm_to_comp = 0.02,`
`seed,` | `_seed,` 
`adaptive_rate,` | `_adaptive_rate = true,` 
`rho,` | `_rho = 0.99,` 
`epsilon,` | `_epsilon = 1e-8,` 
`rate,` | `_rate = .005,` 
`rate_annealing,` | `_rate_annealing = 1e-6,` 
`rate_decay,` | `_rate_decay = 1.0,` 
`momentum_start,` | `_momentum_start = 0,`
`momentum_ramp,` | `_momentum_ramp = 1e6,`
`momentum_stable,` | `_momentum_stable = 0,` 
`nesterov_accelerated_gradient,` | `_nesterov_accelerated_gradient = true,`
`input_dropout_ratio,` | `_input_dropout_ratio = 0.0,` 
`hidden_dropout_ratios,` | `_hidden_dropout_ratios,` 
`l1,` | `_l1 = 0.0,` 
`l2,` | `_l2 = 0.0,` 
`max_w2,` | `_max_w2 = Inf,`
`initial_weight_distribution,` | `_initial_weight_distribution = c("UniformAdaptive","Uniform", "Normal"),`
`initial_weight_scale,` | `_initial_weight_scale = 1.0,`
`loss,` | `_loss = "Automatic", "CrossEntropy", "MeanSquare", "Absolute", "Huber"),`
`score_interval,` | `_score_interval = 5,` 
`score_training_samples,` | `_score_training_samples = 10000l,` 
`score_validation_samples,` | `_score_validation_samples = 0l,`
`score_duty_cycle,` | `_score_duty_cycle = 0.1,` 
`classification_stop,` | `_classification_stop = 0`
`regression_stop,` | `_regression_stop = 1e-6,`
`quiet_mode,` | `_quiet_mode = false,`
`max_confusion_matrix_size,` | &nbsp;
`max_hit_ratio_k,` | `_max_hit_ratio_k,`
`balance_classes,` | `_balance_classes = false,`
`class_sampling_factors,` | `_class_sampling_factors,`
`max_after_balance_size,` | &nbsp; 
`score_validation_sampling,` | `_score_validation_sampling,`
`diagnostics,` | `_diagnostics = true,` 
`variable_importances,` | `_variable_importances = false,`
`fast_mode,` | `_fast_mode = true,` 
`ignore_const_cols,` | `_ignore_const_cols = true,`
`force_load_balance,` | `_force_load_balance = true,`
`replicate_training_data,` | `_replicate_training_data = true,`
`single_node_mode,` | `_single_node_mode = false,` 
`shuffle_training_data,` | `_shuffle_training_data = false,`
 &nbsp; | `_missing_values_handling = MissingValuesHandling.MeanImputation`
`sparse,` | `_sparse = false,` 
`col_major,` | `_col_major = false,`
`max_categorical_features,` | `_max_categorical_features = Integer.MAX_VALUE,`
`reproducible)` | `_reproducible = false,` 
`average_activation` | `_average_activation = 0,`
 &nbsp; | `_sparsity_beta = 0`
 &nbsp; | `_export_weights_and_biases = false)`

###Output


The following table provides the component name in H2O, the corresponding component name in H2O-Dev (if supported), and the model type (binomial, multinomial, or all). Many components are now included in `h2o.performance`; for more information, refer to [(`h2o.performance`)](#h2operf).

H2O  | H2O-Dev  | Model Type
------------- | ------------- | ------------- 
`@model$priorDistribution`| &nbsp;  | `all`
`@model$params` | `@allparameters` | `all`
`@model$train_class_error` | `@model$training_metrics$MSE`  | `all`
`@model$valid_class_error` | `@model$validation_metrics$MSE` | `all`
`@model$varimp` | `@model$_variable_importances` | `all`
`@model$confusion` | `@model$training_metrics$cm$table`  | `binomial` and `multinomial`
`@model$train_auc` | `@model$train_AUC`  | `binomial`
&nbsp; | `@model$_validation_metrics` | `all`
&nbsp; | `@model$_model_summary` | `all`
&nbsp; | `@model$_scoring_history` | `all`

 
 ---

<a name="DRF"></a>
##Distributed Random Forest

###Changes to DRF in H2O-Dev

Distributed Random Forest (DRF) was represented as `h2o.randomForest(type="BigData", ...)` in H2O. In H2O, SpeeDRF (`type="fast"`) was not as accurate, especially for complex data with categoricals, and did not address regression problems. DRF (`type="BigData"`) was at least as accurate as SpeeDRT (`type="fast"`) and was the only algorithm that scaled to big data (data too large to fit on a single node). 
In H2O-Dev, our plan is to improve the performance of DRF so that the data fits on a single node (optimally, for all cases), which will make SpeeDRF obsolete. Ultimately, the goal is provide a single algorithm that provides the "best of both worlds" for all datasets and use cases. 

**Note**: H2O-Dev only supports DRF. SpeeDRF is no longer supported. The functionality of DRF in H2O-Dev is similar to DRF functionality in H2O. 

###Renamed DRF Parameters

The following parameters have been renamed, but retain the same functions: 

H2O Parameter Name | H2O-Dev Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `destination_key`
`validation` | `validation_frame`
`sample.rate` | `sample_rate`
`ntree` | `ntrees` 
`depth` | `max_depth`
`balance.classes` | `balance_classes`
`score.each.iteration` | `score_each_iteration`
`class.sampling.factors` | `class_sampling_factors`
`nodesize` | `min_rows`


###Deprecated DRF Parameters

The following parameters have been removed: 

- `classification`: This is now automatically inferred from the response type. To achieve classification with a 0/1 response column, explicitly convert the response to a factor (`as.factor()`). 
- `importance`: Variable importances are now computed automatically and displayed in the model output. 
- `holdout.fraction`: Specifying the fraction of the training data to hold out for validation is no longer supported. 
- `doGrpSplit`: The bit-set group splitting of categorical variables is now the default. 
- `verbose`: Infonrmation about tree splits and extra statistics is now included automatically in the stdout. 
- `oobee`: The out-of-bag error estimate is now computed automatically (if no validation set is specified).
- `stat.type`: This parameter was used for SpeeDRF, which is no longer supported.
- `type`: This parameter was used for SpeeDRF, which is no longer supported. 

###New DRF Parameters

The following parameter has been added: 

- `build_tree_one_node`: Run on a single node to use fewer CPUs. 

###DRF Algorithm Comparison

H2O  | H2O-Dev
------------- | -------------
`h2o.randomForest <- function(x,` | `h2o.randomForest <- function(`
`x,` | `x,` 
`y,` | `y,` 
`data,` | `training_frame,` 
`key="",` | `destination_key,` 
`validation,` | `validation_frame,` 
`mtries = -1,` | `mtries = -1,` 
`sample.rate=2/3,` | `sample_rate = 0.6666667,` 
 &nbsp; | `build_tree_one_node = FALSE,` 
`ntree=50` | `ntrees=50,` 
`depth=20,` | `max_depth = 20,` 
 &nbsp; | `min_rows = 1,`
`nbins=20,` | `nbins = 20,` 
`balance.classes = FALSE,` | `balance_classes = FALSE,` 
`score.each.iteration = FALSE,` | `score_each_iteration = FALSE,` 
`seed = -1,` | `_seed)` 
`nodesize = 1,` |  
`classification=TRUE,` | 
`importance=FALSE,` | 
`nfolds=0,` | 
`holdout.fraction = 0,` | 
`max.after.balance.size = 5,` | `max_after_balance_size` 
`class.sampling.factors = NULL,` | `class_sampling_factors` 
`doGrpSplit = TRUE,` | 
`verbose = FALSE,` |
`oobee = TRUE,` | 
`stat.type = "ENTROPY",` |
`type = "fast")` | 


###Output


The following table provides the component name in H2O, the corresponding component name in H2O-Dev (if supported), and the model type (binomial, multinomial, or all). Many components are now included in `h2o.performance`; for more information, refer to [(`h2o.performance`)](#h2operf).

H2O  | H2O-Dev  | Model Type
------------- | ------------- | -------------
`@model$priorDistribution`| &nbsp;  | `all`
`@model$params` | `@allparameters` | `all`
`@model$mse` | `@model$scoring_history` | `all`
`@model$forest` | `@model$model_summary`  | `all`
`@model$classification` | &nbsp;  | `all`
`@model$varimp` | `@model$variable_importances` | `all`
`@model$confusion` | `@model$training_metrics$cm$table`  | `binomial` and `multinomial`
`@model$auc` | `@model$training_metrics$AUC`  | `binomial`
`@model$gini` | `@model$training_metrics$Gini`  | `binomial`
`@model$best_cutoff` | &nbsp;  | `binomial`
`@model$F1` | `@model$training_metrics$thresholds_and_metric_scores$f1`  | `binomial`
`@model$F2` | `@model$training_metrics$thresholds_and_metric_scores$f2`  | `binomial`
`@model$accuracy` | `@model$training_metrics$thresholds_and_metric_scores$accuracy`  | `binomial`
`@model$Error` | `@model$Error`  | `binomial`
`@model$precision` | `@model$training_metrics$thresholds_and_metric_scores$precision`  | `binomial`
`@model$recall` | `@model$training_metrics$thresholds_and_metric_scores$recall`  | `binomial`
`@model$mcc` | `@model$training_metrics$thresholds_and_metric_scores$absolute_MCC`  | `binomial`
`@model$max_per_class_err` | currently replaced by `@model$training_metrics$thresholds_and_metric_scores$min_per_class_correct`  | `binomial`





