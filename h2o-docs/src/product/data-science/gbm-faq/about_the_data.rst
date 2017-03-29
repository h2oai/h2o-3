About the Data
^^^^^^^^^^^^^^

- **How does the algorithm handle highly imbalanced data in a response column?**

 The GBM algorithm is quite good at handling highly imbalanced data because it's simply a partitioning scheme. Use the weights column for per-row weights if you want to control over/under-sampling. When your dataset includes imbalanced data, you can also specify ``balance_classes``, ``class_sampling_factors``, ``sample_rate_per_class``, and ``max_after_balance_size`` to control over/under-sampling.

- **What if there are a large number of columns in the dataset?**

 GBM models are best for datasets with fewer than a few thousand columns.

- **What if there are a large number of categorical factor levels in the dataset?**

 Large numbers of categoricals are handled very efficiently - there is never any one-hot encoding.

- **What distribution can I use for my dataset?** 

 Distribution options include the following:

 - AUTO
 - Bernoulli: the response column can be numeric or categorical
 - Multinomial: the response column must be categorical.
 - Gaussian: the response column must be numeric
 - Poisson: the response column must be numeric
 - Gamma: the response column must be numeric
 - Tweedie: the response column must be numeric 
 - Laplace: the response column must be numeric
 - Quantile: the response column must be numeric
 - Huber: the response column must be numeric

 Refer to the `distribution <../algo-params/distribution.html>`__ parameter in the Appendix for more information about the ``distribution`` options. 

.. _lossfunction:

- **What loss function is automatically chosen for each of these distributions?**

 By default, the residual deviance is minimized, which is the natural loss for Poisson, Gamma, Tweedie, Huber, etc. For Laplace, the residual deviance is the same as absolute error. For Gaussian, it's the same as squared error.