#R/Python Parity

All capabilities available in the H2O R package are now available in Python. This document describes the supported capabilities and any differences in behavior. 

Based on https://0xdata.atlassian.net/browse/HEXDEV-30

Include desc. of function? 

transform, within, subset - not to be ported per Spencer in 1307


|R | Python  | Function/Returns | Notes | 
|------------- |---------------| -------------|-----|
| `%/%`      |  |  | Use the `//` operator in Python |
| `%x%`      |        | Use `my_frame.mult(other_frame)`.|
| `cummax` | `cummax()` | Cumulative max over the column.        |            |
| `cummin`| `cummin()` | Cumulative min over the column. 
| `cumprod` | `cumprod()` | Cumulative product over the column. | 
| `cumsum` | `cumsum()` | Cumulative sum over the column. | 
| `transpose` | `transpose()` | Interchange rows and columns of the H2OFrame. 
| `round` | `round(digits=0)` | The rounded values in the H2OFrame to the specified number of decimal digits. | 
| `signif` | `signif(digist=6)` | The rounded values in the H2OFrame to the specified number of significant digits. | 
| `range` | | 
| `prod` | `prod(na_rm=False)`| The product of the column. 
| `any` || Not supported in Rapids (?) 
| `all` |`all()` | True if every element is True in the column.
| `%in%`| | 
| `as.character` |`ascharacter()` | A lazy Expr representing this vec converted to characters. | 
| `as.numeric`| `asnumeric()` | A frame with factor columns converted to numbers (numeric columns untouched). | 
| `match`| | Makes a vector of the positions of (first) matches of its first argument in its second. | 
| `pop` | | Implemented? 
| `push` | | Implemented? 
| `table` | | 	A frame of the counts at each combination of factor levels. | 
| `anyFactor` | | Whether or not the frame has any factor columns. 
| `subset` | | Will not be ported
| `transform`| | Will not be ported 
| `within` | | Will not be ported
| `scale` | | Centers and/or scales the columns of the H2OFrame. | 
| `setLevel`|| A method to set all column values to one of the levels. | 
| `rbind`| | Combines H2O Datasets by Rows; takes a sequence of H2O data sets and combines them by rows. :param data: an H2OFrame :return: self, with data appended (row-wise) | 
| `as.Date`| | Return the column with all elements converted to millis since the epoch.
| `str`| `structure()` | Similar to R’s str method: Compactly Display the Structure of this H2OFrame instance.
| weights & biases in R | | format? 
| scoreHistory accessor || 
| multinomial model `hit_ratio_table` accessor | | 
| screeplot | | 
| plot | | 
| `centroid_stats` | | for clustering models
| `num_iterations`| | for clustering models
| Naive Bayes | | 
| prcomp | | 
| svd | | 
| `h2o.assign` | | 
| `h2o.clearLog` | |
| `h2o.clusterStatus` | | 
| `h2o.createFrame` | |
| `h2o.downloadAllLogs` | |
| `h2o.downloadCSV` | | 
| `h2o.exportHDFS` | | 
| `h2o.getFutureModel` | |
| `h2o.getTimezone` | | 
| `h2o.gsub` | | 
| `h2o.hist` | | 
| `h2o.interaction` | | 
| `h2o.listTimezones` | | 
| `h2o.loadModel` | | 
| `h2o.ls` | | 
| `h2o.makeGLMModel` | |
| `h2o.nlevels` | |
| `h2o.openLog` | | 
| `h2o.removeVecs` | |
| `h2o.rep_len` | | 
| `h2o.saveModel` | |
| `h2o.setLevel` | | 
| `h2o.setLevels` | |
| `h2o.setTimezone` | | 
| `h2o.shutdown` | |
| `h2o.startGLMJob` | | 
| `h2o.startLogging` | | 
| `h2o.stopLogging` | |
| `h2o.strsplit` | |
| `h2o.sub` | |
| `h2o.tolower` | | 
| `h2o.toupper` | | 
| `h2o.trim` | | 
| `isnumeric` | | 
| `ischaracter` | |
| `levels` | | 
| `nlevels` | | 
| `unique` || 
| `which` | | 

