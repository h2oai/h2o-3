#R/Python Parity

All capabilities available in the H2O R package are now available in Python. This document describes the supported capabilities and any differences in behavior. 

Based on https://0xdata.atlassian.net/browse/HEXDEV-30

Include desc. of function? 

transform, within, subset - not to be ported per Spencer in 1307


|R | Python  | Notes | 
|------------- |---------------| -------------|
| `%/%`      |   | Use the `//` operator in Python |
| `%x%`      |        | Use `my_frame.mult(other_frame)`.|
| `cummax` |         |            |
| `cummin`| |
| `cumprod` | | 
| `cumsum` | | 
| `transpose` | | 
| `round` | | 
| `signif` | | 
| `range` | | 
| `prod` | | 
| `any` || Not supported in Rapids (?) 
| `all` | | 
| `%in%`| | 
| `as.character` | | 
| `as.numeric`| | 
| `match`| | 
| `pop` | | Implemented? 
| `push` | | Implemented? 
| `table` | | 
| `anyFactor` | | 
| `subset` | | Will not be ported
| `transform`| | Will not be ported 
| `within` | | Will not be ported
| `scale` | | 
| `setLevel`|| 
| `rbind`| | 
| `as.Date`| | 
| `str`| | 
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

