setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# error accessing archetypes: Error in res$model_metrics[[1L]] : subscript out of bounds
# make sure we pass this one after the fix.
test.glrm.pubdev.3778 <- function() {
  data <- data.frame('C1' = c(1, 1, 1, 1, 1),
                     'C2' = c(1, 8, 0, 9, 8),
                     'C3' = c(5, 1, 8, 4, 9),
                     'C4' = c(3, 4, 5, 4, 4),
                     'C5' = c(8, 1, 4, 9, 6))
  
  data2 <- data.frame('C2' = c(1, 8, 0, 9, 8),
                      'C3' = c(5, 1, 8, 4, 9),
                      'C4' = c(3, 4, 5, 4, 4),
                      'C5' = c(8, 1, 4, 9, 6))
  data <- as.h2o(data)
  data2 <- as.h2o(data2)
  
  # Build GLRM model
  glrm_model2 <- h2o.glrm(data2,  k = 2, ignore_const_cols = TRUE, init="User")
  glrm_model <- h2o.glrm(data,  k = 2, validation_frame = data, ignore_const_cols = TRUE, init="User")
  
  archetypes2 <- h2o.proj_archetypes(glrm_model2, data2)
  archetypes <- h2o.proj_archetypes(glrm_model, data)
  
}

doTest("GLRM Test: Iris with Various Transformations", test.glrm.pubdev.3778)
