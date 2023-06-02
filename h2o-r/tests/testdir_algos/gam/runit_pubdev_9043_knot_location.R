setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.model.gam.knot.locations <- function() {
    data <- h2o.importFile(locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    data$C21 <- as.factor(data$C21)
    
    # Set the predictor and response columns
    predictors <- c("C11","C12","C13")
    response <- 'C21'
    gam_model <- h2o.gam(x = predictors,
                         y = response,
                         training_frame = data,
                         family = 'binomial',
                         gam_columns = predictors,
                         scale = c(1,0.01,0.1),
                         bs = c(2,0,1),
                         num_knots = c(10, 12, 13), store_knot_locations=TRUE)
    all_gam_knots <- h2o.get_knot_locations(gam_model)
    gam_knot_names <- gam_model@model$gam_knot_column_names
    knotC11 <- h2o.get_knot_locations(gam_model, gam_knot_names[1])
    compare_arrays(knotC11[[1]], all_gam_knots[[1]])
    knotC12 <- h2o.get_knot_locations(gam_model, gam_knot_names[2])
    compare_arrays(knotC12[[1]], all_gam_knots[[2]])
    knotC13 <- h2o.get_knot_locations(gam_model, gam_knot_names[3])
    compare_arrays(knotC13[[1]], all_gam_knots[[3]])
    gam_knots <- h2o.get_gam_knot_column_names(gam_model)
    expect_true(length(predictors) == length(gam_knot_names))
}

doTest("General Additive Model test to check knot outputs in model", test.model.gam.knot.locations)

