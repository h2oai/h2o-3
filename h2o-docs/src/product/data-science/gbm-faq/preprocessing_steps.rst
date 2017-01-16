Preprocessing Steps
^^^^^^^^^^^^^^^^^^^

- **What data preprocessing happens before GBM begins building trees/creates its first split?**

 The per-column summaries of the parsed H2O Frame contain the column types, in particular, the number of factors for categorical columns, and the min/max values for numeric columns.

- **Does the algorithm perform any sorting (does quantile binning require sorting-based method or an iterative projection method?) or shuffling beforehand?**

 No, the algorithm currently does not take the feature distribution into account, and the user has to specify the treatment of highly skewed features. With histogram_type = “quantilesGlobal”, the feature distribution is taken into account with a quantile-based binning (where buckets have equal population). By default, with histogram_type = "uniformAdaptive", each feature is binned into buckets of equal step size (not population). This is the fastest method, and usually performs well, but can lead to less accurate splits if the distribution is highly skewed.

- **Does the algorithm determine the distribution and percentiles of each feature beforehand?**

 No, the algorithm does not need to sort any data, everything is iterative and scales to extremely large datasets without modification.  Also, the algorithm does not depend on the sort order of the data and shuffling is not required.  Sorting-based methods can be faster for small datasets, and we are aware that the scalable approach of H2O might not be the fastest method for small datasets.

- **Do I need to encode categorical variables?**

 No, H2O automatically hands categorical feature levels for you.

- **Are the categoricals encoded as factors before the tree building begins?**

 Yes, this is how the H2O Frame is parsed, it internally stores the factors as integers and the column has a mapping from integers to strings.

 **Note**: Missing and categorical data are handled automatically without requiring any preprocessing from the user.

- **Does it matter if the data is sorted?**

 No

- **Should data be shuffled before training?**

 No