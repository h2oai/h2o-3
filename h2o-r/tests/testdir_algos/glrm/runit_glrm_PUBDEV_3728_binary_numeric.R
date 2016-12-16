setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This test is written to check and make sure GLRM will run with logistic loss if the binary data is stored as
# binary level factors or binary data read in as numeric but only contains two values.
test.glrm.pubdev.3728 <- function() {
  prostate.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate_cat.csv"), destination_frame= "prostate.hex",
  na.strings = rep("NA", 8))
  loss_all <- c("Logistic", "Quadratic", "Categorical", "Categorical", "Logistic", "Quadratic", "Quadratic", "Quadratic")
  # Boolean loss does not work on categorical binary columns
  h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = loss_all)

  # Boolean loss does not work on numeric binary columns
  prostate.hex$CAPSULE <- h2o.ifelse(prostate.hex$CAPSULE == "No", 0, 1)
  prostate.hex$DCAPS <- h2o.ifelse(prostate.hex$DCAPS == "No", 0, 1)

  h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = loss_all)
  }

doTest("PUBDEV-3728: GLRM logistic loss with binary data as factors or numeric", test.glrm.pubdev.3728)
