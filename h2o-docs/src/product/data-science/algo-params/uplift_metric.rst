``uplift_metric``
-----------------

- Available in: Uplift DRF
- Hyperparameter: no


Description
~~~~~~~~~~~

Use this option to specify an uplift metric. 

Aggregated data (class distributions) from data histograms are used to calculate ``uplift_metric``.

The goal is to maximize the differences between class distributions in the treatment and control sets, so the splitting criteria are based on distribution divergences. Based on the ``uplift_metric`` parameter, the distribution divergence is calculated. In H2O-3, three ``uplift_metric`` types are supported:

- **Kullback-Leibler divergence** (``uplift_metric="KL"``) - uses logarithms to calculate divergence, asymmetric, widely used, tends to infinity values (if treatment or control group distributions contain zero values). :math:`KL(P, Q) = \sum_{{i=0}^{N} p_i \log{\frac{p_i}{q_i}} }`
- **Squared Euclidean distance** (``uplift_metric="euclidean"``) - symmetric and stable distribution (does not tend to infinity values). :math:`E(P, Q) = \sum_{i=0}^{N} \sqrt{p_i-q_i}`
- **Chi-squared divergence** (``uplift_metric="chi_squared"``) - Euclidean divergence normalized by control group distribution. Asymmetric and also tends to infinity values (if control group distribution contains zero values). :math:`\sqrt{X}(P, Q) = \sum_{i=0}^{N} \frac{\sqrt{p_i-q_i}}{q_i}`

where:

- :math:`P` is treatment class distribution
- :math:`Q` is control class distribution

In a tree node, the result value for the split is the sum :math:`metric(P, Q) + metric(1-P, 1-Q)`. For the split gain value, the result within the node is normalized using the Qini coefficient (Eclidean or ChiSquared) or entropy (KL) for each distribution before and after the split.

The default value is the Kullback-Leibler metric (``uplift_metric="KL"``).

Related Parameters
~~~~~~~~~~~~~~~~~~

- `treatment_column <treatment_column.html>`__
- `auuc_type <auuc_type.html>`__
- `auuc_nbins<auuc_nbins.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the uplift dataset into H2O:
    data <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")

    # Set the predictors, response and treatment column:
    # set the predictors
    predictors <- c("f1", "f2", "f3", "f4", "f5", "f6","f7", "f8") 
    # set the response as a factor
    data$conversion <- as.factor(data$conversion)
    # set the treatment column as a factor
    data$treatment <- as.factor(data$treatment)

    # Split the dataset into a train and valid set:
    data_split <- h2o.splitFrame(data = data, ratios = 0.8, seed = 1234)
    train <- data_split[[1]]
    valid <- data_split[[2]]

    # Build and train the model:
    uplift.model <- h2o.upliftRandomForest(training_frame = train,
                                           validation_frame=valid,               
                                           x=predictors,
                                           y="conversion",
                                           ntrees=10,
                                           max_depth=5,
                                           treatment_column="treatment",
                                           uplift_metric="KL",
                                           min_rows=10,
                                           seed=1234,
                                           auuc_type="qini")
    # Eval performance:
    perf <- h2o.performance(uplift.model)

    # Generate predictions on a validation set (if necessary):
    predict <- h2o.predict(uplift.model, newdata = valid)

   .. code-tab:: python
   
    import h2o
    from h2o.estimators import H2OUpliftRandomForestEstimator
    h2o.init()

    # Import the cars dataset into H2O:
    data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")

    # Set the predictors, response and treatment column:
    predictors = ["f1", "f2", "f3", "f4", "f5", "f6","f7", "f8"]
    # set the response as a factor
    response = "conversion"
    data[response] = data[response].asfactor()
    # set the treatment as a factor
    treatment_column = "treatment"
    data[treatment_column] = data[treatment_column].asfactor()

    # Split the dataset into a train and valid set:
    train, valid = data.split_frame(ratios=[.8], seed=1234)

    # Build and train the model:
    uplift_model = H2OUpliftRandomForestEstimator(ntrees=10,
                                                  max_depth=5,
                                                  treatment_column=treatment_column,
                                                  uplift_metric="KL",
                                                  min_rows=10,
                                                  seed=1234,
                                                  auuc_type="qini")
    uplift_model.train(x=predictors, 
                       y=response, 
                       training_frame=train, 
                       validation_frame=valid)

    # Eval performance:
    perf = uplift_model.model_performance()

    # Generate predictions on a validation set (if necessary):
    pred = uplift_model.predict(valid)
