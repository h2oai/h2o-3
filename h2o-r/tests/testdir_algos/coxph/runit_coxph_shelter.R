setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.CoxPH.heart <- function() {
    shelter <- read.csv(file =locate("smalldata/coxph_test/shelter.csv"))
    coxph_features <- c("intake_condition","intake_type", "animal_breed", "chip_status", "surv_hours", "event")
    
    shelter.df <- shelter[,coxph_features]
    shelter.hex <- as.h2o(shelter.df)

    shelter.hex$event = as.factor(shelter.hex$event)
    shelter.hex$intake_condition = as.factor(shelter.hex$intake_condition)
    shelter.hex$intake_type = as.factor(shelter.hex$intake_type)
    shelter.hex$animal_breed = as.factor(shelter.hex$animal_breed)
    shelter.hex$chip_status = as.factor(shelter.hex$chip_status)
    
    
    coxph.h2o <- h2o.coxph(training_frame = shelter.hex,
                           stop_column = "surv_hours",
                           event_column = "event",
                           stratify_by = "intake_type")
    
    coxph.r <- survival::coxph(Surv(surv_hours, event) ~ intake_condition + animal_breed + chip_status + strata(intake_type), 
                               data = shelter.df)


    output <- coxph.h2o@model
    coefs <- output$coefficients
    coefficients.h2o <- coefs$coefficients
    names(coefficients.h2o) <- gsub("\\.", "", coefs$names)
    
    expect_equal(names(coefficients.h2o), names(coxph.r$coefficients))
    expect_equal(coefficients.h2o, coxph.r$coefficients, tolerance = 1e-5, scale = 1)
    expect_equal(output$var_coef, coxph.r$var, tolerance = 1e-5, scale = 1)
    expect_equal(output$loglik, tail(coxph.r$loglik, 1), tolerance = 1e-5, scale = 1)
    expect_equal(output$score, coxph.r$score, tolerance = 1e-5, scale = 1)
    expect_true(output$iter >= 1)
    
    expect_equal(output$n, coxph.r$n)
    expect_equal(output$total_event, coxph.r$nevent)

    expect_equal(output$wald.test, coxph.r$wald_test, tolerance = 1e-8)
     
    # smoke tests
    print(extractAIC(coxph.h2o))
    print(logLik(coxph.h2o))
    print(vcov(coxph.h2o))
}

doTest("CoxPH: Heart Test", test.CoxPH.heart)
