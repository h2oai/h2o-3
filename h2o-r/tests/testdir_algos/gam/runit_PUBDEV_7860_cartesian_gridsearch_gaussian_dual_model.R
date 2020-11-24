setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure gam will build the same model regardless of how gam columns are specified
test.model.gam.random.gridsearch.dual.modes <- function() {
    trainGaussian <- h2o.importFile(locate("smalldata/gam_test/synthetic_20Cols_gaussian_20KRows.csv"))
    trainGaussian$C3 <- h2o.asfactor(trainGaussian$C3)
    trainGaussian$C7 <- h2o.asfactor(trainGaussian$C7)
    trainGaussian$C8 <- h2o.asfactor(trainGaussian$C8)
    trainGaussian$C10 <- h2o.asfactor(trainGaussian$C10)
    xL <- c("c_0", "c_1", "c_2", "c_3", "c_4", "c_5", "c_6", "c_7", "c_8", "c_9", "C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", 
            "C9", "C10")
    yR = "response"
    
    #setup search criteria
    search_criteria <- list()
    search_criteria$strategy <- 'Cartesian'
    # setup hyper-parameter for gridsearch
    hyper_parameters <- list()
    hyper_parameters$lambda = c(1, 2)   
    
    # generate random hyper-parameter for gridsearch
    subspace1 <- list()
    subspace1$scale <- list(c(0.001, 0.001, 0.001), c(0.002, 0.002, 0.002))
    subspace1$num_knots <- list(c(5, 10, 12), c(6, 11, 13))
    subspace1$bs <- list(c(1, 1, 1), c(0, 1, 1))
    subspace1$gam_columns <- list(list("c_0", c("c_1", "c_2"), c("c_3", "c_4", "c_5")), 
                                  list("c_1", c("c_2", "c_3"), c("c_4", "c_5", "c_6")))
    hyper_parameters$subspaces <- list(subspace1)
    
    # setup hyper-parameter for gridsearch
    hyper_parameters2 <- list()
    hyper_parameters2$lambda <- c(1, 2)   
    
    # generate random hyper-parameter for gridsearch
    subspace2 <- list()
    subspace2$scale <- list(c(0.001, 0.001, 0.001), c(0.002, 0.002, 0.002))
    subspace2$num_knots <- list(c(5, 10, 12), c(6, 11, 13))
    subspace2$bs <- list(c(1, 1, 1), c(0, 1, 1))
    subspace2$gam_columns <- list(list(c("c_0"), c("c_1", "c_2"), c("c_3", "c_4", "c_5")), 
                                  list(c("c_1"), c("c_2", "c_3"), c("c_4", "c_5", "c_6")))
    hyper_parameters2$subspaces <- list(subspace2)
    
    gam_grid1 = h2o.grid("gam", grid_id="GAMModel1", x=xL, y=yR, training_frame=trainGaussian, family='gaussian',
                         hyper_params=hyper_parameters, search_criteria=search_criteria)
    gam_grid2 = h2o.grid("gam", grid_id="GAMModel2", x=xL, y=yR, training_frame=trainGaussian, family='gaussian',
                         hyper_params=hyper_parameters2, search_criteria=search_criteria)
    
    numModel <- length(gam_grid1@model_ids)
    for (index in c(1:numModel)) {
        print(index)
        model1 <- h2o.getModel(gam_grid1@model_ids[[index]])
        model2 <- h2o.getModel(gam_grid2@model_ids[[index]])
        coeff1 <- model1@model$coefficients
        coeff2 <- model2@model$coefficients
        # coefficients from both models should be the same
        compareResult <- sum(abs(coeff1-coeff2))
        expect_true(compareResult < 1e-10)
        
    }
}

doTest("General Additive Model test dual model specification with Gaussian family for cartesian gridsearch", test.model.gam.random.gridsearch.dual.modes)
