.. _Data_Science:


Algorithms
==========

This section provides an overview of each algorithm available in H2O-3. For detailed information about the parameters that can be used for building models, refer to `Appendix A - Parameters <parameters.html>`__.

Data types
----------

.. toctree::
   :maxdepth: 1

   data-science/supported-data-types

Common
------

.. toctree::
   :maxdepth: 1

   data-science/quantiles
   data-science/early_stopping

Supervised
----------

In supervised learning, the dataset is labeled with the answer that the chosen algorithm should come up with. Supervised learning takes input variables (x) along with an output variable (y). The output variable represents the column that you want to predict on. The algorithm then uses these variables to learn and approximate the mapping function from the input to the output. Supervised learning algorithms support classification and regression problems.

H2O-3 supports the following supervised algorithms:

.. toctree::
   :maxdepth: 1

   automl
   data-science/coxph
   data-science/deep-learning
   data-science/drf
   data-science/glm
   data-science/isotonic-regression
   data-science/model_selection
   data-science/gam
   data-science/anova_glm
   data-science/gbm
   data-science/naive-bayes
   data-science/rulefit
   data-science/decision-tree
   data-science/adaboost
   data-science/stacked-ensembles
   data-science/svm
   data-science/upliftdrf
   data-science/xgboost

Unsupervised
------------

In unsupervised learning, the model is provided with a dataset that isn't labeled (i.e. without an explicit outcome that the algorithm should return). In this case, the algorithm attempts to find patterns and structure in the data by extracting useful features. The model organizes the data in different ways, depending on the algorithm (clustering, anomaly detection, autoencoders, etc). 

H2O-3 supports the following unsupervised algorithms:

.. toctree::
   :maxdepth: 1

   data-science/aggregator
   data-science/glrm
   data-science/if
   data-science/eif
   data-science/k-means
   data-science/pca

Miscellaneous
-------------

.. toctree::
   :maxdepth: 1

   data-science/target-encoding
   data-science/tf-idf
   data-science/word2vec
   data-science/permutation-variable-importance
