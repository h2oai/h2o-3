setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This test is mainly written to compare R glm with H2O glm when we have interaction columns.  The coefficients
# should be very close between the two methods.
test.glm.interactions.lambda0 <- function() {
   data <- h2o.importFile(locate("smalldata/census_income/adult_data.csv"))
   temp <- data["hours-per-week"] / data["age"]
   names(temp) <- "y"
   dataCombined <- h2o.cbind(data, temp)
   dataR <- as.data.frame(dataCombined)

   glmH2O <- h2o.glm(x=c("income", "sex"), y="y", training_frame=dataCombined, interactions=c("income", "sex"), lambda=0)
   glmR <- lm(y ~income*sex+income+sex, data=dataR)
   coeffR <- glmR$coefficients
   coeffH2O <- glmH2O@model$coefficients
   rcoeffNames <- c("(Intercept)", "income>50K", "sexMale", "income>50K:sexMale")
   h2ocoeffNames <- c("Intercept", "income.>50K", "sex.Male", "income_sex.>50K_Male")
   
   # compare coefficient between R and H2O glm model.  They should be close
   for (ind in seq_len(length(rcoeffNames))) {
      expect_true(abs(coeffR[rcoeffNames[ind]][[1]]-coeffH2O[h2ocoeffNames[ind]][[1]]) < 1e-6)
   }
   expect_true(length(glmR$coefficients)==length(glmH2O@model$coefficients), "number of coeffcients between R and H2O are different")
}

doTest("Testing model interactions for GLM when Lambda=0.0", test.glm.interactions.lambda0)
