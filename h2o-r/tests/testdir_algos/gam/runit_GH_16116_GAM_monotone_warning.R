setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(data.table)

test.model.gam.monotone.splines <- function() {
    n <- 25000
    sum_insured <- seq(1,200000, length.out=n)
    d <- data.table(sum_insured=sum_insured, sqrt = sqrt(sum_insured), sine = sin(2*pi*sum_insured/40000))
    d[, sine := 0.3*sqrt*sine ,]
    d[, y := pmax(0,sqrt + sine) ,]
     # create frame knots
    knots1 <- c(0, 15000,30000, 50000,70000,90000,110000,130000,150000,170000,190000,200000)
    frame_Knots1 <- as.h2o(knots1)
    # import the dataset
    h2o_data <- as.h2o(d)
    # specify the knots array
    numKnots <- c(length(knots1))
    
    # Monotonic Model ==================================================================================================
    # build the GAM model and expect warning for not setting the non_negative parameters
    expect_warning(
        mono_model <- h2o.gam(
            x = 'sum_insured',
            y = 'y',
            training_frame = h2o_data,
            family = 'poisson',
            link = 'Log',
            gam_columns = c("sum_insured"),
            bs = c(2),
            splines_non_negative = c(T),
            scale = c(0.1),
            spline_orders = c(2),
            num_knots = numKnots,
            knot_ids = c(h2o.keyof(frame_Knots1)),
            lambda_search = TRUE
        ))
}

doTest("General Additive Model Monotone Splines Warning", test.model.gam.monotone.splines)
