#R/Python Parity

The following is a list of Python capabilities that were previously available only through the H2O R interface. Note, this is not a complete listing of the R or Python H2O API. Please refer to the complete documentation for this. 


|R             | Python        | Function Description     |
|------------- |---------------| -------------|
| `%/%`          | `//`          | Integer division.                        | 
| `%x%`          | `mult`        | Matrix multiplication.                   |
| `cummax`       | `cummax`      | Cumulative max over the column.          | 
| `cummin`       | `cummin`      | Cumulative min over the column.          | 
| `cumprod`      | `cumprod`     | Cumulative product over the column.      |   
| `cumsum`       | `cumsum`      | Cumulative sum over the column.          |  
| `transpose`    | `transpose`   | The transpose of the H2OFrame.           | 
| `round`        | `round`       | The rounded values in the H2OFrame to the specified number of decimal digits. |
| `signif`       | `signif`      | The rounded values in the H2OFrame to the specified number of significant digits. |  
| `prod`         | `prod`        | The product of the column.                     |
| `any`          | `any`         | True if any element in the column is True.     | 
| `all`          | `all`         | True if every element in the column is True.   |
| `%in%`         | `in`          | True if the element is in the H2OFrame.  |
| `as.character` | `ascharacter` | Convert the column to characters.        | 
| `as.numeric`   | `asnumeric`   | Convert factor columns to numbers (numeric columns untouched). | 
| `match`        | `match`       | Makes a column of the positions of (first) matches of its first argument in its second. | 
| `table`        | `table`       | A frame of the counts at each combination of factor levels. | 
| `anyFactor`    | `anyfactor`   | Whether or not the frame has any factor columns. | 
| `scale`        | `scale`       | Centers and/or scales the columns of the H2OFrame. | 
| `h2o.setLevel` | `setLevel`    | A method to set all column values to one of the levels. |
| `h2o.setLevels`| `setLevels`   | Works on a single categorical column. New domains must be aligned with the old domains. This call has SIDE EFFECTS and mutates the column in place (does not make a copy). | 
| `h2o.rbind`    | `rbind`       | Combines H2O Datasets by Rows; takes a sequence of H2O data sets and combines them by rows. | 
| `as.Date`      | `as_date`     | Return the column with all elements converted to millis since the epoch. |
| `str`          | `structure`   | Similar to R’s str method: Compactly Display the Structure of this H2OFrame instance. |
| `h2o.weights`  | `weights`     | Return the frame for the respective weight matrix (for Deeplearning). |
| `h2o.biases`   | `biases`      | Return the frame for the respective bias vector (for Deeplearning). |
| `h2o.scoreHistory | `score_history` | Retrieve Model Score History. | 
| `h2o.hit_ratio_table` | `hit_ratio_table` | Retrieve the Hit Ratios. | 
| `screeplot`    | `screeplot`   | Produce the scree plot. | 
| `plot`         | `plot`        | Produce the desired metric plot (currently only ROC supported). | 
| `h2o.centroid_stats`  | `centroid_stats`  | Get the centroid statistics for each cluster (for clustering models. |
| `h2o.num_iterations`  | `num_iterations`  | Get the number of iterations that it took to converge or reach max iterations. |
| `h2o.naiveBayes`      | `h2o.naive_bayes` | Compute naive Bayes probabilities on an H2O dataset. |
| `h2o.prcomp`   | `h2o.prcomp`  | Principal components analysis of a H2O dataset using the power method to calculate the singular value decomposition of the Gram matrix. | 
| `h2o.svd`      | `h2o.svd` | Singular value decomposition of a H2O dataset using the power method. | 
| `h2o.assign`   | `h2o.assign`  | Makes a copy of the data frame and gives it the desired the key. |
| `h2o.clearLog` | `h2o.clear_log` | Clear all H2O R command and error response logs from the local disk. Used primarily for debugging purposes. |
| `h2o.clusterStatus` | `h2o.cluster_status` | Node status check for the node you are connected to. | 
| `h2o.createFrame` | `h2o.create_frame` | Creates a data frame in H2O with real-valued, categorical, integer, and binary columns specified by the user. |
| `h2o.downloadAllLogs` | `h2o.download_all_logs` | Download H2O Log Files to Disk. |
| `h2o.downloadCSV` | `h2o.download_csv` | Download an H2O data set to a CSV file on the local disk. Warning: Files located on the H2O server may be very large! Make sure you have enough hard drive space to accommodate the entire file. |
| `h2o.exportHDFS` | `h2o.export` | Export a given H2OFrame to a path on the machine this python session is currently connected to. | 
| `h2o.getFutureModel` | `h2o.get_future_model` | Waits for the future model to finish building, and then returns the model. |
| `h2o.getTimezone` | `h2o.get_timezone` | Get the Time Zone on the H2O Cloud. | 
| `h2o.gsub` | `gsub` | sub and gsub perform replacement of the first and all matches respectively. Of note, mutates the frame. | 
| `h2o.hist` | `hist` | Compute a histogram over a numeric column. If breaks=="FD", the MAD is used over the IQR in computing bin width. | 
| `h2o.interaction`   | `h2o.interaction` | Categorical Interaction Feature Creation in H2O. Creates a frame in H2O with n-th order interaction features between categorical columns, as specified by the user. |
| `h2o.listTimezones` | `h2o.list_timezones` |  Get a list of all the timezones. | 
| `h2o.loadModel` | `h2o.load_model` | Load a saved H2O model from disk. |  
| `h2o.ls` | `h2o.ls` | List Keys on an H2O Cluster. | 
| `h2o.nlevels` | `nlevels` | Get the number of factor levels for this frame and the specified column index. |
| `h2o.openLog` | `h2o.open_log` | Open existing logs of H2O R POST commands and error responses on local disk. | 
| `h2o.removeVecs` | `remove_vecs` | Drop these columns. |
| `h2o.rep_len` | `rep_len` | Replicates the values in `data` in the H2O backend. | 
| `h2o.saveModel` | `h2o.save_model` | Save an H2O Model Object to Disk. |
| `h2o.setTimezone` | `h2o.set_timezone` | Set the Time Zone on the H2O Cloud. | 
| `h2o.shutdown` | `h2o.shutdown` | Shut down the specified instance. All data will be lost. |
| `h2o.startGLMJob` | `h2o.start_glm_job` | This function is the same as `h2o.glm`, but it doesn't block on model-build. | 
| `h2o.startLogging` | `h2o.start_logging` | Begin logging H2o R POST commands and error responses to local disk. | 
| `h2o.stopLogging` | `h2o.stop_logging` | Halt logging of H2O R POST commands and error responses to local disk. |
| `h2o.strsplit` | `strsplit` | Split the strings in the target column on the given pattern. |
| `h2o.sub` | `sub` | sub and gsub perform replacement of the first and all matches respectively. |
| `h2o.tolower` | `tolower` | Translate characters from upper to lower case for a particular column. Of note, mutates the frame. |
| `h2o.toupper` | `toupper` | Translate characters from lower to upper case for a particular column. Of note, mutates the frame. |
| `h2o.trim` | `trim` | Trim the edge-spaces in a column of strings (only operates on frame with one column). | 
| `is.numeric` | `isnumeric` | True if the column is numeric, otherwise return False. | 
| `is.character` | `ischaracter` | True if the column is a character column, otherwise False (same as isstring). |
| `h2o.levels` | `levels` | Get the factor levels for this frame and the specified column index. |
| `h2o.nlevels` | `nlevels` | Get the number of factor levels for this frame and the specified column index. |
| `unique` | `unique`| Extract the unique values in the column. |
| `h2o.which` | `h2o.which`|  H2OFrame of 1 column filled with 0-based indices for which the condition is True. |

