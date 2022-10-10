``in_training_checkpoints_tree_interval``
-----------------------------------------

- Available in: GBM
- Hyperparameter: no

Description
~~~~~~~~~~~

The ``in_training_checkpoints_tree_interval`` option specifies after how many trees to checkpoint the model. This option is useful when you would like to reduce the size of ``in_training_checkpoints_dir``.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `in_training_checkpoints_dir <in_training_checkpoints_dir.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()
        
        # import the prostate dataset:
        prostate = h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        
        # set the predictors, response, and categorical features:
        prostate$RACE <- as.factor(prostate$RACE)
        prostate$CAPSULE <- as.factor(prostate$CAPSULE)
        predictors <- c("ID", "AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")
        response <- "CAPSULE"
        
        # specify directory for training checkpoints:
        checkpoints_dir <- "checkpoints-interval"
        
        # train the model and provide checkpoints in training process:
        pros_gbm <- h2o.gbm(x = predictors,
                            y = response,
                            model_id = "gbm-model",
                            ntrees = 10,
                            seed = 1111,
                            training_frame = prostate,
                            in_training_checkpoints_dir = checkpoints_dir,
                            in_training_checkpoints_tree_interval=2)
        
        # retrieve the number of files in the exported checkpoints directory:
        num_files <- length(list.files(checkpoints_dir))
        num_files # 4

   .. code-tab:: python

        # import necessary modules:
        import h2o
        from h2o.estimators.gbm import H2OGradientBoostingEstimator
        import tempfile
        from os import listdir, path
        
        # start h2o:
        h2o.init()
        
        # import the prostate dataset:
        prostate = h2o.import_file(path="http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        
        # set the predictors, response, and categorical features:
        prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
        prostate["RACE"] = prostate["RACE"].asfactor()
        predictors = ["ID", "AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]
        response = "CAPSULE"
        
        # specify directory for training checkpoints:
        checkpoints_dir = tempfile.mkdtemp()
        
        # Build and train the model and provide checkpoints in training process:
        pros_gbm = H2OGradientBoostingEstimator(model_id="gbm_model",
                                                ntrees=10,
                                                seed=1111,
                                                in_training_checkpoints_dir=checkpoints_dir,
                                                in_training_checkpoints_tree_interval=2)
        
        pros_gbm.train(x=predictors, y=response, training_frame=prostate)
        
        # retrieve the number of files in the exported checkpoints directory:
        checkpoints = listdir(checkpoints_dir)
        print(checkpoints)
        num_files = len(listdir(checkpoints_dir)) 
        print(num_files) # 4
