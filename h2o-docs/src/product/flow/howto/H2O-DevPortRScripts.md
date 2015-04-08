#Porting R Scripts from H2O to H2O-Dev

This document outlines how to port R scripts written in H2O for compatibility with the new H2O-Dev API. When upgrading from H2O to H2O-Dev, most functions are the same. However, there are some differences that will need to be resolved when porting any scripts that were originally created using H2O to H2O-Dev. 

The original R script for H2O is listed first, followed by the updated script for H2O-Dev. 

Some of the parameters have been renamed for consistency. For each algorithm, a table that describes the differences is provided. 

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

Due to the improved architecture in H2O-Dev, the need to use `h2o.exec` has been eliminated, as the expression can be processed by R as an "unwrapped" typcial R expression. 

Currently, the only known exception is when `factor` is used in conjunction with `h2o.exec`. For example, `h2o.exec(fr$myIntCol <- factor(fr$myIntCol))` would become `fr$myIntCol <- as.factor(fr$myIntCol)`

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

###Deprecated GBM Parameters

The following parameters have been removed: 

- `group_split`: Bit-set group splitting of categorical variables is now the default. 
- `importance`: Variable importances are now computed automatically and displayed in the model output. 
- `holdout.fraction`: The fraction of the training data to hold out for validation is no longer supported. 
- `class.sampling.factors`: Specifying the over- or under-sampling ratios per class is no longer supported. 
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
`nfolds` | `n_folds`
`nlambda` | `nlambdas`
`lambda.min.ratio` | `lambda_min_ratio`
 `iter.max` | `max_iterations`
 `epsilon` | `beta_epsilon`

###Deprecated GLM Parameters
 
The following parameters have been removed: 
 
 - `return_all_lambda`: A logical value indicating whether to return every model built during the lambda search. >> ?? may be re-added
 - `higher_accuracy`: A logical value indicating whether to use line search. >> tweak beta_eps 
 - `strong_rules`: Discards predictors likely to have 0 coefficients prior to model building. >> ?may be re-added; on by default
 - `intercept`: Defines factor columns in the model. >> re-added
 - `non_negative`: Specify a non-negative response. >> re-added
 - `variable_importances`: Variable importances are now computed automatically and displayed in the model output. They have been renamed to *Normalized Coefficient Magnitudes*. 
 - `disable_line_search`: Disables line search for faster model building. >> was for testing only
 - `offset`: Specify a column as an offset. >> -re-added
 - `max_predictors`: Stops training the algorithm if the number of predictors exceeds the specified value. >> re-added

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
&nbsp; |  
`alpha = 0.5,` | `alpha = 0.5,` 
`prior = NULL` | `prior = 0.0,` 
`lambda = 1e-5,` | `lambda = 1e-05,` 
`lambda_search = FALSE,` | `lambda_search = FALSE,` 
`nlambda = -1,` | `nlambdas = -1,` 
`lambda.min.ratio = -1,` | `lambda_min_ratio = 1.0,` 
`use_all_factor_levels = FALSE` | `use_all_factor_levels = FALSE,` 
`nfolds = 0,` | `n_folds = 0,` 
`beta_constraints = NULL,` | `beta_constraint = NULL)` 
`higher_accuracy = FALSE,` |  
`variable_importances = FALSE,` | 
`disable_line_search = FALSE,` | 
`offset = NULL,` | 
`max_predictors = -1)` |

---

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

- `dropNACols`:   Drop columns with more than 20% missing values.  >>To be added later?

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

###Deprecated DL Parameters

The following parameters have been removed:

- `classification`: Classification is now inferred from the data type.
- `holdout_fraction`: >>
- `classification_stop`: Classification is now inferred from the data type. 

###New DL Parameters

The following parameters have been added: 

- `export_weights_and_biases`: An additional option allowing users to export the raw weights and biases as H2O frames. 
- `average_activation`: Average activation for sparse auto-encoder (Experimental)

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
`override_with_best_model,` | `override_with_best_model,` 
`classification = TRUE,` | 
`nfolds = 0,` | `n_folds = 0,` 
`validation,` | `validation_frame,` 
`holdout_fraction = 0,` |  
`checkpoint = " "` | `checkpoint,` 
`autoencoder,` | `autoencoder = FALSE,` 
`use_all_factor_levels,` | `use_all_factor_levels = TRUE`
`activation,` | `activation = c("Rectifier", "Tanh", "TanhWithDropout", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"),`
`hidden,` | `hidden= c(200, 200),`
`epochs,` | `epochs = 10.0,`
`train_samples_per_iteration,` |`train_samples_per_iteration = -2,`
`seed,` | `seed,` 
`adaptive_rate,` | `adaptive_rate = TRUE,` 
`rho,` | `rho = 0.99,` 
`epsilon,` | `epsilon = 1e-08,` 
`rate,` | `rate = 0.005,` 
`rate_annealing,` | `rate_annealing = 1e-06,` 
`rate_decay,` | `rate_decay = 1.0,` 
`momentum_start,` | `momentum_start = 0,`
`momentum_ramp,` | `momentum_ramp = 1e+06,`
`momentum_stable,` | `momentum_stable = 0,` 
`nesterov_accelerated_gradient,` | `nesterov_accelerated_gradient = TRUE,`
`input_dropout_ratio,` | `input_dropout_ratio = 0,` 
`hidden_dropout_ratios,` | `hidden_dropout_ratios,` 
`l1,` | `l1 = 0,` 
`l2,` | `l2 = 0,` 
`max_w2,` | `max_w2 = Inf,`
`initial_weight_distribution,` | `initial_weight_distribution = c("UniformAdaptive","Uniform", "Normal"),`
`initial_weight_scale,` | `initial_weight_scale = 1,`
`loss,` | `loss = "Automatic", "CrossEntropy", "MeanSquare", "Absolute", "Huber"),`
`score_interval,` | `score_interval = 5,` 
`score_training_samples,` | `score_training_samples,` 
`score_validation_samples,` | `score_validation_samples,`
`score_duty_cycle,` | `score_duty_cycle,` 
`classification_stop,` | 
`regression_stop,` | `regression_stop,`
`quiet_mode,` | `quiet_mode,`
`max_confusion_matrix_size,` | `max_confusion_matrix_size,`
`max_hit_ratio_k,` | `max_hit_ratio_k,`
`balance_classes,` | `balance_classes = FALSE,`
`class_sampling_factors,` | `class_sampling_factors,`
`max_after_balance_size,` | `max_after_balance_size,` 
`score_validation_sampling,` | `score_validation_sampling,`
`diagnostics,` | `diagnostics,` 
`variable_importances,` | `variable_importances,`
`fast_mode,` | `fast_mode,` 
`ignore_const_cols,` | `ignore_const_cols,`
`force_load_balance,` | `force_load_balance,`
`replicate_training_data,` | `replicate_training_data,`
`single_node_mode,` | `single_node_mode,` 
`shuffle_training_data,` | `shuffle_training_data,`
`sparse,` | `sparse,` 
`col_major,` | `col_major,`
`max_categorical_features,` | `max_categorical_features,`
`reproducible)` | `reproducible=FALSE,` 
 &nbsp; | `average_activation,`
 &nbsp; | `export_weights_and_biases = FALSE)`

 
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

###Deprecated DRF Parameters

The following parameters have been removed: 

- `classification`: This is now automatically inferred from the response type. To achieve classification with a 0/1 response column, explicitly convert the response to a factor (`as.factor()`). 
- `importance`: Variable importances are now computed automatically and displayed in the model output. 
- `nodesize`: Use the `build_tree_one_node` parameter instead. 
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
`seed = -1,` | `seed)` 
`nodesize = 1,` |  
`classification=TRUE,` | 
`importance=FALSE,` | 
`nfolds=0,` | 
`holdout.fraction = 0,` | 
`max.after.balance.size = 5,` | `max_after_balance_size` (to be readded by Arno>>)
`class.sampling.factors = NULL,` | `class_sampling_factors` (to be readded by Arno>>)
`doGrpSplit = TRUE,` | 
`verbose = FALSE,` |
`oobee = TRUE,` | 
`stat.type = "ENTROPY",` |
`type = "fast")` | 









