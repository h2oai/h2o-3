Tuning a GBM
^^^^^^^^^^^^

- **When fitting a random number between 0 and 1 as a single feature, the training ROC curve is consistent with “random” for low tree numbers and overfits as the number of trees is increased, as expected. However, when a random number is included as part of a set of hundreds of features, as the number of trees increases, the random number increases in feature importance.**

 You can now use ``min_split_improvement`` to prevent overfitting on noise. However, if you are using a verion of H2O that does not include this parameter, then this is a known behavior of GBM that is similar to its behavior in R. If, for example, it takes 50 trees to learn all there is to learn from a frame without the random features, when you add a random predictor and train 1000 trees, the first 50 trees will be approximately the same. The final 950 trees are used to make sense of the random number, which will take a long time since there’s no structure. The variable importance will reflect the fact that all the splits from the first 950 trees are devoted to the random feature.

 The boruta algorithm is popular for feature selection and it makes use of random variables to select those features which consistently outperform random permutations of the original data. GBM is somewhat robust to random features since it is an iterative algorithm and will rescore the same data points over and over, particularly when used with row sampling. Future iterations can counteract a poor split decision. The behavior or random variables has been observed to avoid bad splits on other features.

- **How is column sampling implemented for GBM?**

 For an example model using:
 
  - 100-column dataset
  - ``col_sample_rate_per_tree=0.754``
  - ``col_sample_rate=0.8`` (Refers to available columns after per-tree sampling)

 For each tree, the floor is used to determine the number - in this example, (0.754 * 100)=75 out of the 100 - of columns that are randomly picked, and then the floor is used to determine the number - in this case, (0.754 * 0.8 * 100)=60 - of columns that are then randomly chosen for each split decision (out of the 75).

- **Which parameters can be used for tuning?**

  - ``offset_column``
  - ``weights_column``
  - ``nbins_top_level``
  - ``nbins_cats``
  - ``nbins``
  - ``ntrees``
  - ``max_depth``
  - ``min_rows``
  - ``learn_rate``
  - ``r2_stopping``
  - ``stopping_rounds``
  - ``stopping_metric``
  - ``stopping_tolerance``
  - ``max_runtime_secs``
  - ``learn_rate_annealing``
  - ``quantile_alpha``
  - ``tweedie_power``
  - ``huber_alpha``

- **How can I perform grid search with GBM?**

 GBM tuning tutorials for R, Python, and Flow are avialable `here <https://github.com/h2oai/h2o-3/tree/master/h2o-docs/src/product/tutorials/gbm>`__. 

- **Which parameters are used for early stopping?**

 ``stopping_rounds``, ``stopping_tolerance``, and ``stopping_metric``. The simplest way to turn on early stopping is to use a number >=1 in stopping rounds. The defaults for the other two will work fairly well, but a ``stopping_tolerance`` of 0 is a common alternative to the default.

 Additionally, take ``score_tree_interval`` into account (or ``score_each_iteration``). The stopping rounds applies to the number of scoring iterations H2O has performed, so regular scoring iterations of small size help control early stopping the most (though there is a speed tradeoff to scoring more often). The default is to use H2O’s assessment of a reasonable ratio of training time to scoring time, which often results in inconsistent scoring gaps.

 ``max_runtime_secs`` is another available parameter to stop tree building early. It will use a clock time rather than validation data to determine the time, so the fit may not have approached the best possible model.

 ``r2_stopping`` is also available to specify to control the maximum r^2 value of the fit, but this is rarely useful in practice.

- **Which parameters work together?**

 Most importantly, ``learn_rate`` and ``ntrees``. The lower the learning rate, the more trees you will require to achieve the same level of fit as if you had used a higher learning rate; however, this helps avoid overfitting. (Generally, set the learning rate as low as you can.)
