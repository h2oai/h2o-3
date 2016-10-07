Splitting
^^^^^^^^^

- **Does the algo stop splitting when all the possible splits lead to worse error measures?**

 It does if you use ``min_split_improvement`` (min_split_improvement turned ON by default (0.00001).) When properly tuned, this option can help reduce overfitting. 

- **When does the algo stop splitting on an internal node?**

 A single tree will stop splitting when there are no more splits that satisfy the minimum rows parameter, if it reaches ``max_depth``, or if there are no splits that satisfy the ``min_split_improvement`` parameter.

- **How does the minimum rows parameter work?**

 ``min_rows`` specifies the minimum number of observations for a leaf. If a user specifies ``min_rows = 500``, and they still have 500 TRUEs and 400 FALSEs, we won't split because we need 500 on both sides. The default for ``min_rows` is 10, so ``min_rows`` rarely affects the GBM splits because GBMs are typically shallow, but the concept still applies.

- **How does GBM decide which feature to split on?**

 It splits on the column and level that results in the greatest reduction in residual sum of the squares (RSS) in the subtree at that point. It considers all fields available from the algorithm. Note that any use of column sampling and row sampling will cause each decision to not consider all data points, and that this is on purpose to generate more robust trees. To find the best level, the histogram binning process is used to quickly compute the potential MSE of each possible split. The number of bins is controlled via ``nbins_cats`` for categoricals, the pair of ``nbins`` (the number of bins for the histogram to build, then split at the best point), and ``nbins_top_level`` (the minimum number of bins at the root level to use to build the histogram). This number will then be decreased by a factor of two per level. 

 For ``nbins_top_level``, higher = more precise, but potentially more prone to overfitting. Higher also takes more memory and possibly longer to run.

- **What is the difference between nbins and nbins_top_level?**

 ``nbins`` and ``nbins_top_level`` are both for numerics (real and integer). ``nbins_top_level`` is the number of bins GBM uses at the top of each tree. It then divides by 2 at each ensuing level to find a new number. ``nbins`` controls when GBM stops dividing by 2.

- **Doesn't GBM do the same thing as RF for col_sample_rate < 1 ?**

 Yes for splitting, there is no difference between RF and GBM. They both use the same tree splitting.
