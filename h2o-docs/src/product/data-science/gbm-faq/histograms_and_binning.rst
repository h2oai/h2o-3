Histograms and Binning
^^^^^^^^^^^^^^^^^^^^^^

- **Does H2O's GBM create histograms for each feature before it starts splitting, or, for each feature, does it create histograms at each node, using only the samples that land within each node?**

 GBM’s histograms have to use the node data, because histograms determine where to split. For each feature, GBM creates histograms for each node; GBM does not create histograms for each feature before splitting.  

 If you have few observations in a node (but greater than 10), and nbins is set to 20 (the default), empty bins will be created  if there aren't enough observations to go in each bin.

- **How does one-hot encoding compare to binning and lexicographic ordering, which GBM currently uses?**

 Lexicographic order is an implementation detail (which could change) and can be seen as a poor man's way of grouping categories. If ``nbins_cats`` is large enough, the histograms can fully resolve all levels and a split will be able to send every factor level to the optimal direction. For one-hot encoding, the number of columns increases and column sampling could end up selecting a subset of factor levels for finding the split decision (if and only if animal=cat, go left, otherwise go right). In the binning approach, once the categorical column is picked, every level is included in the split (if cat or dog or mouse, go left, all others go right). So the binning approach can be more accurate in fitting, but can also lead to overfitting, that's why tuning ``nbins_cats`` is very important.

- **How does binning work for categorical variables?**

 **Note**: ``nbins_cats`` is the parameter to control categorical binning. See note above for definition.

 Factors (e.g., “dog”, “cat”, “mouse” ) are mapped in lexicographic order to name lookup array indices (e.g., “cat” -> 0, “dog” -> 1, “mouse” -> 2). We store categoricals as integers, and keep the lookup array of Strings around. Noted: This is an implementation detail, the string to integer mapping can be anything, as long as it is consistent. The implementation only matters once there are more categories than nbins_cats (cat,dog -> 0, mouse,rat -> 1, etc), which usually only happens at the tree’s first few levels.

 In the Java code, categorical splits are encoded via bitsets, so at a given split point, we will send a certain subset of bins left, and the rest right. If the 3rd and the 5th bin are supposed to go left, then a suitable bitset might look like this: 0010100101010001…01011 (up to nbins_cats long). These “optimal” splits are found by sorting the bins by their response, finding the optimal split point in the sorted bins, assigning one part to go left and the other part to go right, and then reverting the sort order. Note that these bitsets are encoded as byte arrays in the POJO, and each byte is printed as a integer in -128..127

 Some ``nbins_cats`` specifics:

 - If there are fewer than nbins_cats categorical levels, then each unique level has its own bin and the method can do a perfect split in one shot.

 - If there are more than nbins_cats categorical levels, then they are grouped in lexicographic order, and [“A”,”B”,”C”],[“D”,”E”,”F”],…,[“X”,”Y”,”Z”], for example, and “A”,”B” and “C” go the same direction in that split, and can only be split further down the tree (or never).

- **What should I do if my variables are long skewed in the tail and might have large outliers?**

 You can try adding a new predictor column which is either pre-binned (e.g. as a categorical - “small”, “median”, and “giant” values), or a log-transform - plus keep the old column.

- **How does binning work for real/integer columns?**

 Using the default (equal-width bins), the equal-width histograms can have difficulties cutting off extreme outliers. If for example, for an integer column, only values of -9999,0,1,2,3,4 occur, then a histogram with 1024 nbins (e.g., default value of nbins_top_level) will have a bin width of ~10, and the resulting bins will all be empty except for the first and the last: [-9999],[],[],[],[],…,[],[0,1,2,3,4]. The squared-error based split finding might then not necessarily separate these two ends unless this column’s reduction in squared error is large enough, compared to all other columns that could be split on. In the extreme case where rows with -9999 column value (uniformly occurring missing value encoded as -9999) have the same mean response as the mean response of all rows with column values of 0,1,2,3,4, then there won’t be a split ever. In practice, outliers do get split off eventually, but we are considering using quantile-based histograms in the future.

- **How does QuantilesGlobal binning differ from UniformAdaptive binning?**

 QuantilesGlobal computes ``nbins`` quantiles for each numeric (non-binary (should this be non-categorical instead of binary, because we can do more than two levels)) column, then refines/pads each bucket (between two quantiles) uniformly (and randomly for remainders) into a total of ``nbins_top_level`` bins. This set of split points is then used for all levels of the tree: each leaf node histogram gets min/max-range adjusted (based on its population range) and also linearly refined/padded to end up with exactly ``nbins`` (level) bins to pick the best split from. For integer columns where this ends up with more than the unique number of distinct values, we fall back to the pure-integer buckets.

 UniformAdaptive builds equal-width histograms, with adaptive ranges based on the content of each column’s data in the node to be split.

- **How is each level created?**

 We build exact histograms for each node and each considered column at each level of the tree. We avoid sorting (and data shuffling) by using histograms, but we pass over all the data to build those histograms. During each pass, H2O analyzes the tree level and decides how to build the next level.
