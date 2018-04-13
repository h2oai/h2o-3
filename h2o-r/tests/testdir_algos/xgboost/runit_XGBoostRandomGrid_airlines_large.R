setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



xgboost.random.grid.test <- function() {
    air.hex <- h2o.uploadFile(locate("smalldata/airlines/allyears2k_headers.zip"), destination_frame="air.hex")
    print(summary(air.hex))
    myX <- c("Year","Month","CRSDepTime","UniqueCarrier","Origin","Dest")

    hyper_params = list( 
      ## restrict the search to the range of max_depth established above
      max_depth = seq(1,10,1),                                      

      ## search a large space of row sampling rates per tree
      sample_rate = seq(0.2,1,0.01),                                             

      ## search a large space of column sampling rates per split
      col_sample_rate = seq(0.2,1,0.01),                                         

      ## search a large space of column sampling rates per tree
      col_sample_rate_per_tree = seq(0.2,1,0.01),                                

      ## search a large space of how column sampling per split should change as a function of the depth of the split
      ## Not available in Xgboost: col_sample_rate_change_per_level = seq(0.9,1.1,0.01),                      

      ## search a large space of the number of min rows in a terminal node
      min_rows = 2^seq(0,log2(nrow(air.hex))-1,1),                                 

      ## search a large space of the number of bins for split-finding for continuous and integer columns
      ## Not available in Xgboost: nbins = 2^seq(4,10,1),                                                     

      ## search a large space of the number of bins for split-finding for categorical columns
      ## Not available in Xgboost: nbins_cats = 2^seq(4,12,1),                                                

      ## search a few minimum required relative error improvement thresholds for a split to happen
      min_split_improvement = c(0,1e-8,1e-6,1e-4),                               

      ## try all histogram types (QuantilesGlobal and RoundRobin are good for numeric columns with outliers)
      ## Not available in XGBoost:      histogram_type = c("UniformAdaptive","QuantilesGlobal","RoundRobin")

      ## Some XGBoost-specific parameters:
      tree_method = c("auto", "exact", "approx", "hist"),
      grow_policy = c("depthwise", "lossguide"),
      booster = c("gbtree", "gblinear", "dart")  
        
    )

    search_criteria = list(
      ## Random grid search
      strategy = "RandomDiscrete",      

      ## limit the runtime to 10 minutes
      max_runtime_secs = 600,         

      ## build no more than 5 models
      max_models = 5,                  

      ## random number generator seed to make sampling of parameter combinations reproducible
      seed = 1234,                        

      ## early stopping once the leaderboard of the top 5 models is converged to 0.1% relative difference
      stopping_rounds = 5,                
      stopping_metric = "AUC",
      stopping_tolerance = 1e-3
    )

    air.grid <- h2o.grid("xgboost", y = "IsDepDelayed", x = myX,
                         distribution="bernoulli",
                         seed=1234,
                         training_frame = air.hex,
                         hyper_params = hyper_params,
                         search_criteria = search_criteria,
                         nfolds = 5, fold_assignment = 'Modulo',
                         keep_cross_validation_predictions = TRUE)
    print(air.grid)
    expect_that(length(air.grid@model_ids) <= 5, is_true())

    # test that predictions work on one of the individual models:
    first_id <- air.grid@model_ids[[1]]
    print("first model in grid: ")
    print(first_id)
    first <- h2o.getModel(first_id)

    first_predictions <- h2o.predict(first, air.hex)  # training data
    print("predictions for single model are in: ")
    print(h2o.getId(first_predictions))

    print("creating StackedEnsemble 1 of models: ")
    print(air.grid@model_ids)
    stacker1 <- h2o.stackedEnsemble(x = myX, y = "IsDepDelayed", 
                                   training_frame = air.hex,
                                   model_id = "my_ensemble_1",
                                   base_models = air.grid@model_ids)

    print("calling predict() on StackedEnsemble 1...")
    predictions = h2o.predict(stacker1, air.hex)  # training data
    print("predictions for ensemble 1 are in: ")
    print(h2o.getId(predictions))

    # StackedEnsemble including GLM, GBM and DL models, which use different CategoricalEncodingSchemes;
    # one-hot for GLM and DL and Enum for GBM.
    # See PUBDEV-5253, which was caused by XGBoost using LabelEncoder.
    gbm <- h2o.gbm(x = myX, y = "IsDepDelayed", 
                   training_frame = air.hex,
                   nfolds = 5, fold_assignment = 'Modulo', keep_cross_validation_predictions = TRUE,
                   model_id = "my_gbm",
                   ntrees = 3,
                   max_depth = 3)

    glm <- h2o.glm(x = myX, y = "IsDepDelayed", 
                   training_frame = air.hex,
                   nfolds = 5, fold_assignment = 'Modulo', keep_cross_validation_predictions = TRUE,
                   model_id = "my_glm",
                   family = "binomial")

    dl <- h2o.deeplearning(x = myX, y = "IsDepDelayed", 
                           training_frame = air.hex,
                           nfolds = 5, fold_assignment = 'Modulo', keep_cross_validation_predictions = TRUE,
                           model_id = "my_dl",
                           hidden=c(5))

    all_model_ids <- c(air.grid@model_ids, gbm@model_id, glm@model_id, dl@model_id)
    print("creating StackedEnsemble 2 of models: ")
    print(all_model_ids)
    stacker2 <- h2o.stackedEnsemble(x = myX, y = "IsDepDelayed", 
                                   training_frame = air.hex,
                                   model_id = "my_ensemble_2",
                                   base_models = all_model_ids)

    print("calling predict() on StackedEnsemble 2...")
    predictions = h2o.predict(stacker2, air.hex)  # training data
    print("predictions for ensemble 2 are in: ")
    print(h2o.getId(predictions))

}

doTest("XGBoost Grid Test: Airlines Smalldata", xgboost.random.grid.test)
