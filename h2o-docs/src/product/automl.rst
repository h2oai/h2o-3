AutoML: Automatic Machine Learning
==================================

In recent years, the demand for machine learning experts has outpaced the supply, despite the surge of people entering the field.  To address this gap, there have been big strides in the development of user-friendly machine learning software that can be used by non-experts.  The first steps toward simplifying machine learning involved developing simple, unified interfaces to a variety of machine learning algorithms, like is provided by H2O.  

Although H2O has made it easy for non-experts to experiment with machine learning, there is still a fair bit of knowledge and background in data science that is required to produce high-performing machine learning models.  Deep Neural Networks in particular are notoriously difficult for a non-expert to tune properly.  In order for machine learning software to truly be accessible to non-experts, such systems must be able to automatically perform proper data pre-processing steps and return a highly optimized machine learning model.

H2O's AutoML can be used for automating the machine learning workflow, which includes automatic training and tuning of many models within a user-specified time-limit.  The user can also specify which model performance metric that they'd like to optimize and use a metric-based stopping criterion for the AutoML process rather than a specific time constraint.  Stacked ensembles will automatically trained on subset of the individual models to produce a highly predictive ensemble model, although this can be turned off if the user prefers to return singleton models only.  Stacked ensembles are not yet available for multiclass classification problems, so in that case, only singleton models will be trained. 

AutoML Interface
----------------

The AutoML interface is designed to have as few parameters as possible so that all the user needs to do is point to their dataset, identify the response column and optionally specify a time-constraint.  

Example in R
~~~~~~~~~~~~

Here’s an R code example showing basic usage of the ``h2o.automl()`` function:

::

    library(h2o)
    h2o.init()

    df <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
    df$CAPSULE <- as.factor(df$CAPSULE)
    aml <- h2o.automl(x = 3:8, y = 2, training_frame = df)

    # View the AutoML Leaderboard
    print(aml@leaderboard)


AutoML Output
-------------

The AutoML object includes a history of all the data-processing and modeling steps that were taken, and will return a "leaderboard" of all the models that were trained in the process, ranked by a user's model performance metric of choice.
