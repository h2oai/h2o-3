setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests ignore_constant_columns argument when used in conjunction with beta constraints ######
test.GLM.bc.ignore.constants <- function() {
  
  h2oTest.logInfo("Import Prostate Dataset...")
  prostate <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"), destination_frame="prostate")
  h2oTest.logInfo("Create a few artificial constant column...")
  prostate$CONSTANT_0 <- 0
  prostate$CONSTANT_1 <- 1
  prostate$CONSTANT_5 <- 5

  h2oTest.logInfo("Set the independent and dependent variables...")
  p_y <- "CAPSULE"
  p_x <- setdiff(names(prostate), c(p_y, "ID"))
  p_x_ <- setdiff(p_x, c("CONSTANT_0", "CONSTANT_1", "CONSTANT_5"))
  p_n <- length(p_x)
  
  con <- data.frame( names = p_x , 
                     lower_bounds = rep(-10000, time = p_n),
                     upper_bounds = rep(10000, time = p_n),
                     beta_given = rep(1, time = p_n),
                     rho = rep(0.2, time = p_n))
  
  h2oTest.logInfo("Check ignore constant columns w/o beta constraints in GLM...")
  m1 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, standardize = T)
  m3 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, standardize = F)
#   m1 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = T, standardize = T)
#   m2 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = F, standardize = T)
#   m3 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = T, standardize = F)
#   m4 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = F, standardize = F)
  if( !all( names(m1@model$coefficients)[-1] %in% p_x_) ) stop("Coefficients do not match up!") 
#   if( !all( names(m2@model$coefficients)[-1] %in% p_x ) ) stop("Coefficients do not match up!") 
  if( !all( names(m3@model$coefficients)[-1] %in% p_x_) ) stop("Coefficients do not match up!") 
#   if( !all( names(m4@model$coefficients)[-1] %in% p_x ) ) stop("Coefficients do not match up!") 
#   if( !checkEqualsNumeric(m1@model$coefficients[p_x_], m2@model$coefficients[p_x_]) )
#     stop("Nonzero coefficients for constant columns when Standardization = T")

  h2oTest.logInfo("Check ignore constant columns w/ beta constraints in GLM...")
  m5 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, standardize = T, beta_constraints = con)
  m7 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, standardize = F, beta_constraints = con)
#   m5 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = T, standardize = T, beta_constraints = con)
#   m6 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = F, standardize = T, beta_constraints = con)
#   m7 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = T, standardize = F, beta_constraints = con)
#   m8 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = F, standardize = F, beta_constraints = con)
  if( !all( names(m5@model$coefficients)[-1] %in% p_x_) ) stop("Coefficients do not match up!") 
# if( !all( names(m6@model$coefficients)[-1] %in% p_x ) ) stop("Coefficients do not match up!") 
  if( !all( names(m7@model$coefficients)[-1] %in% p_x_) ) stop("Coefficients do not match up!") 
# if( !all( names(m8@model$coefficients)[-1] %in% p_x ) ) stop("Coefficients do not match up!") 
 
  h2oTest.logInfo("Check ignore constant columns w/o constant columns in beta constraints in GLM")
  m9 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, standardize = T, beta_constraints = con[1:7,])
  m11 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, standardize = F, beta_constraints = con[1:7,])
#   m9 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = T, standardize = T, beta_constraints = con[1:7,])
#   m10 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = F, standardize = T, beta_constraints = con[1:7,])
#   m11 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = T, standardize = F, beta_constraints = con[1:7,])
#   m12 <- h2o.glm(x = p_x, y = p_y, training_frame = prostate, ignore_const_cols = F, standardize = F, beta_constraints = con[1:7,])
  if( !all( names(m9@model$coefficients)[-1] %in% p_x_) ) stop("Coefficients do not match up!") 
# if( !all( names(m10@model$coefficients)[-1] %in% p_x ) ) stop("Coefficients do not match up!") 
  if( !all( names(m11@model$coefficients)[-1] %in% p_x_) ) stop("Coefficients do not match up!") 
# if( !all( names(m12@model$coefficients)[-1] %in% p_x ) ) stop("Coefficients do not match up!") 
#   if( !checkEqualsNumeric(m9@model$coefficients[p_x_], m10@model$coefficients[p_x_]) )
#     stop("Nonzero coefficients for constant columns when Standardization = T")
  
}

h2oTest.doTest("GLM Beta Constraints Ignore Constants Test : ", test.GLM.bc.ignore.constants)
