Admissible Machine Learning
===========================

We introduce some new concepts and tools to aid the design of *admissible learning algorithms* that are efficient (enjoy good predictive accuracy), fair (minimize discrimination against minority groups), and interpretable (provide mechanistic understanding) to the best possible extent.

Admissible ML introduces two methodological tools: Infogram and L-features. 

- Infogram ("information diagram") is a new graphical feature-exploration method to facilitate the development of admissible machine learning methods. 
- In order to mitigate unfairness, we introduce the concept of L-features, which offers ways to systematically discover the hidden problematic proxy features from a dataset. 


Infogram
--------

The infogram is a graphical information-theoretic interpretability tool which allows the user to quickly spot the core, decision-making variables that uniquely and safely drive the response, in supervised classification problems. The infogram can significantly cut down the number of predictors needed to build a model by identifying only the most valuable, admissible features. When protected variables such as race or gender are present in the data, the admissibility of a variable is determined by a safety and relevancy index, and thus serves as a diagnostic tool for fairness. The safety of each feature can be quantified and variables that are unsafe will be considered inadmissible. Models built using only admissible features will naturally be more interpretable, given the reduced feature set. Admissible models are also less susceptible to overfitting and train faster, while providing similar accuracy as models built using all available features.

Core Infogram
~~~~~~~~~~~~~

The infogram is an information-theoretic graphical tool which allows the user to quickly spot the "core" decision-making variables that are driving the response. One of the advantages of this method is that it works even in the presence of correlated features. Identifying the Core Set is a much more difficult undertaking than merely selecting the most predictive features.   There are additional benefits as well: Machine learning models based on "core" feautues show improved stability, especially when there exists considerable correlation among the features.

The Core Infogram plots all the variables as points on two-dimensional grid of total vs net information. The x-axis is total information, a measure of how much the variable drives the response (the more predictive, the higher the total information). The y-axis is net information, a measure of how unique the variable is. The top right quadrant of the infogram plot is the admissible section; the variables located in this quadrant are the admissible features. In the Core Infogram, the admissible features are the strongest, unique drivers of the response.

Fair Infogram: A Diagnostic Tool for Fairness
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The goal of this tool is to assist identification of admissible features which have little or no information-overlap with sensitive attributes, yet are reasonably predictive for the response.

TO DO: Fix/finish this

Safety-index and Inadmissibility. Define the safety-index for variable Xj as
Fj “ MI`Y,Xj | tS1,...,Squ ̆ (3.5)
This quantifies how much extra information Xj carries for Y that is not acquired through the sensitive variables S “ pS1, . . . , Sqq.


Infogram Interface
------------------

The interface is designed to be simple and aligned with the standard modeling interface in H2O.  If you provide a list of protected features with ``protected_columns``, it will produce a Fair Infogram instead of a Core Infogram.  The infogram object is a data object which also contains the plot, and the plot can be displayed using `plot()` on the infogram object.


.. tabs::
   .. code-tab:: r R

        # Generate and plot the infogram
        ig <- h2o.infogram(x = x, y = y, training_frame = train)
        plot(ig)

   .. code-tab:: python

        # Generate and plot the infogram
        ig = H2OInfoGram()
        ig.train(y = y, training_frame = train)
        ig.plot()


Parameters
~~~~~~~~~~

The infogram function follows the standard modeling interface in H2O, where the user specifies the following data variables: ``x, y, training_frame, validation_frame``.  In addition to the standard set of arguments, the infogram features several new, arguments, which are all optional:

- **protected_columns**: Columns that contain features that are sensitive and need to be protected (legally, or otherwise).  These features (e.g. race, gender, etc) should not drive the prediction of the response.

- **algorithm**: Machine learning algorithm used to build the infogram. Options include:

 - ``"AUTO"`` (GBM). This is the default.
 - ``"deeplearning"`` (Deep Learning with default parameters)
 - ``"drf"`` (Random Forest with default parameters)
 - ``"gbm"`` (GBM with default parameters) 
 - ``"glm"`` (GLM with default parameters)
 - ``"xgboost"`` (if available, XGBoost with default parameters)

-  **algorithm_params**: (Optional) With ``algorithm``, you can also specify a list of customized parameters for that algorithm.  For example if we use a GBM, for example, we can specify ``list(max_depth = 10)`` in R and ``{'max_depth': 10}`` in Python.

- **net_information_threshold**: A number between 0 and 1 representing a threshold for net information, defaulting to 0.1.  For a specific feature, if the net information is higher than this threshold, and the corresponding total information is also higher than the ``total_ information_threshold``, that feature will be considered admissible.  The net information is the y-axis of the Core Infogram.

- **total_information_threshold**: A number between 0 and 1 representing a threshold for total information, defaulting to 0.1.  For a specific feature, if the total information is higher than this threshold, and the corresponding net information is also higher than the threshold ``net_information_threshold``, that feature will be considered admissible. The total information is the x-axis of the Core Infogram.

- **safety_index_threshold**: A number between 0 and 1 representing a threshold for the safety index, defaulting to 0.1.  This is only used when ``protected_columns`` is set by the user.  For a specific feature, if the safety index value is higher than this threshold, and the corresponding relevance index is also higher than the ``relevance_index_threshold``, that feature will be considered admissible.  The safety index is the y-axis of the Fair Infogram.

- **relevance_index_threshold**: A number between 0 and 1 representing a threshold for the relevance index, defaulting to 0.1.  This is only used when ``protected_columns`` is set by the user.  For a specific feature, if the relevance index value is higher than this threshold, and the corresponding safety index is also higher than the ``safety_index_threshold``, that feature will be considered admissible.  The relevance index is the x-axis of the Fair Infogram.

- **data_fraction**: The fraction of training frame to use to build the infogram model. Defaults to 1.0, and any value between 0 and 1.0 is acceptable.

- **top_n_features**: An integer specifying the number of columns to evaluate in the infogram.  The columns are ranked by variable importance, and the top N are evaluated.  Defaults to 50.


Infogram Output
---------------

Infogram Plot
~~~~~~~~~~~~~

The infogram function produces a visual guide to admisibility of the features.  The visualization engine used in the R interface is the `ggplot2 <https://ggplot2.tidyverse.org/>`__ package and in Python, we use `matplotlib <https://matplotlib.org/>`__.  Here's an example of the Core Infogram for the iris dataset.

.. figure:: images/infogram_core_iris.png
   :alt: H2O Core Infogram
   :scale: 80%
   :align: center


Infogram Data 
~~~~~~~~~~~~~

The infogram function produces and object of type ``H2OInfogram``, which contains several data elements and the plot object.  The most important objects are the following:

- ``admissible_features``: A list of the admissible features.
- ``admissible_score``:  A data.frame storing the admissibility data for each feature, where the rows are the features considered (this will max out at 50 rows/features if ``top_n_features`` is set to the default.  The "admissible index" is the length between the origin and the (x, y) feature location on the infogram plot, normalized to 1.0.  The features are sorted by admissible index value, with the most admissible features at the top of the table, for easy access.  There's a binary indicator column which specifies which features are considered "admissible", given the threshold values.



Code Examples
-------------

The R and Python code below is the quickest way to get started.  

Here's an example showing basic usage of the ``h2o.infogram()`` function in *R* and the ``H2OInfogram()`` method in *Python*.  Keep in mind that this code should be run in an environment that can support plots. 

This example below uses a `UCI Credit <https://archive.ics.uci.edu/ml/datasets/default+of+credit+card+clients>`__ from the UCI Machine Learning Repository.  It has 30k rows, representing customers, and 24 predictor variables, including several common `protected <https://www.consumerfinance.gov/fair-lending/>`__ attributes such as sex, age, and marital status.  This is a binary classification problem, aimed to estimate the probabilty of default in order to identify "credible or not credible" customers.

Along with the demographic variables that are included in this dataset, there's a number of payment history variables, including previous bill and payment amounts.  On the surface, you may assume that payment history is not correlated with protected variables, but as we will see in the example below, most of the payment history variables provide a hidden pathway through the protected variables to the response.  Therefore, even if you remove the protected variables during training, the resulting model will still be desicrimatory if any non-admissible bill/payment variables are included.  This is Example 9 from the `Admissble ML <https://arxiv.org/abs/2108.07380>`__ paper.


.. tabs::
   .. code-tab:: r R

        library(h2o)

        h2o.init()
                
        # Import credit dataset
        f <- "https://erin-data.s3.amazonaws.com/admissible/data/taiwan_credit_card_uci.csv"
        col_types <- list(by.col.name = c("SEX", "MARRIAGE", "default_payment_next_month"), 
                          types = c("factor", "factor", "factor"))
        train <- h2o.importFile(path = f, col.types = col_types)

        # Response column and predictor columns
        y <- "default_payment_next_month"
        x <- setdiff(names(train), y)

        # Protected attributes
        pcols <- c("SEX", "MARRIAGE", "AGE")

        # Infogram
        ig <- h2o.infogram(y = y, training_frame = train, protected_columns = pcols)
        plot(ig)

   .. code-tab:: python

        import h2o
        from h2o.estimators.infogram import H2OInfogram

        h2o.init()

        # Import credit dataset
        f = "https://erin-data.s3.amazonaws.com/admissible/data/taiwan_credit_card_uci.csv"
        col_types = {'SEX': "enum", 'MARRIAGE': "enum", 'default_payment_next_month': "enum"}
        train = h2o.import_file(path = f, col_types = col_types)

        # Response column and predictor columns
        y = "default_payment_next_month"
        x = train.columns
        x.remove(y)

        # Protected attributes
        pcols = ["SEX", "MARRIAGE", "AGE"]        

        # Infogram
        ig = H2OInfogram()
        ig.train(y=y, x=x, training_frame=train, protected_columns=pcols)
        ig.plot()


Here's the infogram which shows that ``PAY_0`` and ``PAY_2`` are the only admissible attributes.  Most of the bill or payment features are either redundant or redudant and unsafe.

.. figure:: images/infogram_fair_credit.png
   :alt: H2O Fair Infogram
   :scale: 80%
   :align: center


Glossary
--------

- **Admissible Machine Learning**: Admissible machine learning is a new technology that can balance fairness, interpretability, and accuracy. 
- **Protected Features**:  User-defined features that are sensitive and need to be protected (legally, or otherwise).  These features (e.g. race, gender, etc) should not drive the prediction of the response.
- **Core Features or Core Set**: Key features that are driving the response, without redundancy.  High relevance, low redundancy. 
- **Irrelevant Features**: Features on the vertical side of the L. Core infogram only
mentary set comprises of the desired, admissible features.
- **Redundant Features**: Features n the horizontal side of the L.
- **Safety-index**:  This quantifies how much extra information `X_j` carries for `Y` that is not acquired through the sensitive variables.
- **Relevance-index**: TO DO
- **Admissible Features**: The set of features that are found to acceptable to use (high on the safety index). 
- **Inadmissible Features (L-Features)**: The highlighted L-shaped area in the Infogram contains features that are either irrelevant or redundant. These are variables with small F-values (F-stands for fairness) will be called inadmissible, as they possess little or no informational value beyond their use as a dummy for protected characteristics. 




References
----------

Subhadeep Mukhopadhyay. *InfoGram and Admissible Machine Learning*, August 2021. `arXiv URL <https://arxiv.org/abs/2108.07380>`__.



