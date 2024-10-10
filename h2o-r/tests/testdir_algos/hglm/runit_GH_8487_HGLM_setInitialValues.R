setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# We build two HGLM models, one with user defined initial values, one without.  However, since we use the same initial values
# we expected the model metrics to be the same.
##

test.HGLMData1 <- function() {
  tol = 1e-4
  h2odata <- h2o.importFile(locate("smalldata/glm_test/semiconductor.csv"))
  h2odata$Device <- h2o.asfactor(h2odata$Device)
  yresp <- "y"
  xlist <- c("x1", "x3", "x5", "x6")
  z <- c(1)
  m1H2O <- h2o.glm(x=xlist, y=yresp, family="gaussian", rand_family = c("gaussian"), rand_link=c("identity"), 
                    training_frame=h2odata, HGLM=TRUE, random_columns=z, calc_like=TRUE)
  modelMetrics = h2o.HGLMMetrics(m1H2O) # grab HGLM model metrics
  
  initialVs = c(0.001929687,0.002817188,-0.001707812,-0.003889062,0.010685937,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0.1,0.1)
  m2H2OInitialValues <- h2o.glm(x=xlist, y=yresp, family="gaussian", rand_family = c("gaussian"), rand_link=c("identity"), 
                                training_frame=h2odata, HGLM=TRUE, random_columns=z, calc_like=TRUE, startval=initialVs)
  rModelMetrics = h2o.HGLMMetrics( m2H2OInitialValues)
  
  # compare H2O output with no startval and with startval assigned by user to be the same as the machine generated ones
  expect_true(abs(rModelMetrics$hlik-modelMetrics$hlik)<tol, "R hlik and H2O-3 hlik differ too much.")
  expect_true(abs(rModelMetrics$pvh-modelMetrics$pvh)<tol, "R pvh and H2O-3 pvh differ too much.")
  expect_true(abs(rModelMetrics$pbvh-modelMetrics$pbvh)<tol, "R pbvh and H2O-3 pbvh differ too much.")
  expect_true(abs(rModelMetrics$caic-modelMetrics$caic)<tol, "R caic and H2O-3 caic differ too much.")
  compare_arrays(rModelMetrics$fixef, modelMetrics$fixef, tol)
  compare_arrays(rModelMetrics$ranef, modelMetrics$ranef, tol)
  expect_true(abs(rModelMetrics$varfix-modelMetrics$varfix)<tol, "R varfix and H2O-3 varfix differ too much.")
  expect_true(abs(rModelMetrics$varranef-modelMetrics$varranef)<tol, "R varranef and H2O-3 varranef differ too much.")
  compare_arrays(rModelMetrics$sefe, modelMetrics$sefe, tol)
  compare_arrays(rModelMetrics$sere, modelMetrics$sere, tol)
  expect_true(abs(rModelMetrics$dfrefe-modelMetrics$dfrefe)<tol, "R dfrefe and H2O-3 dfrefe differ too much.")
  compare_arrays(rModelMetrics$summvc1, modelMetrics$summvc1, tol)
  compare_arrays(rModelMetrics$summvc2, modelMetrics$summvc2, tol)
 }

doTest("Comparison of H2O HGLM with and without initial values", test.HGLMData1)


