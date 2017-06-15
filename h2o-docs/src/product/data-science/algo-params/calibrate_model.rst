``calibrate_model``
-------------------

- Available in: GBM, DRF
- Hyperparameter: no

Description
~~~~~~~~~~~

The ``calibrate_model`` option allows you to specify Platt scaling in GBM and DRF to calculate calibrated class probabilities. `Platt scaling <https://en.wikipedia.org/wiki/Platt_scaling>`__ transforms the output of a classification model into a probability distribution over classes. It works by fitting a logistic regression model to a classifier's scores. Platt scaling will generally not affect the ranking of observations. Logloss, however, will generally improve with Platt scaling.

The ``calibrate_model`` option is disabled by default. When enabled, the calibrated probabilities will be appended to the frame with the original prediction. 

Note that when this option is enabled, then you must also specify the calibration dataframe (specified with `calibrate_frame <calibrate_frame.html>`__) that will be used for Platt scaling. A best practice is to split the original dataset into training and calibration sets. 

Refer to the following for more information about Platt scaling:

- `Calibrating Classifier Probabilities <http://danielnee.com/tag/platt-scaling/>`__
- `Predicting Good Probabilities with Supervised Learning <http://www.datascienceassn.org/sites/default/files/Predicting%20good%20probabilities%20with%20supervised%20learning.pdf>`__

Related Parameters
~~~~~~~~~~~~~~~~~~

- `calibrate_frame <calibrate_frame.html>`__


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
                                               weights_column = "weight", calibrate_model = True,
                                               calibration_frame = calib)
    ecology_gbm.train(x = predictors, y = "Angaus", training_frame = train)

    predicted = ecology_gbm.predict(calib)

    # View the calibrated predictions appended to the original predictions
    predicted
      predict        p0         p1    cal_p0     cal_p1
    ---------  --------  ---------  --------  ---------
            0  0.881607  0.118393   0.925676  0.0743243
            0  0.917786  0.0822144  0.945076  0.0549236
            0  0.697753  0.302247   0.706711  0.293289
            1  0.538659  0.461341   0.367735  0.632265
            1  0.442108  0.557892   0.197091  0.802909
            1  0.382415  0.617585   0.125879  0.874121
            0  0.923423  0.0765771  0.947633  0.0523671
            0  0.879797  0.120203   0.924555  0.0754445
            0  0.811017  0.188983   0.868916  0.131084
            0  0.709102  0.290898   0.727279  0.272721

    [256 rows x 5 columns]

