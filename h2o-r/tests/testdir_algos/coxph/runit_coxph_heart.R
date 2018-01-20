setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.heart <- function() {
    heart.hex <- h2o.importFile(locate("smalldata/coxph_test/heart.csv"))
    heart.df <- as.data.frame(heart.hex)

    coxph.h2o <- h2o.coxph(x="age", event_column="event", start_column="start", stop_column="stop", training_frame=heart.hex)
    coxph.r <- survival::coxph(Surv(start, stop, event) ~ age, data = heart.df)


    output <- coxph.h2o@model

    names(output$coef) <- output$coef_names
    # Note: only for this particular test (nums only)
    names(output$x_mean_num) <- output$coef_names

    expect_equal(output$coef, coxph.r$coefficients, tolerance = 1e-8)
    expect_equal(output$var_coef, coxph.r$var, tolerance = 1e-8)
    expect_equal(output$loglik, tail(coxph.r$loglik, 1), tolerance = 1e-8)
    expect_equal(output$score, coxph.r$score, tolerance = 1e-8)
    expect_true(output$iter >= 1)
    expect_equal(output$x_mean_num, coxph.r$means, tolerance = 1e-8)

    expect_equal(output$n, coxph.r$n)
    expect_equal(sum(output$n_event), coxph.r$nevent)


    expect_equal(output$wald.test, coxph.r$wald_test, tolerance = 1e-8)
}

doTest("CoxPH: Heart Test", test.CoxPH.heart)