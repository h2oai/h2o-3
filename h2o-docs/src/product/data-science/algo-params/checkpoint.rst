``checkpoint``
--------------

- Available in: GBM, DRF, XGBoost, Deep Learning, GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

In real-world scenarios, data can change. For example, you may have a model currently in production that was built using 1 million records. At a later date, you may receive several hundred thousand more records. Rather than building a new model from scratch, you can use checkpointing to create a new model based on the existing model. 

The ``checkpoint`` option allows you to specify a model key associated with a previously trained model. This will build a new model as a continuation of a previously generated model. If this is not specified, then the algorithm will start training a new model instead of continuing building a previous model. 

When setting parameters that continue to build on a previous model, specifically ``ntrees`` (in GBM/DRF/XGBoost) or ``epochs`` (in Deep Learning), specify the total amount of training that you want if you had started from scratch, not the number of additional epochs or trees you want. Note that this means the ``ntrees`` or ``epochs`` parameter for the checkpointed model must always be greater than the original value. For example:

- If the first model builds 1 tree, and you want your new model to build 50 trees, then the continuation model (using checkpointing) would specify ``ntrees=50``. This gives you a total of 50 trees including 49 new ones. 
- If your original model included 20 trees, and you specify ``ntrees=50`` for the continuation model, then the new model will  add 30 trees to the model, again giving you a total of 50 trees.
- If your oringinal model included 20 trees, and you specify ``ntrees=10`` (a lower value), then you will receive an error indicating that the requested ntrees must be higher than 21.

**Notes**:

- The response type and model type of the training data must be the same as for the checkpointed model.
- The columns of the training data must be the same as for the checkpointed model.
- Categorical factor levels of the training data must be the same as for the checkpointed model.
- The total number of predictors of the training data must be the same as for the checkpointed model.

The following options cannot be modified when rebuilding a model using ``checkpoint``:

 **GBM/DRF Options**

	- build_tree_one_node
	- max_depth
	- min_rows
	- nbins
	- nbins_cats
	- nbins_top_level
	- sample_rate

 **XGBoost Options**
 
    - booster
    - grow_policy
    - max_rows
    - min_rows
    - sample_rate
    - tree_method   

 **Deep Learning Options**

    - activation
    - adaptive_rate
    - autoencoder
    - col_major
    - distribution
    - drop_na20_cols
    - epsilon
    - huber_alpha
    - ignore_const_cols
    - max_categorical_features
    - momentum_ramp
    - momentum_stable
    - momentum_start
    - nesterov_accelerated_gradient
    - nfolds
    - quantile_alpha
    - rate
    - rate_annealing
    - rate_decay
    - rho
    - sparse
    - sparsity_beta
    - standardize
    - tweedie_power
    - use_all_factor_levels
    - y (response_column)

Related Parameters
~~~~~~~~~~~~~~~~~~

- None


Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # import the cars dataset: 
        # this dataset is used to classify whether or not a car is economical based on 
        # the car's displacement, power, weight, and acceleration, and the year it was made 
        cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

        # convert response column to a factor
        cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

        # set the predictor names and the response column name
        predictors <- c("displacement", "power", "weight", "acceleration", "year")
        response <- "economy_20mpg"

        # split into train and validation sets
        cars_split <- h2o.splitFrame(data = cars,ratios = 0.8, seed = 1234)
        train <- cars_split[[1]]
        valid <- cars_split[[2]]

        # build a GBM with 1 tree (ntrees = 1) for the first model:
        cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
                            validation_frame = valid, ntrees = 1, seed = 1234)

        # print the auc for the validation data
        print(h2o.auc(cars_gbm, valid = TRUE))
        [1] 0.9690799

        # re-start the training process on a saved GBM model using the ‘checkpoint‘ argument:
        # the checkpoint argument requires the model id of the model on which you want to 
        # continue building
        # get the model's id from "cars_gbm" model using `cars_gbm@model_id`
        # the first model has 1 tree, let's continue building the GBM with an additional 49 
        # more trees, so set ntrees = 50

        # to see how many trees the original model built you can look at the `ntrees` attribute
        print(paste("Number of trees built for cars_gbm model:", cars_gbm@allparameters$ntrees))
        [1] "Number of trees built for cars_gbm model: 1"

        # build and train model with 49 additional trees for a total of 50 trees:
        cars_gbm_continued <- h2o.gbm(x = predictors, y = response, training_frame = train,
                                      validation_frame = valid, 
                                      checkpoint = cars_gbm@model_id, 
                                      ntrees = 50, 
                                      seed = 1234)

        # print the auc for the validation data
        print(h2o.auc(cars_gbm_continued, valid = TRUE))
        [1] 0.9803922

        # to see how many trees the continuation model built you can look at the `ntrees` attribute
        print(paste("Number of trees built for cars_gbm model:", cars_gbm_continued@allparameters$ntrees))
        [1] "Number of trees built for cars_gbm model: 50"

        # you can also use checkpointing to pass in a new dataset 
        # (see options above for parameters you cannot change)
        # simply change out the training and validation frames with your new dataset




   .. code-tab:: python

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
        0.981146304676

        # re-start the training process on a saved GBM model using the ‘checkpoint‘ argument:
        # the checkpoint argument requires the model id of the model on which you wish to continue building
        # get the model's id from "cars_gbm" model using `cars_gbm.model_id`
        # the first model has 1 tree, let's continue building the GBM with an additional 49 more trees, 
        # so set ntrees = 50

        # to see how many trees the original model built you can look at the `ntrees` attribute
        print("Number of trees built for cars_gbm model:", cars_gbm.ntrees)
        ('Number of trees built for cars_gbm model:', 20)

        # build and train model with 49 additional trees for a total of 50 trees:
        cars_gbm_continued = H2OGradientBoostingEstimator(checkpoint= cars_gbm.model_id, ntrees = 50, seed = 1234)
        cars_gbm_continued.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

        # print the auc for the validation data
        cars_gbm_continued.auc(valid=True)
        0.9803921568627451

        # to see how many trees the continuation model built you can look at the `ntrees` attribute
        print("Number of trees built for cars_gbm model:", cars_gbm_continued.ntrees)
        ('Number of trees built for cars_gbm model:', 50)

        # you can also use checkpointing to pass in a new dataset in addition to increasing 
        # the number of trees/epochs. (See options above for parameters you cannot change.)
        # simply change out the training and validation frames with your new dataset.


