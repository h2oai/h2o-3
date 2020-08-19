Naïve Bayes Classifier
----------------------

Introduction
~~~~~~~~~~~~

Naïve Bayes is a classification algorithm that relies on strong assumptions of the independence of covariates in applying Bayes Theorem. The Naïve Bayes classifier assumes independence between predictor variables conditional on the response, and a Gaussian distribution of numeric predictors with mean and standard deviation computed from the training dataset. 

Naïve Bayes models are commonly used as an alternative to decision trees for classification problems. When building a Naïve Bayes classifier, every row in the training dataset that contains at least one NA will be skipped completely. If the test dataset has missing values, then those predictors are omitted in the probability calculation during prediction.


Defining a Naïve Bayes Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the dependent variable. The data must be categorical and must contain at least two unique categorical levels.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for
   algorithm components dependent on randomization. The seed is
   consistent for each H2O instance so that you can create models with
   the same starting conditions in alternative configurations.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for ``nfold`` is specified and ``fold_column`` is not specified.) Specify the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

-  `keep_cross_validation_models <algo-params/keep_cross_validation_models.html>`__: Specify whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster. This option defaults to TRUE.

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Enable this option to keep the cross-validation predictions.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment. 

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option is enabled by default.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Specify whether to score during each iteration of the model training.

-  `balance_classes <algo-params/balance_classes.html>`__: Specify whether to oversample the minority classes to balance the class distribution. This option is not enabled by default and can increase the data frame size. This option is only applicable for classification. Majority classes can be undersampled to satisfy the **max_after_balance_size** parameter.

-  `class_sampling_factors <algo-params/class_sampling_factors.html>`__: Specify the per-class (in lexicographical order) over/under-sampling ratios. By default, these ratios are automatically computed during training to obtain the class balance. Note that this requires ``balance_classes=true``.

-  `max_after_balance_size <algo-params/max_after_balance_size.html>`__: Specify the maximum relative size of the training data after balancing class counts (**balance_classes** must be enabled). The value can be less than 1.0.

-  `laplace <algo-params/laplace.html>`__: Specify the Laplace smoothing parameter. The value must be an integer >= 0.

-  `min_sdev <algo-params/min_sdev.html>`__: Specify the minimum standard deviation to use for observations without enough data. The value must be at least 1e-10.

-  `eps_sdev <algo-params/eps_sdev.html>`__: Specify the threshold for standard deviation. The value must be positive. If this threshold is not met, the ``min_sdev`` value is used.

-  `min_prob <algo-params/min_prob.html>`__: Specify the minimum probability to use for observations without enough data.

-  `eps_prob <algo-params/eps_prob.html>`__: Cutoff below which probability is replaced with ``min_prob``.

-  `compute_metrics <algo-params/compute_metrics.html>`__: Enable this option to compute metrics on training data. 

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. Use 0 to disable.

-  `export_checkpoints_dir <algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will automatically be exported.

- `gainslift_bins <algo-params/gainslift_bins>`__: The number of bins for a Gains/Lift table. The default value is ``-1`` and makes the binning automatic. To disable this feature, set to ``0``.

Interpreting a Naïve Bayes Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The output from Naïve Bayes is a list of tables containing the a-priori and conditional probabilities of each class of the response. The a-priori probability is the estimated probability of a particular class before observing any of the predictors. Each conditional probability table corresponds to a predictor column. The row headers are the classes of the response and the column headers are the classes of the predictor. Thus, in the sample output below, the probability of survival (y) given a person is male (x) is 0.51617440.

::

                    Sex
    Survived       Male     Female
         No  0.91543624 0.08456376
         Yes 0.51617440 0.48382560

When the predictor is numeric, Naïve Bayes assumes it is sampled from a Gaussian distribution given the class of the response. The first column contains the mean and the second column contains the standard deviation of the distribution.

By default, the following output displays:

-  Output, including model category, model summary, scoring history, training metrics, and validation metrics
-  Y-Levels (levels of the response column)
-  A Priori response probabilities
-  P-conditionals

Examples
~~~~~~~~

Below is a simple example showing how to build a Naïve Bayes Classifier model.

.. tabs::
   .. code-tab:: r R

    # Import the prostate dataset into H2O:
    prostate <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")

    # Set the predictors and response; set the response as a factor:
    prostate$CAPSULE <- as.factor(prostate$CAPSULE)
    predictors <- c("ID", "AGE", "RACE", "DPROS" ,"DCAPS" ,"PSA", "VOL", "GLEASON")
    response <- "CAPSULE"

    # Build and train the model:
    pros_nb <- h2o.naiveBayes(x = predictors, 
                              y = response, 
                              training_frame = prostate, 
                              laplace = 0, 
                              nfolds = 5, 
                              seed = 1234)

    # Eval performance:
    perf <- h2o.performance(pros_nb)

    # Generate the predictions on a test set (if necessary):
    pred <- h2o.predict(pros_nb, newdata = prostate)
    


   .. code-tab:: python

    import h2o
    from h2o.estimators import H2ONaiveBayesEstimator
    h2o.init()

    # Import the prostate dataset into H2O:
    prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")

    # Set predictors and response; set the response as a factor:
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    predictors = ("ID","AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
    response = "CAPSULE"

    # Build and train the model:
    pros_nb = H2ONaiveBayesEstimator(laplace=0, 
                                     nfolds=5, 
                                     seed=1234)
    pros_nb.train(x=predictors, 
                  y=response, 
                  training_frame=prostate)

    # Eval performance:
    perf = pros_nb.model_performance()

    # Generate predictions on a test set (if necessary):
    pred = pros_nb.predict(prostate)


FAQ
~~~

-  **How does the algorithm handle missing values during training?**

  All rows with one or more missing values (either in the predictors or the response) will be skipped during model building.

-  **How does the algorithm handle missing values during testing?**

  If a predictor is missing, it will be skipped when taking the product of conditional probabilities in calculating the joint probability conditional on the response.

-  **What happens if the response domain is different in the training
   and test datasets?**

  The response column in the test dataset is not used during scoring, so any response categories absent in the training data will not be predicted.

-  **What happens when you try to predict on a categorical level not seen during training?**

 The conditional probability of that predictor level will be set according to the Laplace smoothing factor. If the Laplace smoothing parameter is disabled (``laplace = 0``), then Naive Bayes will predict a probability of 0 for any row in the test set that contains a previously unseen categorical level. However, if the Laplace smoothing parameter is used (e.g. ``laplace = 1``), then the model can make predictions for rows that include previously unseen categorical level.

 Laplace smoothing adjusts the maximum likelihood estimates by adding 1 to the numerator and :math:`k` to the denominator to allow for new categorical levels in the training set:

   :math:`\phi_{j|y=1}= \frac{\Sigma_{i=1}^m 1(x_{j}^{(i)} \ = \ 1 \ \bigcap y^{(i)} \ = \ 1) \ + \ 1}{\Sigma_{i=1}^{m}1(y^{(i)} \ = \ 1) \ + \ k}`

   :math:`\phi_{j|y=0}= \frac{\Sigma_{i=1}^m 1(x_{j}^{(i)} \ = \ 1 \ \bigcap y^{(i)} \ = \ 0) \ + \ 1}{\Sigma_{i \ = \ 1}^{m}1(y^{(i)} \ = \ 0) \ + \ k}`

 :math:`x^{(i)}` represents features, :math:`y^{(i)}` represents the response column, and :math:`k` represents the addition of each new categorical level. (:math:`k` functions to balance the added 1 in the numerator.)

 Laplace smoothing should be used with care; it is generally intended to allow for predictions in rare events. As prediction data becomes increasingly distinct from training data, new models should be trained when possible to account for a broader set of possible feature values.

-  **Does it matter if the data is sorted?**

  No.

-  **Should data be shuffled before training?**

  This does not affect model building.

-  **How does the algorithm handle highly imbalanced data in a response
   column?**

  Unbalanced data will not affect the model. However, if one response category has very few observations compared to the total, the conditional probability may be very low. A cutoff (``eps_prob``) and minimum value (``min_prob``) are available for the user to set a floor on the calculated probability.

-  **What if there are a large number of columns?**

  More memory will be allocated on each node to store the joint frequency counts and sums.

-  **What if there are a large number of categorical factor levels?**

  More memory will be allocated on each node to store the joint frequency count of each categorical predictor level with the response’s level.

-  **When running PCA, is it better to create a cluster that uses many
   smaller nodes or fewer larger nodes?**

  For Naïve Bayes, we recommend using many smaller nodes because the distributed task doesn't require intensive computation.

Naïve Bayes Algorithm
~~~~~~~~~~~~~~~~~~~~~

The algorithm is presented for the simplified binomial case without loss
of generality.

Under the Naive Bayes assumption of independence, given a training set for a set of discrete valued features X :math:`{(X^{(i)}, y^{(i)}; i=1,...m)}`

The joint likelihood of the data can be expressed as:

:math:`\mathcal{L}(\phi(y), \phi_{i|y=1}, \phi_{i|y=0})=\Pi_{i=1}^{m}p(X^{(i)},y^{(i)})`

The model can be parameterized by:

:math:`\phi_{i|y=0} = p(x_{i}=1|y=0); \phi_{i|y=1}= p(x_{i}=1|y=1);\phi(y)`

where :math:`\phi_{i|y=0}= p(x_{i}=1| y=0)` can be thought of as the fraction of the observed instances where feature :math:`x_{i}` is observed, and the outcome is :math:`y=0,\phi_{i|y=1}=p(x_{i}=1| y=1)` is the fraction of the observed instances where feature :math:`x_{i}` is observed, and the outcome is :math:`y=1`, and so on.

The objective of the algorithm is to maximize with respect to :math:`\phi_{i|y=0}`, :math:`\phi_{i|y=1}`, and :math:`\phi(y)` where the maximum likelihood estimates are:

 :math:`\phi_{j|y=1}=\frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 1)}{\Sigma_{i=1}^{m}(y^{(i)}=1)}`

 :math:`\phi\_{j|y=0}=\frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 0)}{\Sigma_{i=1}^{m}(y^{(i)}=0)}`

 :math:`\phi(y)=\frac{(y^{i} = 1)}{m}`

Once all parameters :math:`\phi_{j|y}` are fitted, the model can be used to predict new examples with features :math:`X_{(i^*)}`. This is carried out by calculating:

 :math:`p(y=1|x)=\frac{\Pi p(x_i|y=1) p(y=1)}{\Pi p(x_i|y=1)p(y=1) + \Pi p(x_i|y=0)p(y=0)}`

 :math:`p(y=0|x)=\frac{\Pi p(x_i|y=0) p(y=0)}{\Pi p(x_i|y=1)p(y=1) + \Pi p(x_i|y=0)p(y=0)}`

and then predicting the class with the highest probability.

It is possible that prediction sets contain features not originally seen in the training set. If this occurs, the maximum likelihood estimates for these features predict a probability of 0 for all cases of :math:`y`.

Laplace smoothing allows a model to predict on out of training data
features by adjusting the maximum likelihood estimates to be:

 :math:`\phi_{j|y=1}=\frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 1) + 1}{\Sigma_{i=1}^{m}(y^{(i)}=1 + 2}`)

 :math:`\phi_{j|y=0}=\frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 0) + 1}{\Sigma_{i=1}^{m}(y^{(i)}=0 + 2}`

Note that in the general case where :math:`y` takes on :math:`k` values, there are :math:`k+1` modified parameter estimates, and they are added in when the denominator is :math:`k` (rather than 2, as shown in the two-level classifier shown here).

Laplace smoothing should be used with care; it is generally intended to allow for predictions in rare events. As prediction data becomes increasingly distinct from training data, train new models when possible to account for a broader set of possible X values.

References
~~~~~~~~~~

`Hastie, Trevor, Robert Tibshirani, and J Jerome H Friedman. The
Elements of Statistical Learning. Vol.1. N.p., Springer New York,
2001. <http://www.stanford.edu/~hastie/local.ftp/Springer/OLD//ESLII_print4.pdf>`__

`Ng, Andrew. "Generative Learning algorithms."
(2008). <http://cs229.stanford.edu/notes/cs229-notes2.pdf>`__
