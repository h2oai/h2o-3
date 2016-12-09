``checkpoint``
--------------

- Available in: GBM, DRF, Deep Learning
- Hyperparameter: no

Description
~~~~~~~~~~~

In real-world scenarios, data can change. For example, you may have a model currently in production that was built using 1 million records. At a later date, you may receive several hundred thousand more records. Rather than building a new model from scratch, you can use checkpointing to create a new model based on the existing model. 

The ``checkpoint`` option allows you to specify a model key associated with a previously trained model. This will build a new model as a continuation of a previously generated model. If this is not specified, then the algorithm will start training a new model instead of continuing building a previous model. 

When setting parameters that continue to build on a previous model, such as ``ntrees`` or ``epoch``, the new parameter value must be greater than the original value. For example, if the first model builds 1 tree, the continuation model (using checkpointing) must build ``ntrees`` equal to 2 (meaning build one additional tree) or greater.

**Note**: The following options cannot be modified when rebuilding a model using ``checkpoint``:

 **GBM/DRF Options**

	- build_tree_one_node
	- max_depth
	- min_rows
	- nbins
	- nbins_cats
	- nbins_top_level
	- sample_rate

 **Deep Learning Options**

    - activation
    - autoencoder
    - backend
    - channels
    - distribution
    - drop_na20_cols
    - ignore_const_cols
    - max_categorical_features
    - mean_image_file
    - missing_values_handling
    - momentum_ramp
    - momentum_stable
    - momentum_start
    - network
    - network_definition_file
    - nfolds
    - problem_type
    - standardize
    - use_all_factor_levels
    - y (response column)

Related Parameters
~~~~~~~~~~~~~~~~~~

- None


Example
~~~~~~~

.. example-code::
   .. code-block:: r

    library(h2o)
    h2o.init()

    # import the cars dataset: 
    # this dataset is used to classify whether or not a car is economical based on 
    # the car's displacement, power, weight, and acceleration, and the year it was made 
    cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

    # convert response column to a factor
    cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

    # set the predictor names and the response column name
    predictors <- c("displacement","power","weight","acceleration","year")
    response <- "economy_20mpg"

    # split into train and validation sets
    cars.split <- h2o.splitFrame(data = cars,ratios = 0.8, seed = 1234)
    train <- cars.split[[1]]
    valid <- cars.split[[2]]

    # build a GBM with 1 tree (ntrees = 1) for the first model:
    cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
                        validation_frame = valid, ntrees = 1, seed = 1234)

    # print the auc for the validation data
    print(h2o.auc(cars_gbm, valid = TRUE))

    # re-start the training process on a saved GBM model using the ‘checkpoint‘ argument:
    # the checkpoint argument requires the model id of the model on which you wish to continue building
    # get the model's id from "cars_gbm" model using `cars_gbm@model_id`
    # the first model has 1 tree, let's continue building the GBM with an additional 49 more trees, so set ntrees = 50

    # to see how many trees the original model built you can look at the `ntrees` attribute
    print(paste("Number of trees built for cars_gbm model:", cars_gbm@allparameters$ntrees))

    # build and train model with 49 additional trees for a total of 50 trees:
    cars_gbm_continued <- h2o.gbm(x = predictors, y = response, training_frame = train,
                        validation_frame = valid, checkpoint = cars_gbm@model_id, ntrees = 50, seed = 1234)

    # print the auc for the validation data
    print(h2o.auc(cars_gbm_continued, valid = TRUE))

    # you can also use checkpointing to pass in a new dataset (see options above for parameters you cannot change)
    # simply change out the training and validation frames with your new dataset




   .. code-block:: python

    import h2o
    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    h2o.init()

    # import the cars dataset:
    # this dataset is used to classify whether or not a car is economical based on
    # the car's displacement, power, weight, and acceleration, and the year it was made
    cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

    # convert response column to a factor
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    # set the predictor names and the response column name
    predictors = ["displacement","power","weight","acceleration","year"]
    response = "economy_20mpg"

    # split into train and validation sets
    train, valid = cars.split_frame(ratios = [.8], seed = 1234)

    # build a GBM with 1 tree (ntrees = 1) for the first model:
    cars_gbm = H2OGradientBoostingEstimator(ntrees = 1, seed = 1234)
    cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

    # print the auc for the validation data
    print(cars_gbm.auc(valid=True))

    # re-start the training process on a saved GBM model using the ‘checkpoint‘ argument:
    # the checkpoint argument requires the model id of the model on which you wish to continue building
    # get the model's id from "cars_gbm" model using `cars_gbm.model_id`
    # the first model has 1 tree, let's continue building the GBM with an additional 49 more trees, so set ntrees = 50

    # to see how many trees the original model built you can look at the `ntrees` attribute
    print("Number of trees built for cars_gbm model:", cars_gbm.ntrees)

    # build and train model with 49 additional trees for a total of 50 trees:
    cars_gbm_continued = H2OGradientBoostingEstimator(checkpoint= cars_gbm.model_id, ntrees = 50, seed = 1234)
    cars_gbm_continued.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

    # print the auc for the validation data
    cars_gbm_continued.auc(valid=True)

    # you can also use checkpointing to pass in a new dataset in addition to increasing/ (see options above for parameters you cannot change)
    # simply change out the training and validation frames with your new dataset


