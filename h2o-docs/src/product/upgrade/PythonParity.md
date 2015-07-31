#R/Python Parity

The following is a list of R functions alongside the equivalent Python ones. 
Most methods in Python are member methods of the of the H2OFrame class. H2O does not override native Python `all` or `any` methods but these are included as member methods (e.g., `myFrame[0].any()` not `any(myFrame[0])`). 

Similarly, model accessor methods are members of their respective classes. 

**Note**: This is not a complete listing of the R or Python H2O API. Please refer to the [complete documentation](http://h2o-release.s3.amazonaws.com/h2o/master/3098/docs-website/h2o-py/docs/index.html). 

**H2O Algorithms**:

|R             | Python        | Function Description     |
|------------- |---------------| -------------|
| `h2o.naiveBayes`      | `h2o.naive_bayes` | Computes Naive Bayes probabilities on an H2O dataset. |
| `h2o.prcomp`   | `h2o.prcomp`  | Performs principal components analysis of an H2O dataset using the power method to calculate the singular value decomposition of the Gram matrix. | 
| `h2o.svd`      | `h2o.svd` | Performs singular value decomposition of an H2O dataset using the power method. | 
| `h2o.deeplearning`      | `h2o.deeplearning` | Creates a Deep Learning model. |
| `h2o.gbm`      | `h2o.gbm` |Creates a Gradient Boosting Machine model.  |
| `h2o.glm`      | `h2o.glm` | Creates a Generalized Linear model. |
| `h2o.randomForest`      | `h2o.random_forest` |Creates a Distributed Random Forest model.  |
| `h2o.kmeans`      | `h2o.kmeans` | Creates a K-means model. |

**H2OFrame Operations**:

|R             | Python        | Function Description     |
|------------- |---------------| -------------|
| `%/%`          | `//`          | Floor division.                        | 
| `%in%`         | `in`          | Returns `true` if the element is in the H2OFrame.  |
| `%x%`          | `mult`        | Multiplies matrices.                   |
| `all`          | `all`         | Returns `true` if every element in the column is True.   |
| `any`          | `any`         | Returns `true` if any element in the column is True.     | 
| `anyFactor`    | `anyfactor`   | Returns whether or not the frame has factor columns. | 
| `as.character` | `ascharacter` | Converts the column to characters.        | 
| `as.Date`      | `as_date`     | Returns the column with all elements converted to millis since the epoch. |
| `as.numeric`   | `asnumeric`   | Converts factor columns to numbers (numeric columns unchanged). |
| `cummax`       | `cummax`      | Returns the cumulative max over the column.          | 
| `cummin`       | `cummin`      | Returns the cumulative min over the column.          | 
| `cumprod`      | `cumprod`     | Returns the cumulative product over the column.      |   
| `cumsum`       | `cumsum`      | Returns the cumulative sum over the column.          |  
| `h2o.assign`   | `h2o.assign`  | Copies the data frame and assigns it the specified key. |
| `h2o.biases`   | `biases`      | (for Deep Learning) Returns the frame for the respective bias vector. |
| `h2o.centroid_stats`  | `centroid_stats`  | (for clustering models) Returns the centroid statistics for each cluster. |
| `h2o.clearLog` | `h2o.clear_log` | Clears all H2O R command and error response logs from the local disk for debugging. |
| `h2o.clusterStatus` | `h2o.cluster_status` | Checks node status for the node you are connected to. | 
| `h2o.createFrame` | `h2o.create_frame` | Creates an H2O data frame with real-valued, categorical, integer, and binary columns as specified. |
| `h2o.downloadAllLogs` | `h2o.download_all_logs` | Downloads H2O log Files to the disk. |
| `h2o.downloadCSV` | `h2o.download_csv` | Downloads an H2O data set to a CSV file on the local disk. **Caution**: Files located on the H2O server may be very large! Make sure you have enough hard drive space to accommodate the entire file. |
| `h2o.exportHDFS` | `h2o.export` | Exports a specified H2OFrame to a path on the machine this Python session is currently connected to. | 
| `h2o.getFutureModel` | `h2o.get_future_model` | Waits for the future model to finish building, then returns the model. |
| `h2o.getTimezone` | `h2o.get_timezone` | Returns the time zone for the H2O Cloud. | 
| `h2o.gsub` | `gsub` | Replaces all matches. **Note**: Changes the frame. | 
| `h2o.hist` | `hist` | Computes a histogram over a numeric column. If breaks=="FD", the MAD is used over the IQR in computing bin width. | 
| `h2o.hit_ratio_table` | `hit_ratio_table` | Retrieves the Hit Ratios. | 
| `h2o.interaction`   | `h2o.interaction` | Creates an H2O frame with n-th order interaction features between categorical columns. |
| `h2o.levels` | `levels` | Returns the factor levels for this frame and the specified column index. |
| `h2o.loadModel` | `h2o.load_model` | Loads a saved H2O model from the disk. |  
| `h2o.listTimezones` | `h2o.list_timezones` |  Returns a list of all the timezones. | 
| `h2o.ls` | `h2o.ls` | Lists Keys on an H2O Cluster. | 
| `h2o.nlevels` | `nlevels` | Returns the number of factor levels for this frame and the specified column index. |
| `h2o.num_iterations`  | `num_iterations`  | Returns the number of iterations required for convergence or to reach max iterations. |
| `h2o.openLog` | `h2o.open_log` | Opens existing logs of H2O R POST commands and error responses on the local disk. | 
| `h2o.rbind`    | `rbind`       | Combines H2O Datasets by Rows; takes a sequence of H2O data sets and combines them by rows. | 
| `h2o.removeVecs` | `remove_vecs` | Drops specified columns. |
| `h2o.rep_len` | `rep_len` | Replicates the values in `data` in the H2O backend. | 
| `h2o.saveModel` | `h2o.save_model` | Saves an H2O model object to the disk. |
| `h2o.scoreHistory` | `score_history` | Retrieves model score history. | 
| `h2o.setLevel` | `setLevel`    | Sets all column values to one of the levels. |
| `h2o.setLevels`| `setLevels`   | Applicable on a single categorical column. New domains must be aligned with the old domains. **Note**: This call does not copy the file, it changes the column in place. | 
| `h2o.setTimezone` | `h2o.set_timezone` | Sets the time zone for the H2O Cloud. | 
| `h2o.shutdown` | `h2o.shutdown` | Shuts down the specified instance. All data will be lost. |
| `h2o.startGLMJob` | `h2o.start_glm_job` | Functions the same as `h2o.glm`, but without blocking on model-build. | 
| `h2o.startLogging` | `h2o.start_logging` | Starts logging H2O R POST commands and error responses to the local disk. | 
| `h2o.stopLogging` | `h2o.stop_logging` | Stops logging of H2O R POST commands and error responses to the local disk. |
| `h2o.strsplit` | `strsplit` | Splits the strings in the target column using the specifed pattern. |
| `h2o.sub` | `sub` | Replaces the first match. **Note**: Changes the frame.|
| `h2o.tolower` | `tolower` | Translates characters from upper to lower case for a particular column. **Note**: Changes the frame. |
| `h2o.toupper` | `toupper` | Translates characters from lower to upper case for a particular column. **Note**: Changes the frame. |
| `h2o.trim` | `trim` | (Applicable only to frames with one column) Trims the edge-spaces in a column of strings. | 
| `h2o.which` | `h2o.which`|  Returns the H2OFrame of 1 column filled with 0-based indices for which the condition is True. |
| `h2o.weights`  | `weights`     | Returns the frame for the respective weight matrix (for Deep Learning). |
| `is.character` | `ischaracter` | Returns `true` if the column is a character column, otherwise `false` (same as `isstring`). |
| `is.numeric` | `isnumeric` | Returns `true` if the column is numeric, otherwise returns `false`. | 
| `match`        | `match`       | Creates a column of the positions of the first matches of its first argument in its second. | 
| `prod`         | `prod`        | Returns the product of the column.                     |
| `round`        | `round`       | Returns the rounded values in the H2OFrame to the specified number of decimal digits. |
| `scale`        | `scale`       | Centers and/or scales the columns of the H2OFrame. |
| `screeplot`    | `screeplot`   | Produces a scree plot. | 
| `signif`       | `signif`      | Returns the rounded values in the H2OFrame to the specified number of significant digits. | 
| `str`          | `structure`   | Compactly displays the structure of the specified H2OFrame instance (similar to R’s `str` method). |
| `table`        | `table`       | Returns a frame of the counts at each combination of factor levels. | 
| `transpose`    | `transpose`   | Returns the transpose of the H2OFrame.           | 
| `unique` | `unique`| Extracts the unique values in the column. |
| `+`          | `+`           | |
| `-`          | `-`           | |
| `*`          | `*`           | |
| `^`          | `**`           | |
| `%%`         | `%`           | |
| `/`          | `/`           | |
| `==`         | `==`           | |
| `>`          | `>`           | |
| `<`          | `<`           | |
| `!=`         | `!=`           | |
| `<=`         | `<=`           | |
| `>=`         | `>=`           | |
| `&`          | `and`          | |
| `|`          | `or`           | |
| `**`         | `**`           | |
| `abs`        | `abs`          | |
| `sign`       | `sign`           | |
|`sqrt`|`sqrt` | |
|`ceiling`|`ceil` | |
|`floor`|`floor` |  |          
|`trunc`|`trunc` || 
|`log`|`log` | |
|`log10`|`log10` || 
|`log2`|`log2` | |
|`log1p`|`log1p` || 
|`acos`|`acos` | |
|`acosh`|`acosh` ||       
|`asin`|`asin` | |
|`asinh`|`asinh` || 
|`atan`|`atan` | |
|`atanh`|`atanh` || 
|`exp`|`exp` | |
|`expm1`|`expm1` || 
|`cos`|`cos` | |
|`cosh`|`cosh` | |
|`cospi`|`cospi` || 
|`sin`|`sin` | |
|`sinh`|`sinh` | |
|`sinpi`|`sinpi` || 
|`tan`|`tan` | |
|`tanh`|`tanh` | |
|`tanpi`|`tanpi` | |
|`gamma`|`gamma` | |
|`lgamma`|`lgamma` | |
|`digamma`|`digamma` | |
|`trigamma`|`trigamma` | |
|`!`|`not` | |
|`is.na`|`isna` |Returns a new boolean H2OVec.| 
|`max`|`max` | Returns the maximum value of all frame entries.|
|`min`|`min` | Returns the minimum value of all frame entries.|
|`sum`|`sum` | Returns the sum of all frame entries. |
|`as.factor`|`asfactor`| Returns a lazy Expr representing this vec converted to a factor. |
|`colnames`|`col_names`|Retrieve the column names (one name per H2OVec) for this H2OFrame.|
|`colnames<-`|`setNames`||
|`dim`|`dim`| Returns the number of rows and columns in the H2OFrame.|
|`head`|`head`| Display a digestible chunk of the H2OFrame starting from the beginning. Analogous to R’s head call on a data.frame. |
|`is.factor`|`isfactor`|Returns a lazy Expr representing whether or not this vec is a factor.|
|`length`|`len`||
|`names`|`names`|Retrieves the column names (one name per H2OVec) for this H2OFrame.|
|`names<-`|`setNames`|Changes the column names to a list of strings equal to the number of columns in the H2OFrame.|
|`ncol`|`ncol`| Returns the number of columns in this H2OFrame.|
|`nrow`|`nrow`| Returns the number of rows in this H2OFrame.|
|`sd`|`sd`|Returns the standard deviation of the H2OVec elements.|
|`show`|`show`| |
|`summary`|`summary`|Generates a summary of the frame on a per-Vec basis.|
|`tail`|`tail`| Displays a digestible chunk of the H2OFrame starting from the end. Analogous to R’s tail call on a data.frame. |
|`var`|`var`| Returns the covariance matrix of the columns in this H2OFrame.|
|`cut`|`cut`| Cuts a numeric vector into factor “buckets”. Similar to R’s cut method.|
|`h2o.runif`|`runif`| Returns new H2OVec filled with doubles sampled uniformly from [0,1).|
|`quantile`|`quantile`|Computes quantiles over a given H2OFrame.|
|`h2o.cbind`|`cbind`||
|`mean`|`mean`| Returns the mean of the column.|
|`median`|`median`| Returns the median of this column.|
|`day`|`day`| Returns a new day column from a msec-since-Epoch column.|
|`dayOfWeek`|`dayOfWeek`| Returns a new Day-of-Week column from a msec-since-Epoch column.|
|`hour`|`hour`|Returns a new Hour-of-Day column from a msec-since-Epoch column.|
|`month`|`month`|Returns a new month column from a msec-since-Epoch column.|
|`week`|`week`|Returns a new week column from a msec-since-Epoch column.|
|`year`|`year`|Returns a new year column from a msec-since-Epoch column.|

**H2O Model Operations**:

|R             | Python        | Function Description     |
|------------- |---------------| -------------|
|`h2o.predict`|`predict`| Predicts on a dataset.|
|`h2o.deepfeatures`|`deepfeatures`| Returns hidden layer details.|
|`h2o.performance`|`model_performance`| Generates model metrics for this model on `test_data`.|
|`summary`|`summary`| Generates a summary of the frame on a per-Vec basis.|
|`show`|`show`| Returns the rounded values in the H2OFrame to the specified number of significant digits. |
|`h2o.varimp`|`varimp`| Prettyprints the variable importances or returns them in a list. If True, then return the variable importances in an list (ordered from most important to least important). Each entry in the list is a 4-tuple of (variable, relative_importance, scaled_importance, percentage). |
|`h2o.residual_deviance`|`residual_deviance`|Returns the residual deviance if the model has residual deviance, or None if no residual deviance. |
|`h2o.residual_dof`|`residual_degrees_of_freedom`| Returns the residual degrees of freedom if the model has residual deviance, or None if no residual degrees of freedom.|
|`h2o.null_deviance`|`null_deviance`| Returns the null deviance if the model has residual deviance, or None if no null deviance.|
|`h2o.null_dof`|`null_degrees_of_freedom`|Returns the null degrees of freedom if the model has residual deviance, or None if no null degrees of freedom. |
|`h2o.coef`|`coef`|Returns the coefficients for this model. |
|`h2o.coef_norm`|`coef_norm`| Returns the normalized coefficients.|
|`h2o.r2`|`r2`| Retrieves the R^2 coefficient for this set of metrics.|
|`h2o.mse`|`mse`|Retrieves the MSE for this set of metrics. |
|`h2o.logloss`|`logloss`|Retrieves the log loss for this set of metrics. |
|`h2o.auc`|`auc`| Retrieves the AUC for this set of metrics.|
|`h2o.aic`|`aic`| Retrieves the AIC for this set of metrics.|
|`h2o.giniCoef`|`giniCoef`| Retrieves the Gini coefficeint for this set of metrics. |
|`h2o.download_pojo`|`h2o.download_pojo`|Downloads the POJO for this model to the specified path directory - do not use a trailing slash. If path is `“”`, then dump to screen. |
|`h2o.F1`|`F1`| Returns the F1 for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.F2`|`F2`| Returns the F2 for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.F0point5`|`F0point5`|Returns the F0.5 for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval` |
|`h2o.accuracy`|`accuracy`| Returns the accuracy for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.precision`|`precision`| Returns the precision for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.tpr`|`tpr`| Returns the True Positive Rate for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.tnr`|`tnr`| Returns the True Negative Rate for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.fnr`|`fnr`| Returns the False Negative Rates for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.fpr`|`fpr`| Returns the False Positive Rates for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.mcc`|`mcc`| Returns the [Matthews correlation coefficient](https://en.wikipedia.org/wiki/Matthews_correlation_coefficient) (MCC) for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.maxPerClassError`|`max_per_class_error`| Returns the max per-class error for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.metric`|`metric`| Returns the metric value for a set of thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.confusionMatrix`|`confusion_matrix`|Returns the confusion matrix for the specified metrics/thresholds. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval` |
|`h2o.find_threshold_by_max_metric`|`find_threshold_by_max_metric`|  If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.find_idx_by_threshold`|`find_idx_by_threshold`| Retrieves the index in this metric’s threshold list at which the given threshold is located. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.size`|`size`| Returns the sizes of each cluster. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval` |
|`h2o.betweenss`|`betweenss`| Returns the between cluster sum of squares. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.totss`|`totss`| Returns the total sum of squares to grand mean. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval` |
|`h2o.tot_withinss`|`tot_withinss`| Returns the total within cluster sum of squares. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.centers`|`centers`| Returns the centers for the kmeans model. |
|`h2o.centers_std`|`centers_std`| Returns the standardized centers for the kmeans model.|
|`h2o.centroid_stats`|`centroid_stats`| Returns the centroid statistics for each cluster. If all are False (default), then return the training metric value. If more than one options is set to True, then return a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.withinss`|`withinss`| Returns the within cluster sum of squares for each cluster. If all are False (default), then returns the training metric value. If more than one options is set to True, then returns a dictionary of metrics where the keys are `train`, `valid`, and `xval`|
|`h2o.anomaly`|`anomaly`| Obtain the reconstruction error for the input test_data. |

**Other Methods**:

|R             | Python        | Function Description     |
|------------- |---------------| -------------|
|`h2o.clusterInfo`|`h2o.cluster_info`| Performs node status check for connected node.| 
|`h2o.getFrame`|`h2o.get_frame`| Obtains a handle to the frame in H2O with the frame_id key.|
|`h2o.getModel`|`h2o.get_model`| Returns the specified model.|
|`h2o.groupBy`|`group_by`| |
|`h2o.importFile`|`h2o.import_frame`| Imports a frame from a file on a remote or local machine. When running H2O on Hadoop, you can access HDFS.|
|`h2o.impute`|`impute`| Imputes a column in the H2OFrame.|
|`h2o.init`|`h2o.init`| Initiates an H2O connection to the specified IP address and port.|
|`h2o.insertMissingValues`|`insert_missing_values`|  Primarily used for testing. Randomly replaces a user-specified fraction of entries in a H2O dataset with missing values. **Caution**: This will modify the original dataset. Unless this is intended, this function should only be called on a subset of the original. |
|`h2o.logAndEcho`|`h2o.log_and_echo`| Sends a message to H2O for logging and/or debugging purposes. |
|`h2o.networkTest`|`h2o.network_test`| |
|`h2o.parseRaw`|`h2o.parse_raw`| Used in conjunction with `import_file` and `parse_setup` in order to make alterations before parsing.|
|`h2o.parseSetup`|`h2o.parse_setup`| |
|`h2o.removeAll`|`h2o.remove_all`| Removes all objects from H2O.|
|`h2o.rm`|`h2o.remove`| Removes the specified object from H2O. This is a “hard” delete of the object and removes all subparts.|
|`h2o.splitFrame`|`split_frame`| |
|`h2o.uploadFile`|`h2o.upload_file`| Uploads a dataset at the path given from the local machine to the H2O cluster.|

