#Migrating to H2O 3.0

We're excited about the upcoming release of the latest and greatest version of H2O, and we hope you are too! H2O 3.0 has lots of improvements, including: 

- Powerful Python APIs
- Flow, a brand-new intuitive web UI
- The ability to share, annotate, and modify workflows
- Versioned REST APIs with full metadata
- Spark integration using Sparkling Water
- Improved algorithm accuracy and speed

and much more! Overall, H2O has been retooled for better accuracy and performance and to provide additional functionality. If you're a current user of H2O, we strongly encourage you to upgrade to the latest version to take advantage of the latest features and capabilities. 

Please be aware that H2O 3.0 will supersede all previous versions of H2O as the primary version as of May 15th, 2015. Support for previous versions will be offered for a limited time, but there will no longer be any significant updates to the previous version of H2O. 

The following information and links will inform you about what's new and different and help you prepare to upgrade to H2O 3.0. 

Overall, H2O 3.0 is more stable, elegant, and simplified, with additional capabilities not available in previous versions of H2O. 

---

##Algorithm Changes

Most of the algorithms available in previous versions of H2O have been improved in terms of speed and accuracy. Currently available model types include:

###Supervised 

- **Generalized Linear Model (GLM)**: Binomial classification, regression (including logistic regression)
- **Distributed Random Forest (DRF)**: Binomial classification, multinomial classification, regression
- **Gradient Boosting Machine (GBM)**: Binomial classification, multinomial classification, regression
- **Deep Learning (DL)**: Binomial classification, multinomial classification, regression

###Unsupervised

- K-means
- Principal Component Analysis
- Autoencoder 

There are a few algorithms that are still being refined to provide these same benefits and will be available in a future version of H2O. 

Currently, the following algorithms and associated capabilities are still in development: 

- Naïve Bayes
- GLRM

Check back for updates, as these algorithms will be re-introduced in an improved form in a future version of H2O. 

**Note**: The SpeeDRF model has been removed, as it was originally intended as an optimization for small data only. This optimization will be added to the Distributed Random Forest model automatically for small data in a future version of H2O. 

---

##Parsing Changes

In H2O Classic, the parser reads all the data and tries to guess the column type. In H2O 3.0, the parser reads a subset and makes a type guess for each column. In Flow, you can view the preliminary parse results in the **Edit Column Names and Types** area. To change the column type, select an option from the drop-down menu to the right  of the column. H2O 3.0 can also automatically identify mixed-type columns; in H2O Classic, if one column is mixed integers or real numbers using a string, the output is blank. 

---

##Web UI Changes

Our web UI has been completely overhauled with a much more intuitive interface that is similar to IPython Notebook. Each point-and-click action is translated immediately into an individual workflow script that can be saved for later interactive and offline use.  As a result, you can now revise and rerun your workflows easily, and can even add comments and rich media. 

For more information, refer to our [Getting Started with Flow](https://github.com/h2oai/h2o-dev/blob/master/h2o-docs/src/product/flow/README.md) guide, which comprehensively documents how to use Flow. You can also view this brief [video](https://www.youtube.com/watch?v=wzeuFfbW7WE), which provides an overview of Flow in action. 

---

##API Users

H2O's new Python API allows Pythonistas to use H2O in their favorite environment. Using the Python command line or an integrated development environment like IPython Notebook, H2O users can control clusters and manage massive datasets quickly. 

H2O's REST API is the basis for the web UI (Flow), as well as the R and Python APIs, and is versioned for stability. It is also easier to understand and use, with full metadata available dynamically from the server, allowing for easier integration by developers. 

---

##Java Users

Generated Java REST classes ease REST API use by external programs running in a Java Virtual Machine (JVM).

As in previous versions of H2O, users can export trained models as Java objects for easy integration into JVM applications. H2O is currently the only ML tool that provides this capability, making it the data science tool of choice for enterprise developers. 

---

##R Users

If you use H2O primarily in R, be aware that as a result of the improvements to the R package for H2O scripts created using previous versions (Nunes 2.8.6.2 or prior) will require minor revisions to work with H2O 3.0. 

To assist our R users in upgrading to H2O 3.0, a "shim" tool has been developed. The [shim](https://github.com/h2oai/h2o-dev/blob/9795c401b7be339be56b1b366ffe816133cccb9d/h2o-r/h2o-package/R/shim.R) reviews your script, identifies deprecated or revised parameters and arguments, and suggests replacements. 

  >**Note**: As of Slater v.3.2.0.10, this shim will no longer be available. 

There is also an [R Porting Guide](#PortingGuide) that provides a side-by-side comparison of the algorithms in the previous version of H2O with H2O 3.0. It outlines the new, revised, and deprecated parameters for each algorithm, as well as the changes to the output. 

---

<a name="PortingGuide"></a>

#Porting R Scripts

This document outlines how to port R scripts written in previous versions of H2O (Nunes 2.8.6.2 or prior, also known as "H2O Classic") for compatibility with the new H2O 3.0 API. When upgrading from H2O to H2O 3.0, most functions are the same. However, there are some differences that will need to be resolved when porting any scripts that were originally created using H2O to H2O 3.0. 

The original R script for H2O is listed first, followed by the updated script for H2O 3.0. 

Some of the parameters have been renamed for consistency. For each algorithm, a table that describes the differences is provided. 

For additional assistance within R, enter a question mark before the command (for example, `?h2o.glm`). 

There is also a "shim" available that will review R scripts created with previous versions of H2O, identify deprecated or renamed parameters, and suggest replacements. For more information, refer to the repo [here](https://github.com/h2oai/h2o-dev/blob/d9693a97da939a2b77c24507c8b40a5992192489/h2o-r/h2o-package/R/shim.R). 

##Changes from H2O 2.8 to H2O 3.0

###`h2o.exec`
The `h2o.exec` command is no longer supported. Any workflows using `h2o.exec` must be revised to remove this command.  If the H2O 3.0 workflow contains any parameters or commands from H2O Classic, errors will result and the workflow will fail. 

The purpose of `h2o.exec` was to wrap expressions so that they could be evaluated in a single `\Exec2` call. For example, 
 `h2o.exec(fr[,1] + 2/fr[,3])`
 and 
 `fr[,1] + 2/fr[,3]`
produced the same results in H2O. However, the first example makes a single REST call and uses a single temp object, while the second makes several REST calls and uses several temp objects. 

Due to the improved architecture in H2O 3.0, the need to use `h2o.exec` has been eliminated, as the expression can be processed by R as an "unwrapped" typical R expression. 

Currently, the only known exception is when `factor` is used in conjunction with `h2o.exec`. For example, `h2o.exec(fr$myIntCol <- factor(fr$myIntCol))` would become `fr$myIntCol <- as.factor(fr$myIntCol)`

Note also that an array is not inside a string:

An int array is [1, 2, 3], *not* "[1, 2, 3]".

A String array is ["f00", "b4r"], *not* "[\"f00\", \"b4r\"]"

Only string values are enclosed in double quotation marks (`"`).  

<a name="h2operf"></a>
###`h2o.performance`

To access any exclusively binomial output, use `h2o.performance`, optionally with the corresponding accessor. The accessor can only use the model metrics object created by `h2o.performance`. Each accessor is named for its corresponding field (for example, `h2o.AUC`, `h2o.gini`, `h2o.F1`). `h2o.performance` supports all current algorithms except for K-Means. 

If you specify a data frame as a second parameter, H2O will use the specified data frame for scoring. If you do not specify a second parameter, the training metrics for the model metrics object are used. 

###`xval` and `validation` slots

The `xval` slot has been removed, as `nfolds` is not currently supported. 

The `validation` slot has been merged with the `model` slot. 

###Principal Components Regression (PCR)

Principal Components Regression (PCR) has also been deprecated. To obtain PCR values, create a Principal Components Analysis (PCA) model, then create a GLM model from the scored data from the PCA model. 

###Saving and Loading Models

Saving and loading a model from R is supported in version 3.0.0.18 and later. H2O 3.0 uses the same binary serialization method as previous versions of H2O, but saves the model and its dependencies into a directory, with each object as a separate file. The `save_CV` option for  available in previous versions of H2O has been deprecated, as `h2o.saveAll` and `h2o.loadAll` are not currently supported. The following commands are now supported: 

- `h2o.saveModel`
- `h2o.loadModel`



**Table of Contents**

- [GBM](#GBM)
- [GLM](#GLM)
- [K-Means](#Kmeans)
- [Deep Learning](#DL)
- [Distributed Random Forest](#DRF)



<a name="GBM"></a>
##GBM

N-fold cross-validation and grid search are currently supported in H2O 3.0. 

###Renamed GBM Parameters

The following parameters have been renamed, but retain the same functions: 

H2O Classic Parameter Name | H2O 3.0 Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `model_id`
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
- `grid.parallelism`: Specifying the number of parallel threads to run during a grid search is no longer supported. 

###New GBM Parameters

The following parameters have been added: 

- `seed`: A random number to control sampling and initialization when `balance_classes` is enabled. 
- `score_each_iteration`: Display error rate information after each tree in the requested set is built. 
- `build_tree_one_node`: Run on a single node to use fewer CPUs. 

###GBM Algorithm Comparison

H2O Classic  | H2O 3.0 
------------- | -------------
`h2o.gbm <- function(` | `h2o.gbm <- function(` 
`x,` |`x,`
`y,` |`y,` 
`data,` | `training_frame,`
`key = "",` | `model_id,` 
`distribution = 'multinomial',` | `distribution = c("bernoulli", "multinomial", "gaussian"),` 
`n.trees = 10,` | `ntrees = 50`
`interaction.depth = 5,` | `max_depth = 5,` 
`n.minobsinnode = 10,` | `min_rows = 10,` 
`shrinkage = 0.1,` | `learn_rate = 0.1,` 
`n.bins = 20,`| `nbins = 20,` 
`validation,` | `validation_frame = NULL,` 
`balance.classes = FALSE` | `balance_classes = FALSE,` 
`max.after.balance.size = 5,` | `max_after_balance_size = 1,` 
 &nbsp; | `seed,` 
 &nbsp; | `build_tree_one_node = FALSE,`
 &nbsp; | `score_each_iteration)`
`group_split = TRUE,` | 
`importance = FALSE,` | 
`nfolds = 0,` | 
`holdout.fraction = 0,` | 
`class.sampling.factors = NULL,` | 
`grid.parallelism = 1)` | 


###Output

The following table provides the component name in H2O, the corresponding component name in H2O 3.0 (if supported), and the model type (binomial, multinomial, or all). Many components are now included in `h2o.performance`; for more information, refer to [(`h2o.performance`)](#h2operf).

H2O Classic | H2O 3.0  | Model Type
------------- | ------------- | -------------
`@model$priorDistribution`| &nbsp;  | `all`
`@model$params` | `@allparameters` | `all`
`@model$err` | `@model$scoring_history` | `all`
`@model$classification` | &nbsp;  | `all`
`@model$varimp` | `@model$variable_importances` | `all`
`@model$confusion` | `@model$training_metrics@metrics$cm$table`  | `binomial` and `multinomial`
`@model$auc` | `@model$training_metrics@metrics$AUC`  | `binomial`
`@model$gini` | `@model$training_metrics@metrics$Gini`  | `binomial`
`@model$best_cutoff` | &nbsp;  | `binomial`
`@model$F1` | `@model$training_metrics@metrics$thresholds_and_metric_scores$f1`  | `binomial`
`@model$F2` | `@model$training_metrics@metrics$thresholds_and_metric_scores$f2`  | `binomial`
`@model$accuracy` | `@model$training_metrics@metrics$thresholds_and_metric_scores$accuracy`  | `binomial`
`@model$error` | &nbsp;  | `binomial`
`@model$precision` | `@model$training_metrics@metrics$thresholds_and_metric_scores$precision`  | `binomial`
`@model$recall` | `@model$training_metrics@metrics$thresholds_and_metric_scores$recall`  | `binomial`
`@model$mcc` | `@model$training_metrics@metrics$thresholds_and_metric_scores$absolute_MCC`  | `binomial`
`@model$max_per_class_err` | currently replaced by `@model$training_metrics@metrics$thresholds_and_metric_scores$min_per_class_correct`  | `binomial`





---

<a name="GLM"></a>
##GLM

###Renamed GLM Parameters

The following parameters have been renamed, but retain the same functions:

H2O Classic Parameter Name | H2O 3.0 Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `model_id`
`nlambda` | `nlambdas`
`lambda.min.ratio` | `lambda_min_ratio`
 `iter.max` | `max_iterations`
 `epsilon` | `beta_epsilon`

###Deprecated GLM Parameters
 
The following parameters have been removed: 
 
 - `return_all_lambda`: A logical value indicating whether to return every model built during the lambda search. (may be re-added)
 - `higher_accuracy`: For improved accuracy, adjust the `beta_epsilon` value. 
 - `strong_rules`: Discards predictors likely to have 0 coefficients prior to model building. (may be re-added as enabled by default)
 - `non_negative`: Specify a non-negative response. (may be re-added)
 - `variable_importances`: Variable importances are now computed automatically and displayed in the model output. They have been renamed to *Normalized Coefficient Magnitudes*. 
 - `disable_line_search`: This parameter has been deprecated, as it was mainly used for testing purposes. 
 - `max_predictors`: Stops training the algorithm if the number of predictors exceeds the specified value. (may be re-added)

###New GLM Parameters
 
 The following parameters have been added: 
 
 - `validation_frame`: Specify the validation dataset. 
 - `solver`: Select IRLSM or LBFGS. 

###GLM Algorithm Comparison


H2O Classic | H2O 3.0 
------------- | -------------
`h2o.glm <- function(` | `h2o.startGLMJob <- function(`
`x,` | `x,`
`y,` | `y,` 
`data,` |`training_frame,` 
`key = "",` | `model_id,` 
 &nbsp; | `validation_frame`
`iter.max = 100,` |  `max_iterations = 50,` 
`epsilon = 1e-4` | `beta_epsilon = 0` 
`strong_rules = TRUE,` | 
`return_all_lambda = FALSE,` | 
`intercept = TRUE,` | `intercept = TRUE`
`non_negative = FALSE,` | 
&nbsp; | `solver = c("IRLSM", "L_BFGS"),`
`standardize = TRUE,` | `standardize = TRUE,` 
`family,` | `family = c("gaussian", "binomial", "poisson", "gamma", "tweedie"),` 
`link,` | `link = c("family_default", "identity", "logit", "log", "inverse", "tweedie"),`
`tweedie.p = ifelse(family == "tweedie",1.5, NA_real_)` |  `tweedie_variance_power = NaN,`
&nbsp; | `tweedie_link_power = NaN,`
`alpha = 0.5,` | `alpha = 0.5,` 
`prior = NULL` | `prior = 0.0,` 
`lambda = 1e-5,` | `lambda = 1e-05,` 
`lambda_search = FALSE,` | `lambda_search = FALSE,` 
`nlambda = -1,` | `nlambdas = -1,` 
`lambda.min.ratio = -1,` | `lambda_min_ratio = 1.0,` 
`use_all_factor_levels = FALSE` | `use_all_factor_levels = FALSE,` 
`nfolds = 0,` |  `nfolds = 0,`
`beta_constraints = NULL,` | `beta_constraint = NULL)` 
`higher_accuracy = FALSE,` |  
`variable_importances = FALSE,` | 
`disable_line_search = FALSE,` | 
`offset = NULL,` | 
`max_predictors = -1)` |


###Output


The following table provides the component name in H2O, the corresponding component name in H2O 3.0 (if supported), and the model type (binomial, multinomial, or all). Many components are now included in `h2o.performance`; for more information, refer to [(`h2o.performance`)](#h2operf).

H2O Classic | H2O 3.0  | Model Type
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

H2O Classic Parameter Name | H2O 3.0 Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `model_id`
`centers` | `k`
`cols` | `x`
`iter.max` | `max_iterations`
`normalize` | `standardize`

**Note** In H2O, the `normalize` parameter was disabled by default. The `standardize` parameter is enabled by default in H2O 3.0 to provide more accurate results for datasets containing columns with large values. 

###New K-Means Parameters

The following parameters have been added:

- `user` has been added as an additional option for the `init` parameter. Using this parameter forces the K-Means algorithm to start at the user-specified points. 
- `user_points`: Specify starting points for the K-Means algorithm. 

###K-Means Algorithm Comparison

H2O Classic | H2O 3.0
------------- | -------------
`h2o.kmeans <- function(` | `h2o.kmeans <- function(`
`data,` | `training_frame,` 
`cols = '',` | `x,`
`centers,` | `k,`
`key = "",` | `model_id,`
`iter.max = 10,` | `max_iterations = 1000,`
`normalize = FALSE,`  | `standardize = TRUE,`
`init = "none",` | `init = c("Furthest","Random", "PlusPlus"),`
`seed = 0,` | `seed)`

###Output


The following table provides the component name in H2O and the corresponding component name in H2O 3.0 (if supported).

H2O Classic | H2O 3.0
------------- | -------------
`@model$params` | `@allparameters`
`@model$centers` | `@model$centers`
`@model$tot.withinss` | `@model$tot_withinss`
`@model$size` | `@model$size`
`@model$iter` | `@model$iterations`
&nbsp; | `@model$_scoring_history`
&nbsp; | `@model$_model_summary`

---

<a name="DL"></a>
##Deep Learning

**Note**: If the results in the confusion matrix are incorrect, verify that `score_training_samples` is equal to 0. By default, only the first 10,000 rows are included. 

###Renamed Deep Learning Parameters

The following parameters have been renamed, but retain the same functions: 

H2O Classic Parameter Name | H2O 3.0 Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `model_id`
`validation` | `validation_frame`
`class.sampling.factors` | `class_sampling_factors`
`override_with_best_model` | `overwrite_with_best_model`
`dlmodel@model$valid_class_error` | `@model$validation_metrics@$MSE`


###Deprecated DL Parameters

The following parameters have been removed:

- `classification`: Classification is now inferred from the data type.
- `holdout_fraction`: Fraction of the training data to hold out for validation.
- `dlmodel@model$best_cutoff`: This output parameter has been removed. 

###New DL Parameters

The following parameters have been added: 

- `export_weights_and_biases`: An additional option allowing users to export the raw weights and biases as H2O frames. 

The following options for the `loss` parameter have been added:

- `absolute`: Provides strong penalties for mispredictions 
- `huber`: Can improve results for regression 

###DL Algorithm Comparison

H2O Classic  | H2O 3.0 
------------- | -------------
`h2o.deeplearning <- function(x,` | `h2o.deeplearning <- function(x, `
`y,` | `y,`
`data,` | `training_frame,` 
`key = "",` | `model_id = "",`
`override_with_best_model,` | `overwrite_with_best_model = true,` 
`classification = TRUE,` | 
`nfolds = 0,` |  `nfolds = 0`
`validation,` | `validation_frame,` 
`holdout_fraction = 0,` |  
`checkpoint = " "` | `checkpoint,` 
`autoencoder,` | `autoencoder = false,` 
`use_all_factor_levels,` | `use_all_factor_levels = true`
`activation,` | `_activation = c("Rectifier", "Tanh", "TanhWithDropout", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"),`
`hidden,` | `hidden= c(200, 200),`
`epochs,` | `epochs = 10.0,`
`train_samples_per_iteration,` |`train_samples_per_iteration = -2,`
`seed,` | `_seed,` 
`adaptive_rate,` | `adaptive_rate = true,` 
`rho,` | `rho = 0.99,` 
`epsilon,` | `epsilon = 1e-8,` 
`rate,` | `rate = .005,` 
`rate_annealing,` | `rate_annealing = 1e-6,` 
`rate_decay,` | `rate_decay = 1.0,` 
`momentum_start,` | `momentum_start = 0,`
`momentum_ramp,` | `momentum_ramp = 1e6,`
`momentum_stable,` | `momentum_stable = 0,` 
`nesterov_accelerated_gradient,` | `nesterov_accelerated_gradient = true,`
`input_dropout_ratio,` | `input_dropout_ratio = 0.0,` 
`hidden_dropout_ratios,` | `hidden_dropout_ratios,` 
`l1,` | `l1 = 0.0,` 
`l2,` | `l2 = 0.0,` 
`max_w2,` | `max_w2 = Inf,`
`initial_weight_distribution,` | `initial_weight_distribution = c("UniformAdaptive","Uniform", "Normal"),`
`initial_weight_scale,` | `initial_weight_scale = 1.0,`
`loss,` | `loss = "Automatic", "CrossEntropy", "MeanSquare", "Absolute", "Huber"),`
`score_interval,` | `score_interval = 5,` 
`score_training_samples,` | `score_training_samples = 10000l,` 
`score_validation_samples,` | `score_validation_samples = 0l,`
`score_duty_cycle,` | `score_duty_cycle = 0.1,` 
`classification_stop,` | `classification_stop = 0`
`regression_stop,` | `regression_stop = 1e-6,`
`quiet_mode,` | `quiet_mode = false,`
`max_confusion_matrix_size,` | `max_confusion_matrix_size,`
`max_hit_ratio_k,` | `max_hit_ratio_k,`
`balance_classes,` | `balance_classes = false,`
`class_sampling_factors,` | `class_sampling_factors,`
`max_after_balance_size,` | `max_after_balance_size,` 
`score_validation_sampling,` | `score_validation_sampling,`
`diagnostics,` | `diagnostics = true,` 
`variable_importances,` | `variable_importances = false,`
`fast_mode,` | `fast_mode = true,` 
`ignore_const_cols,` | `ignore_const_cols = true,`
`force_load_balance,` | `force_load_balance = true,`
`replicate_training_data,` | `replicate_training_data = true,`
`single_node_mode,` | `single_node_mode = false,` 
`shuffle_training_data,` | `shuffle_training_data = false,`
`sparse,` | `sparse = false,` 
`col_major,` | `col_major = false,`
`max_categorical_features,` | `max_categorical_features = Integer.MAX_VALUE,`
`reproducible)` | `reproducible=FALSE,` 
`average_activation` | `average_activation = 0,`
 &nbsp; | `sparsity_beta = 0`
 &nbsp; | `export_weights_and_biases=FALSE)`

###Output


The following table provides the component name in H2O, the corresponding component name in H2O 3.0 (if supported), and the model type (binomial, multinomial, or all). Many components are now included in `h2o.performance`; for more information, refer to [(`h2o.performance`)](#h2operf).

H2O Classic | H2O 3.0  | Model Type
------------- | ------------- | ------------- 
`@model$priorDistribution`| &nbsp;  | `all`
`@model$params` | `@allparameters` | `all`
`@model$train_class_error` | `@model$training_metrics@metrics@$MSE`  | `all`
`@model$valid_class_error` | `@model$validation_metrics@$MSE` | `all`|
`@model$varimp` | `@model$_variable_importances` | `all`
`@model$confusion` | `@model$training_metrics@metrics$cm$table`  | `binomial` and `multinomial`
`@model$train_auc` | `@model$train_AUC`  | `binomial`
&nbsp; | `@model$_validation_metrics` | `all`
&nbsp; | `@model$_model_summary` | `all`
&nbsp; | `@model$_scoring_history` | `all`

 
 ---

<a name="DRF"></a>
##Distributed Random Forest

###Changes to DRF in H2O 3.0 

Distributed Random Forest (DRF) was represented as `h2o.randomForest(type="BigData", ...)` in H2O Classic. In H2O Classic, SpeeDRF (`type="fast"`) was not as accurate, especially for complex data with categoricals, and did not address regression problems. DRF (`type="BigData"`) was at least as accurate as SpeeDRF (`type="fast"`) and was the only algorithm that scaled to big data (data too large to fit on a single node). 
In H2O 3.0, our plan is to improve the performance of DRF so that the data fits on a single node (optimally, for all cases), which will make SpeeDRF obsolete. Ultimately, the goal is provide a single algorithm that provides the "best of both worlds" for all datasets and use cases. 
Please note that H2O does not currently support the ability to specify the number of trees when using `h2o.predict` for a DRF model. 


**Note**: H2O 3.0 only supports DRF. SpeeDRF is no longer supported. The functionality of DRF in H2O 3.0 is similar to DRF functionality in H2O. 

###Renamed DRF Parameters

The following parameters have been renamed, but retain the same functions: 

H2O Classic Parameter Name | H2O 3.0 Parameter Name
-------------------|-----------------------
`data` | `training_frame`
`key` | `model_id`
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

H2O Classic | H2O 3.0
------------- | -------------
`h2o.randomForest <- function(x,` | `h2o.randomForest <- function(`
`x,` | `x,` 
`y,` | `y,` 
`data,` | `training_frame,` 
`key="",` | `model_id,` 
`validation,` | `validation_frame,` 
`mtries = -1,` | `mtries = -1,` 
`sample.rate=2/3,` | `sample_rate = 0.632,` 
 &nbsp; | `build_tree_one_node = FALSE,` 
`ntree=50` | `ntrees=50,` 
`depth=20,` | `max_depth = 20,` 
 &nbsp; | `min_rows = 1,`
`nbins=20,` | `nbins = 20,` 
`balance.classes = FALSE,` | `balance_classes = FALSE,` 
`score.each.iteration = FALSE,` | `score_each_iteration = FALSE,` 
`seed = -1,` | `seed` 
`nodesize = 1,` |  
`classification=TRUE,` | 
`importance=FALSE,` | 
`nfolds=0,` | 
`holdout.fraction = 0,` | 
`max.after.balance.size = 5,` | `max_after_balance_size)` 
`class.sampling.factors = NULL,` | &nbsp; 
`doGrpSplit = TRUE,` | 
`verbose = FALSE,` |
`oobee = TRUE,` | 
`stat.type = "ENTROPY",` |
`type = "fast")` | 


###Output


The following table provides the component name in H2O, the corresponding component name in H2O 3.0 (if supported), and the model type (binomial, multinomial, or all). Many components are now included in `h2o.performance`; for more information, refer to [(`h2o.performance`)](#h2operf).

H2O Classic | H2O 3.0  | Model Type
------------- | ------------- | -------------
`@model$priorDistribution`| &nbsp;  | `all`
`@model$params` | `@allparameters` | `all`
`@model$mse` | `@model$scoring_history` | `all`
`@model$forest` | `@model$model_summary`  | `all`
`@model$classification` | &nbsp;  | `all`
`@model$varimp` | `@model$variable_importances` | `all`
`@model$confusion` | `@model$training_metrics@metrics$cm$table`  | `binomial` and `multinomial`
`@model$auc` | `@model$training_metrics@metrics$AUC`  | `binomial`
`@model$gini` | `@model$training_metrics@metrics$Gini`  | `binomial`
`@model$best_cutoff` | &nbsp;  | `binomial`
`@model$F1` | `@model$training_metrics@metrics$thresholds_and_metric_scores$f1`  | `binomial`
`@model$F2` | `@model$training_metrics@metrics$thresholds_and_metric_scores$f2`  | `binomial`
`@model$accuracy` | `@model$training_metrics@metrics$thresholds_and_metric_scores$accuracy`  | `binomial`
`@model$Error` | `@model$Error`  | `binomial`
`@model$precision` | `@model$training_metrics@metrics$thresholds_and_metric_scores$precision`  | `binomial`
`@model$recall` | `@model$training_metrics@metrics$thresholds_and_metric_scores$recall`  | `binomial`
`@model$mcc` | `@model$training_metrics@metrics$thresholds_and_metric_scores$absolute_MCC`  | `binomial`
`@model$max_per_class_err` | currently replaced by `@model$training_metrics@metrics$thresholds_and_metric_scores$min_per_class_correct`  | `binomial`


##Github Users

All users who pull directly from the H2O classic repo on Github should be aware that this repo will be renamed. To retain access to the original H2O (2.8.6.2 and prior) repository: 

**The simple way**

This is the easiest way to change your local repo and is recommended for most users. 

0. Enter `git remote -v` to view a list of your repositories. 
0. Copy the address your H2O classic repo (refer to the text in brackets below - your address will vary depending on your connection method):

  ```
  H2O_User-MBP:h2o H2O_User$ git remote -v
  origin	https://{H2O_User@github.com}/h2oai/h2o.git (fetch)
  origin	https://{H2O_User@github.com}/h2oai/h2o.git (push)
  ```
0. Enter `git remote set-url origin {H2O_User@github.com}:h2oai/h2o-2.git`, where `{H2O_User@github.com}` represents the address copied in the previous step. 

**The more complicated way**

This method involves editing the Github config file and should only be attempted by users who are confident enough with their knowledge of Github to do so. 

0. Enter `vim .git/config`. 
0. Look for the `[remote "origin"]` section:

   ```
   [remote "origin"]
        url = https://H2O_User@github.com/h2oai/h2o.git
        fetch = +refs/heads/*:refs/remotes/origin/*
    ```
0. In the `url =` line, change `h2o.git` to `h2o-2.git`. 
0. Save the changes.  

The latest version of H2O is stored in the `h2o-3` repository. All previous links to this repo will still work, but if you would like to manually update your Github configuration, follow the instructions above, replacing `h2o-2` with `h2o-3`. 
