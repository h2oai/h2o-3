Checkpointing Models
====================

In real-world scenarios, data can change. For example, you may have a model currently in production that was built using 1 million records. At a later date, you may receive several hundred thousand more records. Rather than building a new model from scratch, you can use the ``checkpoint`` option to create a new model based on the existing model. 

The ``checkpoint`` option is available for DRF, GBM, XGBoost, and Deep Learning algorithms. This allows you to specify a model key associated with a previously trained model. This will build a new model as a continuation of a previously generated model. If this is not specified, then the algorithm will start training a new model instead of continuing building a previous model. 

When setting parameters that continue to build on a previous model, specifically ``ntrees`` (in GBM/DRF/XGBoost) or ``epochs`` (in Deep Learning), specify the total amount of training that you want if you had started from scratch, not the number of additional epochs or trees you want. Note that this means the ``ntrees`` or ``epochs`` parameter for the checkpointed model must always be greater than the original value. For example:

- If the first model builds 1 tree, and you want your new model to build 50 trees, then the continuation model (using checkpointing) would specify ``ntrees=50``. This gives you a total of 50 trees including 49 new ones. 
- If your original model included 20 trees, and you specify ``ntrees=50`` for the continuation model, then the new model will  add 30 trees to the model, again giving you a total of 50 trees.
- If your oringinal model included 20 trees, and you specify ``ntrees=10`` (a lower value), then you will receive an error indicating that the requested ntrees must be higher than 21.

**Notes**:

- The response type and model type of the training data must be the same as for the checkpointed model.
- The columns of the training data must be the same as for the checkpointed model.
- Categorical factor levels of the training data must be the same as for the checkpointed model.
- The total number of predictors of the training data must be the same as for the checkpointed model.
- Cross-validation is not currently supported for checkpointing. In addition, if you use a dataset for validation (with the ``validation_frame`` parameter), you must use this same validation set each time you continue training through checkpointing.

The parameters that you can specify with checkpointing vary based on the algorithm that was used for model training. Scenarios for different algorithms are described in the sections that follow.

Checkpoint with Deep Learning
-----------------------------

In Deep Learning, ``checkpoint`` can be used to continue training on the same dataset for additional epochs or to train on new data for additional epochs.

To resume model training, use checkpoint model keys (``model_id``) to incrementally train a specific model using more iterations, more data, different data, and so forth. To further train the initial model, use it (or its key) as a checkpoint argument for a new model.

To get the best possible model in a general multi-node setup, we recommend building a model with ``train_samples_per_iteration=-2`` (default, auto-tuning) and saving it to disk so that you'll have at least one saved model.

To improve this initial model, start from the previous model and add iterations by building another model, specifying ``checkpoint=previous_model_id``, and changing ``train_samples_per_iteration``, ``target_ratio_comm_to_comp``, or other parameters. Many parameters can be changed between checkpoints, especially those that affect regularization or performance tuning.

Checkpoint restart suggestions:

1. For multi-node only: Leave ``train_samples_per_iteration=-2`` and increase ``target_ratio_comm_to_comp`` from 0.05 to 0.25 or 0.5 (more communication). This should lead to a better model when using multiple nodes. **Note**: This has no effect on single-node performance at all because there is no actual communication needed.

2. For both single and multi-node (bagging-like): Explicitly set ``train_samples_per_iteration=N``, where :math:`N` is the number of training samples for the whole cluster to train with for one iteration. Each of the :math:`n` nodes will then train on :math:`N/n` randomly chosen rows for each iteration. Obviously, a good choice for :math:`N` depends on the dataset size and the model complexity. Refer to the logs to see what values of :math:`N` are used in option 1 (when auto-tuning is enabled). Typically, option 1 is sufficient.

3. For both single and multi-node: Change regularization parameters such as ``l1``, ``l2``, ``max_w2``, ``input_dropout_ratio``, ``hidden_dropout_ratios``. For best results, build the first model with ``RectifierWithDropout`` and ``input_dropout_ratio=0`` and ``hidden_dropout_ratios`` with a list of all 0s, just to be able to enable dropout regularization later. Hidden dropout is often used for initial models because it often improves generalization. Input dropout is especially useful if there is some noise in the input.

Options 1 and 3 should result in a good model. Of course, grid search can be used with checkpoint restarts to scan a broad range of good continuation models.

**Note**: The following parameters cannot be modified during checkpointing:

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

The following example demonstrates how to build a deep learning model that will later be used for checkpointing. This example will cover both types of checkpointing: checkpointing with the same dataset and checkpointing with new data. This example uses the famous MNIST dataset, which is used to classify handwritten digits from 0 through 9.

.. tabs::
  .. code-tab:: r R

      library(h2o)
      h2o.init()

      # Import the mnist dataset
      mnist_original <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")

      # The last column, C785, is the target that lists whether the 
      # handwritten digit was a 0,1,2,3,4,5,6,7,8, or 9. Before we 
      # set the variables for our predictors and target, we will 
      # convert our target column from type int to type enum.
      mnist_original[, 785] <- as.factor(mnist_original[, 785])
      predictors <- c(1:784)
      target <- c(785)

      # Split the data into training and validation sets, and split
      # a piece off to demonstrate adding new data with checkpointing. 
      # In a real world scenario, however, you would not have your 
      # new data at this point.
      mnist_original_split <- h2o.splitFrame(data = mnist_original,ratios = c(0.7, 0.15), seed = 1234)
      train <- mnist_original_split[[1]]
      valid <- mnist_original_split[[2]]
      new_data <- mnist_original_split[[3]]

      # Build the first deep learning model, specifying the model_id so you 
      # can indicate which model to use when you want to continue training.
      # We will use 4 epochs to start off with and then build an additional
      # 16 epochs with checkpointing.
      dl <- h2o.deeplearning(model_id = 'dl',
                             x = predictors,
                             y = target,
                             training_frame = train,
                             validation_frame = valid,
                             distribution = 'multinomial',
                             epochs = 4,
                             activation = 'RectifierWithDropout',
                             hidden_dropout_ratios = c(0, 0),
                             seed = 1234)

      print(h2o.mean_per_class_error(dl, valid = TRUE))
      [1] 0.06742894
      print(h2o.logloss(dl, valid = TRUE))
      [[1] 0.3991185

      # Checkpoint on the same dataset. This shows how to train an additional
      # 16 epochs on top of the first 4. To do this, set epochs equal to 20 (not 16).
      # This example also changes the list of hidden dropout ratios.
      dl_checkpoint1 <- h2o.deeplearning(model_id = 'dl_checkpoint1',
                                         x = predictors,
                                         y = target,
                                         training_frame = train,
                                         checkpoint = 'dl',
                                         validation_frame = valid,
                                         distribution = 'multinomial',
                                         epochs = 20,
                                         activation = 'RectifierWithDropout',
                                         hidden_dropout_ratios = c(0, 0.5),
                                         seed = 1234)
      

      print(h2o.mean_per_class_error(dl_checkpoint1, valid = TRUE))
      [1] 0.05604628
      print(h2o.logloss(dl_checkpoint1, valid = TRUE))
      [1] 0.2328195
      print(improvement_dl <- h2o.logloss(dl, valid = TRUE) - h2o.logloss(dl_checkpoint1, valid = TRUE))
      [1] 0.166299

      # Checkpoint on a new dataset. Notice that to train on new data, 
      # you set training_frame to new_data (not train) and leave the 
      # same dataset to use for validation.
      dl_checkpoint2 <- h2o.deeplearning(model_id = 'dl_checkpoint2',
                                         x = predictors,
                                         y = target,
                                         training_frame = new_data,
                                         checkpoint = 'dl',
                                         validation_frame = valid,
                                         distribution = 'multinomial',
                                         epochs = 15,
                                         activation = 'RectifierWithDropout',
                                         hidden_dropout_ratios = c(0, 0),
                                         seed = 1234)

      print(h2o.mean_per_class_error(dl_checkpoint2, valid = TRUE))
      [1] 0.06610397
      print(h2o.logloss(dl_checkpoint2, valid = TRUE))
      [[1] 0.3532841
      print(improvement_dl <- h2o.logloss(dl, valid = TRUE) - h2o.logloss(dl_checkpoint2, valid = TRUE))
      [1] 0.04583448

  .. code-tab:: python

      import h2o
      from h2o.estimators.deeplearning import H2ODeepLearningEstimator
      h2o.init()

      # Import the mnist dataset
      mnist_original = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")

      # The last column, C785, is the target that lists whether the 
      # handwritten digit was a 0,1,2,3,4,5,6,7,8, or 9. Before we 
      # set the variables for our predictors and target, we will 
      # convert our target column from type int to type enum.
      mnist_original['C785'] = mnist_original['C785'].asfactor()
      predictors = mnist_original.columns[0:-1]
      target = 'C785'

      # Split the data into training and validation sets, and split
      # a piece off to demonstrate adding new data with checkpointing. 
      # In a real world scenario, however, you would not have your 
      # new data at this point.
      train, valid, new_data = mnist_original.split_frame(ratios=[.7, .15], seed=1234)

      # Build the first deep learning model, specifying the model_id so you 
      # can indicate which model to use when you want to continue training.
      # We will use 4 epochs to start off with and then build an additional
      # 16 epochs with checkpointing.
      dl = H2ODeepLearningEstimator(distribution='multinomial', 
                                    model_id='dl',
                                    epochs=4,
                                    activation='rectifier_with_dropout',
                                    hidden_dropout_ratios=[0,0],
                                    seed=1234)
      dl.train(x=predictors, y=target, training_frame=train, validation_frame=valid)

      print('Validation Mean Per Class Error for DL:', dl.mean_per_class_error(valid=True))
      ('Validation Mean Per Class Error for DL:', 0.0665710328899672)

      print('Validation Logloss for DL:', dl.logloss(valid=True))
      ('Validation Logloss for DL:', 0.38771905396189366)


      # Checkpoint on the same dataset. This shows how to train an additional
      # 16 epochs on top of the first 4. To do this, set epochs equal to 20 (not 6).
      # This example also changes the list of hidden dropout ratios.
      dl_checkpoint1 = H2ODeepLearningEstimator(distribution='multinomial',
                                                model_id='dl_w_checkpoint1',
                                                checkpoint='dl', 
                                                epochs=20,
                                                activation='rectifier_with_dropout',
                                                hidden_dropout_ratios=[0,0.5],
                                                seed=1234)
      dl_checkpoint1.train(x=predictors, y=target, training_frame=train, validation_frame=valid)

      print('Validation Mean Per Class Error for DL with Checkpointing:', dl_checkpoint1.mean_per_class_error(valid=True))
      ('Validation Mean Per Class Error for DL with Checkpointing:', 0.05596493320234874)

      print('Validation Logloss for DL with Checkpointing:', dl_checkpoint1.logloss(valid=True))
      ('Validation Logloss for DL with Checkpointing:', 0.2622290756893055)

      improvement_dl = dl.logloss(valid=True) - dl_checkpoint1.logloss(valid=True) 
      print('Overall improvement in logloss is {0}'.format(improvement_dl))
      Overall improvement in logloss is 0.142712240337

      # Checkpoint on a new dataset. Notice that to train on new data, 
      # you set training_frame to new_data (not train) and leave the 
      # same dataset to use for validation.
      dl_checkpoint2 = H2ODeepLearningEstimator(distribution='multinomial', 
                                                model_id='dl_w_checkpoint2',
                                                checkpoint='dl',
                                                epochs=15,
                                                activation='rectifier_with_dropout',
                                                hidden_dropout_ratios=[0,0],
                                                seed=1234)
      dl_checkpoint2.train(x=predictors, y=target, training_frame=new_data, validation_frame=valid)

      print('Validation Mean Per Class Error for DL:', dl_checkpoint2.mean_per_class_error(valid=True))
      ('Validation Mean Per Class Error for DL:', 0.06465957648350525)

      print('Validation Logloss for DL:', dl_checkpoint2.logloss(valid=True))
      ('Validation Logloss for DL:', 0.3616085918270951)

      improvement_dl =  dl.logloss(valid=True) - dl_checkpoint2.logloss(valid=True) 
      print('Overall improvement in logloss is {0}'.format(improvement_dl))
      Overall improvement in logloss is 0.0261104621348


Checkpoint with DRF
-------------------

In DRF, ``checkpoint`` can be used to continue training on the same dataset for additional iterations, or continue training on new data for additional iterations.

**Note**: The following parameters cannot be modified during checkpointing:

- build_tree_one_node
- max_depth
- min_rows
- nbins
- nbins_cats
- nbins_top_level
- sample_rate

The following example demonstrates how to build a distributed random forest model that will later be used for checkpointing. This checkpoint example shows how to continue training on an existing model and also builds with new data. This example uses the cars dataset, which classifies whether or not a car is economical based on the car's displacement, power, weight, and acceleration, and the year it was made.
 
.. tabs::
  .. code-tab:: r R

      library(h2o)
      h2o.init()

      # Import the cars dataset.
      cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

      # Convert the response column to a factor
      cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

      # Set the predictor names and the response column name
      predictors <- c("displacement", "power", "weight", "acceleration", "year")
      response <- "economy_20mpg"

      # Split the data into training and validation sets, and split
      # a piece off to demonstrate adding new data with checkpointing.
      # In a real world scenario, however, you would not have your
      # new data at this point.
      cars_split <- h2o.splitFrame(data = cars,ratios = c(0.7, 0.15), seed = 1234)
      train <- cars_split[[1]]
      valid <- cars_split[[2]]
      new_data <- cars_split[[3]]

      # Build the first DRF model, specifying the model_id so you can
      # indicate which model to use when you want to continue training.
      # We will use 1 tree to start off with and then build an additional
      # 9 trees with checkpointing.
      drf <- h2o.randomForest(model_id = 'drf',
                              x = predictors,
                              y = response,
                              training_frame = train,
                              validation_frame = valid,
                              ntrees = 1,
                              seed = 1234)

      print(h2o.mean_per_class_error(drf, valid = TRUE))
      [1] 0.09453782
      print(h2o.logloss(drf, valid = TRUE))
      [1] 3.597789

      # Checkpoint on the same dataset. This shows how to train an additional
      # 9 trees on top of the first 1. To do this, set ntrees equal to 10.
      drf_continued <- h2o.randomForest(model_id = 'drf_continued',
                                        x = predictors,
                                        y = response,
                                        training_frame = train,
                                        validation_frame = valid,
                                        checkpoint = 'drf',
                                        ntrees = 10,
                                        seed = 1234)

      print(h2o.mean_per_class_error(drf_continued, valid = TRUE))
      [[1] 0.06512605
      print(h2o.logloss(drf_continued, valid = TRUE))
      [1] 0.1826136
      print(improvement_drf <- h2o.logloss(drf, valid = TRUE) - h2o.logloss(drf_continued, valid = TRUE))
      [1] 3.415176

      # Checkpoint on a new dataset. Notice that to train on new data, 
      # you set training_frame to new_data (not train) and leave the 
      # same dataset to use for validation.

      drf_newdata <- h2o.randomForest(model_id = 'drf_newdata',
                                      x = predictors,
                                      y = response,
                                      training_frame = new_data,
                                      validation_frame = valid,
                                      checkpoint = 'drf',
                                      ntrees = 15,
                                      seed = 1234)

      print(h2o.mean_per_class_error(drf_newdata, valid = TRUE))
      [1] 0.07142857
      print(h2o.logloss(drf_newdata, valid = TRUE))
      [1] 0.1767007
      print(improvement_drf <- h2o.logloss(drf, valid = TRUE) - h2o.logloss(drf_newdata, valid = TRUE))
      [1] 3.421088

  .. code-tab:: python

      import h2o
      from h2o.estimators.random_forest import H2ORandomForestEstimator
      h2o.init()

      # Import the cars dataset.
      cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

      # Convert the response column to a factor
      cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

      # Set the predictor names and the response column name
      predictors = ["displacement","power","weight","acceleration","year"]
      response = "economy_20mpg"

      # Split the data into training and validation sets, and split
      # a piece off to demonstrate adding new data with checkpointing. 
      # In a real world scenario, however, you would not have your 
      # new data at this point.
      train, valid, new_data = cars.split_frame(ratios = [.7, .15], seed = 1234)

      # Build the first DRF model, specifying the model_id so you can
      # indicate which model to use when you want to continue training.
      # We will use 1 trees to start off with and then build an additional
      # 9 trees with checkpointing.
      drf = H2ORandomForestEstimator(model_id="drf", ntrees = 1, seed = 1234)
      drf.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

      print('Validation Mean Per Class Error for DRF:', drf.mean_per_class_error(valid=True))
      ('Validation Mean Per Class Error for DRF:', [[1.0, 0.09453781512605042]])

      print('Validation Logloss for DRF:', drf.logloss(valid=True))
      ('Validation Logloss for DRF:', 3.597789207803196)

      # Checkpoint on the same dataset. This shows how to train an additional
      # 9 trees on top of the first 1. To do this, set ntrees equal to 10.
      drf_continued = H2ORandomForestEstimator(model_id = 'drf_continued', 
                                               checkpoint = drf, 
                                               ntrees = 10, 
                                               seed = 1234)
      drf_continued.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

      print('Validation Mean Per Class Error for DRF with Checkpointing:', drf_continued.mean_per_class_error(valid=True))
      ('Validation Mean Per Class Error for DRF with Checkpointing:', [[0.7, 0.06512605042016806]])

      print('Validation Logloss for DRF with Checkpointing:', drf_continued.logloss(valid=True))
      ('Validation Logloss for DRF with Checkpointing:', 0.1826135624064031)

      improvement_drf = drf.logloss(valid=True) - drf_continued.logloss(valid=True)
      print('Overall improvement in logloss is {0}'.format(improvement_drf))
      Overall improvement in logloss is 3.4151756454

      # Checkpoint on a new dataset. Notice that to train on new data, 
      # you set training_frame to new_data (not train) and leave the 
      # same dataset to use for validation.
      drf_newdata = H2ORandomForestEstimator(model_id='drf_newdata',
                                             checkpoint='drf', 
                                             ntrees=15,
                                             seed=1234)
      drf_newdata.train(x=predictors, y=response, training_frame=new_data, validation_frame=valid)

      print('Validation Mean Per Class Error for DRF:', drf_newdata.mean_per_class_error(valid=True))
      ('Validation Mean Per Class Error for DRF:', [[0.5575757582982381, 0.06512605042016806]])

      print('Validation Logloss for DRF:', drf_newdata.logloss(valid=True))
      ('Validation Logloss for DRF:', 0.17670074914138334)

      improvement_drf =  drf.logloss(valid=True) - drf_newdata.logloss(valid=True)
      print('Overall improvement in logloss is {0}'.format(improvement_drf))
      Overall improvement in logloss is 3.42108845866

Checkpoint with GBM
-------------------

In GBM, ``checkpoint`` can be used to continue training on a previously generated model rather than rebuilding the model from scratch. For example, you may train a model with 50 trees and wonder what the model would look like if you trained 10 more.

**Note**: The following parameters cannot be modified during checkpointing:

- build_tree_one_node
- max_depth
- min_rows
- nbins
- nbins_cats
- nbins_top_level
- sample_rate

The following example demonstrates how to build a gradient boosting model that will later be used for checkpointing. This checkpoint example shows how to continue training on an existing model. We do not recommend using GBM to train on new data. This example uses the cars dataset, which classifies whether or not a car is economical based on the car's displacement, power, weight, and acceleration, and the year it was made.

.. tabs::
  .. code-tab:: r R

      library(h2o)
      h2o.init()

      # Import the cars dataset.
      cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

      # Convert the response column to a factor
      cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

      # Set the predictor names and the response column name
      predictors <- c("displacement", "power", "weight", "acceleration", "year")
      response <- "economy_20mpg"

      # Split the data into training and validation sets, and split
      # a piece off to demonstrate adding new data with checkpointing. 
      # In a real world scenario, however, you would not have your 
      # new data at this point.
      cars_split <- h2o.splitFrame(data = cars, ratios = c(0.7, 0.15), seed = 1234)
      train <- cars_split[[1]]
      valid <- cars_split[[2]]
      new_data <- cars_split[[3]]

      # Build the first GBM model, specifying the model_id so you can
      # indicate which model to use when you want to continue training.
      # We will use 5 trees to start off with and then build an additional
      # 45 trees with checkpointing.
      gbm <- h2o.gbm(model_id = 'gbm', 
                     x = predictors, 
                     y = response, 
                     training_frame = train,
                     validation_frame = valid, 
                     ntrees = 5, 
                     seed = 1234)
      
      print(h2o.mean_per_class_error(gbm, valid = TRUE))
      [1] 0.08613445
      print(h2o.logloss(gbm, valid = TRUE))
      [1] 0.3822369

      # Checkpoint on the same dataset. This shows how to train an additional
      # 45 trees on top of the first 5. To do this, set ntrees equal to 50.
      gbm_continued <- h2o.gbm(model_id = 'gbm_continued', 
                               x = predictors, 
                               y = response, 
                               training_frame = train,
                               validation_frame = valid,
                               checkpoint = 'gbm',
                               ntrees = 50,
                               seed = 1234)

      print(h2o.mean_per_class_error(gbm_continued, valid = TRUE))
      [1] 0.02941176
      print(h2o.logloss(gbm_continued, valid = TRUE))
      [1] [1] 0.1959525
      print(improvement_gbm <- h2o.logloss(gbm, valid = TRUE) - h2o.logloss(gbm_continued, valid=TRUE))
      [1] 0.1862843

      # See how the variable importance changes between the original model
      # trained on 5 trees and the checkpointed model that adds 45 more trees
      h2o.varimp(gbm)
      Variable Importances: 
            variable relative_importance scaled_importance percentage
      1 displacement          157.492630          1.000000   0.826301
      2         year           16.086107          0.102139   0.084397
      3       weight           13.484656          0.085621   0.070749
      4        power            1.995252          0.012669   0.010468
      5 acceleration            1.540924          0.009784   0.008085
      
      h2o.varimp(gbm_continued)
      Variable Importances: 
            variable relative_importance scaled_importance percentage
      1       weight           60.823166          1.000000   0.408687
      2 displacement           50.491047          0.830129   0.339263
      3         year           18.169544          0.298727   0.122086
      4        power           10.953478          0.180087   0.073599
      5 acceleration            8.388416          0.137915   0.056364

      # Train a GBM with cross validation (nfolds=3)
      gbm_cv <- h2o.gbm(model_id = 'gbm_cv',
                        x = predictors,
                        y = response,
                        training_frame = train,
                        validation_frame = valid,
                        distribution = 'multinomial', 
                        ntrees = 5, 
                        nfolds = 3)

      # Recall that cross validation is not supported for checkpointing.
      # Add 2 more trees to the GBM without cross validation.
      gbm_nocv_checkpoint = h2o.gbm(model_id = 'gbm_nocv_checkpoint', 
                                    x = predictors, 
                                    y = response, 
                                    training_frame = train,
                                    validation_frame = valid,
                                    checkpoint = 'gbm_cv',
                                    distribution = 'multinomial',
                                    ntrees = (5 + 2),
                                    seed = 1234)

      # Logloss on cross validation hold out does not change on checkpointed model
      h2o.logloss(gbm_cv, xval = TRUE) == h2o.logloss(gbm_nocv_checkpoint, xval = TRUE)
      True

      # Logloss on training and validation data changes as more trees are added (checkpointed model)
      print(h2o.logloss(gbm_cv, valid = TRUE))
      [1] 0.3823892

      # Validation Logloss for GBM with Checkpointing 
      print(h2o.logloss(gbm_nocv_checkpoint, valid = TRUE))
      [1] 0.3314789

  .. code-tab:: python

      import h2o
      from h2o.estimators.gbm import H2OGradientBoostingEstimator
      h2o.init()

      # Import the cars dataset.
      cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

      # Convert the response column to a factor
      cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

      # Set the predictor names and the response column name
      predictors = ["displacement","power","weight","acceleration","year"]
      response = "economy_20mpg"

      # Split the data into training and validation sets, and split
      # a piece off to demonstrate adding new data with checkpointing. 
      # In a real world scenario, however, you would not have your 
      # new data at this point.
      train, valid, new_data = cars.split_frame(ratios = [.7, .15], seed = 1234)

      # Build the first GBM model, specifying the model_id so you can
      # indicate which model to use when you want to continue training.
      # We will use 5 trees to start off with and then build an additional
      # 45 trees with checkpointing.
      gbm = H2OGradientBoostingEstimator(model_id="gbm", ntrees = 5, seed = 1234)
      gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

      print('Validation Mean Per Class Error for GBM:', gbm.mean_per_class_error(valid=True))
      ('Validation Mean Per Class Error for GBM:', [[0.6978087517334117, 0.05882352941176472]])

      print('Validation Logloss for GBM:', gbm.logloss(valid=True))
      ('Validation Logloss for GBM:', 0.38223687802228534)

      # Checkpoint on the same dataset. This shows how to train an additional
      # 45 trees on top of the first 5. To do this, set ntrees equal to 50.
      gbm_continued = H2OGradientBoostingEstimator(model_id = 'gbm_continued', 
                                                   checkpoint = gbm, 
                                                   ntrees = 50, 
                                                   seed = 1234)
      gbm_continued.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

      print('Validation Mean Per Class Error for GBM with Checkpointing:', gbm_continued.mean_per_class_error(valid=True))
      ('Validation Mean Per Class Error for GBM with Checkpointing:', [[0.8908495796146818, 0.02941176470588236]])

      print('Validation Logloss for GBM with Checkpointing:', gbm_continued.logloss(valid=True))
      ('Validation Logloss for GBM with Checkpointing:', 0.19595254685018604)

      improvement_gbm = gbm.logloss(valid=True) - gbm_continued.logloss(valid=True)
      print('Overall improvement in logloss is {0}'.format(improvement_gbm))
      Overall improvement in logloss is 0.186284331172

      # See how the variable importance changes between the original model
      # trained on 5 trees and the checkpointed model that adds 45 more trees
      gbm.varimp(use_pandas=True).head()
             variable  relative_importance  scaled_importance  percentage
      0  displacement           157.492630           1.000000    0.826301
      1          year            16.086107           0.102139    0.084397
      2        weight            13.484656           0.085621    0.070749
      3         power             1.995252           0.012669    0.010468
      4  acceleration             1.540924           0.009784    0.008085

      gbm_continued.varimp(use_pandas=True).head()
             variable  relative_importance  scaled_importance  percentage
      0  displacement           207.983673           1.000000    0.612753
      1        weight            74.307816           0.357277    0.218923
      2          year            34.255642           0.164704    0.100923
      3         power            12.948729           0.062258    0.038149
      4  acceleration             9.929341           0.047741    0.029253

      # Train a GBM with cross validation (nfolds=3)
      gbm_cv = H2OGradientBoostingEstimator(distribution = 'multinomial', 
                                            model_id = 'gbm_cv', 
                                            ntrees = 5, 
                                            nfolds = 3)
      gbm_cv.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

      # Recall that cross validation is not supported for checkpointing.
      # Add 2 more trees to the GBM without cross validation.
      gbm_nocv_checkpoint = H2OGradientBoostingEstimator(distribution='multinomial', 
                                                         model_id='gbm_nocv_checkpoint',
                                                         checkpoint='gbm_cv', 
                                                         ntrees=(5 + 2), 
                                                         seed=1234)
      gbm_nocv_checkpoint.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

      # Logloss on cross validation hold out does not change on checkpointed model
      gbm_cv.logloss(xval = True) == gbm_nocv_checkpoint.logloss(xval = True)
      True

      # Logloss on training and validation data changes as more trees are added (checkpointed model)
      print('Validation Logloss for GBM: ' + str(round(gbm_cv.logloss(valid=True), 3)))
      Validation Logloss for GBM: 0.382

      print('Validation Logloss for GBM with Checkpointing: ' + str(round(gbm_nocv_checkpoint.logloss(valid=True), 3)))
      Validation Logloss for GBM with Checkpointing: 0.331

Checkpoint with XGBoost
-----------------------

In XGBoost, checkpoint can be used to continue training on a previously generated model rather than rebuilding the model from scratch. For example, you may train a model with 50 trees and wonder what the model would look like if you trained 10 more.

**Note**: The following parameters cannot be modified during checkpointing:

- booster
- grow_policy
- max_depth
- min_rows
- sample_rate
- tree_method

The following example demonstrates how to build a gradient boosting model that will later be used for checkpointing. This checkpoint example shows how to continue training on an existing model. We do not recommend using GBM to train on new data. This example uses the cars dataset, which classifies whether or not a car is economical based on the car's displacement, power, weight, and acceleration, and the year it was made.

.. tabs::
  .. code-tab:: r R

     library(h2o)
     h2o.init

     # import the iris dataset:
     iris <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

     # set the factor and response column:
     iris["class"] <- as.factor(iris["class"])
     response <- "class"

     # split the training and validation sets:
     splits <- h2o.splitFrame(iris, ratio = 0.8)
     train <- splits[[1]]
     valid <- splits[[2]]

     # build and train the first XGB model; specify the model_id
     # so you can indicate which model to use when you want to continue
     # training:
     iris_xgb <- h2o.xgboost(model_id = 'iris_xgb', 
                             y = response, 
                             training_frame = train, 
                             validation_frame = valid)

     # check the mse value:
     h2o.mse(iris_xgb)

     # build and train the second model using the checkpoint
     # you established in the first model:
     iris_xgb_cont <- h2o.xgboost(y = response, 
                                  training_frame = train, 
                                  validation_frame = valid, 
                                  checkpoint = 'iris_xgb', 
                                  ntrees = 51)

     # check the continued model mse value:
     h2o.mse(iris_xgb_cont)


  .. code-tab:: python

      import h2o
      from h2o.estimators import H2OXGBoostEstimator
      h2o.init()

      # import the iris dataset:
      iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

      # set the factor and response column:
      iris["class"] = iris["class"].asfactor()
      response = "class"

      # split the training and validation sets:
      train, valid = iris.split_frame(ratios=[.8])

      # build and train the first XGB model; specify the model_id
      # so you can indicate which model to use when you want to continue
      # training:
      iris_xgb = H2OXGBoostEstimator(model_id='iris_xgb', seed=1234)
      iris_xgb.train(y=response, training_frame=train, validation_frame=valid)

      # check the mse value:
      iris_xgb.mse()

      # build and train the second model using the checkpoint
      # you established in the first model:
      iris_xgb_cont = H2OXGBoostEstimator(ntrees=51, checkpoint='iris_xgb', seed=1234)
      iris_xgb_cont.train(y=response, training_frame=train, validation_frame=valid)

      # check the continued model mse value: 
      iris_xgb_cont.mse()

