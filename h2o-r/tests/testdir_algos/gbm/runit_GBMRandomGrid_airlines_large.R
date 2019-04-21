setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



gbm.random.grid.test <- function() {
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
      col_sample_rate_change_per_level = seq(0.9,1.1,0.01),                      

      ## search a large space of the number of min rows in a terminal node
      min_rows = 2^seq(0,log2(nrow(air.hex))-1,1),                                 

      ## search a large space of the number of bins for split-finding for continuous and integer columns
      nbins = 2^seq(4,10,1),                                                     

      ## search a large space of the number of bins for split-finding for categorical columns
      nbins_cats = 2^seq(4,12,1),                                                

      ## search a few minimum required relative error improvement thresholds for a split to happen
      min_split_improvement = c(0,1e-8,1e-6,1e-4),                               

      ## try all histogram types (QuantilesGlobal and RoundRobin are good for numeric columns with outliers)
      histogram_type = c("UniformAdaptive","QuantilesGlobal","RoundRobin")       
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

    air.grid <- h2o.grid("gbm", y = "IsDepDelayed", x = myX,
                         distribution="bernoulli",
                         seed=1234,
                         training_frame = air.hex,
                         hyper_params = hyper_params,
                         search_criteria = search_criteria,
                         nfolds = 5, fold_assignment = 'Modulo',
                         keep_cross_validation_predictions = TRUE)
    print(air.grid)
    expect_that(length(air.grid@model_ids) <= 5, is_true())

    stacker <- h2o.stackedEnsemble(x = myX, y = "IsDepDelayed", 
                                   training_frame = air.hex,
                                   model_id = "my_ensemble",
                                   base_models = air.grid@model_ids)

    predictions = h2o.predict(stacker, air.hex)  # training data
    print("predictions for ensemble are in: ")
    print(h2o.getId(predictions))
}

doTest("GBM Grid Test: Airlines Smalldata", gbm.random.grid.test)
