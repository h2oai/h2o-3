setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.save.grid.with.cv <- function() {
    train_data <- data.frame(y = rnorm(100,1,2),
                             x1 = rnorm(100,5,5),
                             x2 = rnorm(100,4,4),
                             x3 = rnorm(100,3,3),
                             x4 = rnorm(100,2,2))
    
    params <- list(max_depth = seq(1, 1, 6),
                   sample_rate = seq(0.2, 1.0, 0.1))
    search_criteria <- list(strategy = "RandomDiscrete", max_models = 3, seed = 42)
    
    train_h2o <- as.h2o(train_data,destination_frame = "Train")
    
    gbm_grid <- h2o.grid("gbm", y = "y", x = c("x1", "x2", "x3", "x4"),
                         training_frame = train_h2o,
                         grid_id = "gbm_grid", nfolds = 2, ntrees = 5, seed = 42,
                         keep_cross_validation_predictions = TRUE,
                         hyper_params = params,
                         search_criteria = search_criteria)
    
    saved_path <- h2o.saveGrid(grid_directory = tempdir(), grid_id = gbm_grid@grid_id, export_cross_validation_predictions = TRUE)

    # Wipe the cloud to simulate cluster restart - the models will no longer be available
    h2o.removeAll()
  
    # Load the Grid back in with all the models checkpointed
    grid <- h2o.loadGrid(saved_path)
  
    # Load the dataset in once again, as it was removed as the cloud was wiped
    train_h2o_reloaded <- as.h2o(train_data)

    # No error produced when building the SE model
    h2o.stackedEnsemble(y = "y", x = c("x1","x2","x3","x4"), training_frame = train_h2o_reloaded,
                        base_models = grid@model_ids)
}

doTest("Tests that grid can be exported with CV holdout predictions to further build a SE model", test.save.grid.with.cv)
