About the Data
^^^^^^^^^^^^^^

- **How does the algorithm handle highly imbalanced data in a response column?**

 - Use the weights column for per-row weights if you want control.
 - First, the GBM algorithm is quite good at handling highly imbalanced data because it's simply a partitioning scheme.
 - Second, we have noticed that our way of recalibrating probabilities after using balanced classes tends to underperform. (Probabilities wind up brought too far into the mean.) If nothing else, we can specify how that works a bit. A better option would be to indicate what it is useful for (not necessarily better calibrated probabilities, but usually a higher recall).

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

- **What loss function is automatically chosen for each of these distributions?**

 By default, the residual deviance is minimized, which is the natural loss for Poisson, Gamma, Tweedie, Huber, etc. For Laplace, the residual deviance is the same as absolute error. For Gaussian, it's the same as squared error.