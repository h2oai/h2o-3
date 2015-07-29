#R/Python Parity

The following is a list of R functions alongside the equivalent Python ones. 

**Note**: This is not a complete listing of the R or Python H2O API. Please refer to the [complete documentation](http://h2o-release.s3.amazonaws.com/h2o/master/3098/docs-website/h2o-py/docs/index.html). 

**H2O Algorithms**
|R             | Python        | Function Description     |
|------------- |---------------| -------------|
| `h2o.naiveBayes`      | `h2o.naive_bayes` | Computes Naive Bayes probabilities on an H2O dataset. |
| `h2o.prcomp`   | `h2o.prcomp`  | Performs principal components analysis of an H2O dataset using the power method to calculate the singular value decomposition of the Gram matrix. | 
| `h2o.svd`      | `h2o.svd` | Performs singular value decomposition of an H2O dataset using the power method. | 
| `h2o.deeplearning`      | `h2o.deeplearning` | |
| `h2o.gbm`      | `h2o.gbm` | |
| `h2o.glm`      | `h2o.glm` | |
| `h2o.randomForest`      | `h2o.random_forest` | |
| `h2o.kmeans`      | `h2o.kmeans` | |

**H2OFrame Operations**
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
|`is.na`|`isna` || 
|`max`|`max` | |
|`min`|`min` | |
|`sum`|`sum` ||
|`as.factor`|`asfactor`||
|`colnames`|`col_names`||
|`colnames<-`|`setNames`||
|`dim`|`dim`||
|`head`|`head`||
|`is.factor`|`isfactor`||
|`length`|`len`||
|`names`|`names`||
|`names<-`|`setNames`||
|`ncol`|`ncol`||
|`nrow`|`nrow`||
|`sd`|`sd`||
|`show`|`show`||
|`summary`|`summary`||
|`tail`|`tail`||
|`var`|`var`||
|`cut`|`cut`||
|`h2o.runif`|`runif`||
|`quantile`|`quantile`||
|`h2o.cbind`|`cbind`||
|`mean`|`mean`||
|`median`|`median`||
|`day`|`day`||
|`dayOfWeek`|`dayOfWeek`||
|`hour`|`hour`||
|`month`|`month`||
|`week`|`week`||
|`year`|`year`||

**H2O Model Operations**
|R             | Python        | Function Description     |
|------------- |---------------| -------------|
|`h2o.predict`|`predict`| |
|`h2o.deepfeatures`|`deepfeatures`| |
|`h2o.performance`|`model_performance`| |
|`summary`|`summary`| |
|`show`|`show`| |
|`h2o.varimp`|`varimp`| |
|`h2o.residual_deviance`|`residual_deviance`| |
|`h2o.residual_dof`|`residual_degrees_of_freedom`| |
|`h2o.null_deviance`|`null_deviance`| |
|`h2o.null_dof`|`null_degrees_of_freedom`| |
|`h2o.coef`|`coef`| |
|`h2o.coef_norm`|`coef_norm`| |
|`h2o.r2`|`r2`| |
|`h2o.mse`|`mse`| |
|`h2o.logloss`|`logloss`| |
|`h2o.auc`|`auc`| |
|`h2o.aic`|`aic`| |
|`h2o.giniCoef`|`giniCoef``| |
|`h2o.download_pojo`|`h2o.download_pojo`| |
|`h2o.F1`|`F1`| |
|`h2o.F2`|`F2`| |
|`h2o.F0point5`|`F0point5`| |
|`h2o.accuracy`|`accuracy`| |
|`h2o.precision`|`precision`| |
|`h2o.tpr`|`tpr`| |
|`h2o.tnr`|`tnr`| |
|`h2o.fnr`|`fnr`| |
|`h2o.fpr`|`fpr`| |
|`h2o.mcc`|`mcc`| |
|`h2o.maxPerClassError`|`max_per_class_error`| |
|`h2o.metric`|`metric`| |
|`h2o.confusionMatrix`|`confusion_matrix`| |
|`h2o.find_threshold_by_max_metric`|`find_threshold_by_max_metric`| |
|`h2o.find_idx_by_threshold`|`find_idx_by_threshold`| |
|`h2o.size`|`size`| |
|`h2o.betweenss`|`betweenss`| |
|`h2o.totss`|`totss`| |
|`h2o.tot_withinss`|`tot_withinss`| |
|`h2o.centers`|`centers`| |
|`h2o.centers_std`|`centers_std`| |
|`h2o.centroid_stats`|`centroid_stats`| |
|`h2o.withinss`|`withinss`| |
|`h2o.anomaly`|`anomaly`| |

**Other Methods**
|`h2o.clusterInfo`|`h2o.cluster_info`| |
|`h2o.getFrame`|`h2o.get_frame`| |
|`h2o.getModel`|`h2o.get_model`| |
|`h2o.groupBy`|`group_by`| |
|`h2o.importFile`|`h2o.import_frame`| |
|`h2o.impute`|`impute`| |
|`h2o.init`|`h2o.init`| |
|`h2o.insertMissingValues`|`insert_missing_values`| |
|`h2o.logAndEcho`|`h2o.log_and_echo`| |
|`h2o.networkTest`|`h2o.network_test`| |
|`h2o.parseRaw`|`h2o.parse_raw`| |
|`h2o.parseSetup`|`h2o.parse_setup`| |
|`h2o.removeAll`|`h2o.remove_all`| |
|`h2o.rm`|`h2o.remove`| |
|`h2o.splitFrame`|`split_frame`| |
|`h2o.uploadFile`|`h2o.upload_file`| |

