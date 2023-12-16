setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(uplift)

check.uplift.grid <- function() {
    data <- sim_pte(n = 2000, p = 6, rho = 0, sigma = sqrt(2), beta.den = 4)
    print(summary(data))
    
    data$treat <- ifelse(data$treat == 1, 1, 0)
    data$treat <- as.factor(data$treat)
    data$y <- as.factor(data$y)
    data <- as.h2o(data)
    
    x <- c("X1", "X2", "X3", "X4", "X5", "X6")
    y <- "y"
    treat <- "treat"

    pretty.list <- function(ll) {
        str <- lapply(ll, function(x) { paste0("(", paste(x, collapse = ","), ")", sep = "") })
        paste(str, collapse = ",")
    }
    ntrees_opts <- c(1, 5)
    max_depth_opts <- c(2, 5)
    uplift_metric_opts <- c("KL", "Euclidean", "ChiSquared")
    size_of_hyper_space <- length(ntrees_opts) * length(max_depth_opts) * length(uplift_metric_opts)

    hyper_parameters <- list(ntrees=ntrees_opts, max_depth=max_depth_opts, uplift_metric=uplift_metric_opts)
    Log.info(paste("UpliftDRF grid with the following hyper_parameters:", pretty.list(hyper_parameters)))
    gg <- h2o.grid("upliftdrf", grid_id="upliftdrf_grid_test", x=x, y=y, training_frame=data, treatment_column=treat, hyper_params=hyper_parameters)

    # Get models
    gg_models <- lapply(gg@model_ids, function(mid) {
        model <- h2o.getModel(mid)
    })
    # Check expected number of models
    print(paste(length(gg@model_ids), "==", size_of_hyper_space))
    expect_equal(length(gg_models), size_of_hyper_space)

    # Check parameters coverage
    # ntrees
    expect_model_param(gg_models, "ntrees", ntrees_opts)

    # Learn rate
    expect_model_param(gg_models, "max_depth", max_depth_opts)
    
    # uplift metric
    expect_model_param(gg_models, "uplift_metric", uplift_metric_opts)

    cat("\n\n Grid search results:")
    print(gg)

    # Test grid sorting
    ascending <- h2o.getGrid(grid_id=gg@grid_id, sort_by="auuc", decreasing=FALSE)
    descending <- h2o.getGrid(grid_id=gg@grid_id, sort_by="auuc", decreasing=TRUE)
    
    ascending_model_ids <- ascending@model_ids
    descending_model_ids <- descending@model_ids

    expect_equal(length(ascending_model_ids), length(descending_model_ids))
    expect_equal(length(ascending_model_ids), size_of_hyper_space)
    expect_equal(rev(ascending_model_ids), descending_model_ids)
}

doTest("UpliftDRF Grid Search: iteration over parameters", check.uplift.grid)

