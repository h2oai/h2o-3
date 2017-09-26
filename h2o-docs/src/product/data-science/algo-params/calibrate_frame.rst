``calibrate_frame``
-------------------

- Available in: GBM, DRF
- Hyperparameter: no

Description
~~~~~~~~~~~

The ``calibrate_frame`` specifies the calibration frame that will be used for Platt scaling. This option is required if `calibrate_model <calibrate_model.html>`__ is enabled. 

`Platt scaling <https://en.wikipedia.org/wiki/Platt_scaling>`__ transforms the output of a classification model into a probability distribution over classes. It works by fitting a logistic regression model to a classifier's scores. Platt scaling will generally not affect the ranking of observations. Logloss, however, will generally improve with Platt scaling.

Refer to the following for more information about Platt scaling:

- `Calibrating Classifier Probabilities <http://danielnee.com/tag/platt-scaling/>`__
- `Predicting Good Probabilities with Supervised Learning <http://www.datascienceassn.org/sites/default/files/Predicting%20good%20probabilities%20with%20supervised%20learning.pdf>`__

Related Parameters
~~~~~~~~~~~~~~~~~~

- `calibrate_model <calibrate_model.html>`__


Examples
~~~~~~~~

.. example-code::
   .. code-block:: r

    library(h2o)
    h2o.init()

    # Import the ecology dataset
    ecology.hex <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/ecology_model.csv")

    # Convert response column to a factor
    ecology.hex$Angaus <- as.factor(ecology.hex$Angaus)

    # Split the dataset into training and calibrating datasets
    ecology.split <- h2o.splitFrame(ecology.hex, seed = 12354)
    ecology.train <- ecology.split[[1]]
    ecology.calib <- ecology.split[[2]]

    # Introduce a weight column (artificial non-constant) ONLY to the train set (NOT the calibration one)
    weights <- c(0, rep(1, nrow(ecology.train) - 1))
    ecology.train$weight <- as.h2o(weights)

    # Train an H2O GBM Model with the Calibration dataset
    ecology.model <- h2o.gbm(x = 3:13, y = "Angaus", training_frame = ecology.train,
                             ntrees = 10,
                             max_depth = 5,
                             min_rows = 10,
                             learn_rate = 0.1,
                             distribution = "multinomial",
                             weights_column = "weight",
                             calibrate_model = TRUE,
                             calibration_frame = ecology.calib
    )

    predicted <- h2o.predict(ecology.model, ecology.calib)

    # View the predictions
    predicted
      predict        p0         p1    cal_p0     cal_p1
    1       0 0.9201473 0.07985267 0.9415007 0.05849932
    2       0 0.9304295 0.06957048 0.9461329 0.05386715
    3       0 0.8742164 0.12578357 0.9159100 0.08408999
    4       1 0.4877726 0.51222745 0.2896916 0.71030837
    5       1 0.4104012 0.58959878 0.1744277 0.82557230
    6       1 0.3476665 0.65233355 0.1102849 0.88971514

    [256 rows x 5 columns]

   .. code-block:: python

    import h2o
    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    h2o.init()

    # Import the ecology dataset
    ecology = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/ecology_model.csv")

    # Convert response column to a factor
    ecology['Angaus'] = ecology['Angaus'].asfactor()

    # Set the predictors and the response column name
    response = 'Angaus'
    predictors = ecology.columns[3:13]

    # Split into train and calibration sets
    train, calib = ecology.split_frame(seed = 12354)

    # Introduce a weight column (artificial non-constant) ONLY to the train set (NOT the calibration one)
    w = h2o.create_frame(binary_fraction=1, binary_ones_fraction=0.5, missing_fraction=0, rows=744, cols=1)
    w.set_names(["weight"])
    train = train.cbind(w)

    # Train an H2O GBM Model with Calibration
    ecology_gbm = H2OGradientBoostingEstimator(ntrees = 10, max_depth = 5, min_rows = 10,
                                               learn_rate = 0.1, distribution = "multinomial",
                                               calibrate_model = True, calibration_frame = calib)
    ecology_gbm.train(x = predictors, y = "Angaus", training_frame = train, weights_column = "weight")

    predicted = ecology_gbm.predict(train)

    # View the calibrated predictions appended to the original predictions
    predicted
      predict        p0         p1     cal_p0     cal_p1
    ---------  --------  ---------  ---------  ---------
            1  0.319428  0.680572   0.185613   0.814387
            0  0         0          0.0274573  0.972543
            0  0.90577   0.0942296  0.913323   0.0866773
            0  0.783394  0.216606   0.825601   0.174399
            0  0.899183  0.100817   0.909852   0.0901482
            0  0         0          0.0274573  0.972543
            0  0.909846  0.090154   0.915409   0.0845909
            1  0.456384  0.543616   0.358169   0.641831
            0  0         0          0.0274573  0.972543
            0  0.918923  0.0810765  0.919893   0.0801069

    [744 rows x 5 columns]



