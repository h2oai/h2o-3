``in_training_checkpoints_dir``
-------------------------------

- Available in: GBM
- Hyperparameter: no

Description
~~~~~~~~~~~

This option is used to automatically checkpoint an unfinished model into a defined directory while the training process is still running. This checkpoint allows you to manually restart training in case the cluster shuts down. 

The checkpoints are not considered fully trained models and should not be used as fully trained models (e.g., a checkpoint containing four trees can have a different prediction than a fully trained model containing four trees). However, a fully trained model without any training interruptions will always be the same as the model trained from the checkpoint if you provide the same data and hyperparameters.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `in_training_checkpoints_tree_interval <in_training_checkpoints_tree_interval.html>`__

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
        checkpoints_dir <- "checkpoints"
        
        # train the model and provide checkpoints in training process:
        pros_gbm <- h2o.gbm(x = predictors,
                            y = response,
                            model_id = "gbm-model",
                            ntrees = 10,
                            seed = 1111,
                            training_frame = prostate,
                            in_training_checkpoints_dir = checkpoints_dir)
        
        # retrieve the number of files in the exported checkpoints directory:
        num_files <- length(list.files(checkpoints_dir))
        num_files # 9

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
        
        # train the model and export checkpoints in training process:
        pros_gbm = H2OGradientBoostingEstimator(model_id="gbm_model",
                                                ntrees=10,
                                                seed=1111,
                                                in_training_checkpoints_dir=checkpoints_dir)
        
        pros_gbm.train(x=predictors, y=response, training_frame=prostate)
        
        # retrieve the number of files in the exported checkpoints directory:
        checkpoints = listdir(checkpoints_dir)
        print(checkpoints)
        num_files = len(listdir(checkpoints_dir)) 
        print(num_files) # 9
        
        # load checkpoint containing 3. trees:
        checkpoint = h2o.load_model(path.join(checkpoints_dir, pros_gbm.model_id + ".ntrees_3"))
        display("Checkpoint:", checkpoint)
        
        
        # restart from checkpoint containing 3. trees:
        pros_gbm_restarted = H2OGradientBoostingEstimator(model_id="gbm_model",
                                                          ntrees=10,
                                                          seed=1111,
                                                          checkpoint=checkpoint,
                                                          in_training_checkpoints_dir=checkpoints_dir)
        pros_gbm_restarted.train(x=predictors, y=response, training_frame=prostate)
        pros_gbm_restarted # this model is equal to pros_gbm
