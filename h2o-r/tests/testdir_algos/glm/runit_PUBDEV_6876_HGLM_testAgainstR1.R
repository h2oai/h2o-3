setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Copy example from HGLM package and compare results from our implementation.
##

test.HGLMData1 <- function() {
  tol = 2e-2
  h2odata <- h2o.importFile(locate("smalldata/glm_test/semiconductor.csv"))
  h2odata$Device <- h2o.asfactor(h2odata$Device)
  yresp <- "y"
  xlist <- c("x1", "x3", "x5", "x6")
  z <- c(1)
  m11H2O <- h2o.glm(x=xlist, y=yresp, family="gaussian", rand_family = c("gaussian"), rand_link=c("identity"), 
                    training_frame=h2odata, HGLM=TRUE, random_columns=z, calc_like=TRUE)
  print(m11H2O)
  summary(m11H2O)
  modelMetrics = h2o.HGLMMetrics(m11H2O) # grab HGLM model metrics
  
  # correct R outputs
  rModelMetrics <- list(hlik = 363.6833,
  pvh = 272.1705,
  pbvh =  241.1754,
  caic= -550.9321,
  fixef = c(0.001929687,0.002817188,-0.001707813,-0.003889063, 0.010685938),
  ranef = c( 0.0033338907,0.0004762701,-0.0029608332,-0.0018242513,-0.0003195859,0.0015680851,
             0.0002648708,0.0038885029,0.0010084988,-0.0027494339,0.0022346145,0.0004812442,
             -0.0018938887,-0.0031647713,-0.0016675671,0.0013243542),
  varfix = 0.00000862277,
  varranef = 0.000008353105,
  sefe = c(0.0008120345,0.0008120345,0.0008120345,0.0008120345,0.0008120345),
  sere = c(0.001949972,0.001949972,0.001949972,0.001949972,0.001949972,0.001949972,0.001949972, 0.001949972,
           0.001949972,0.001949972,0.001949972,0.001949972,0.001949972,0.001949972,0.001949972,0.001949972),
  dfrefe = 50,
  summvc1 = c(-11.6611042,0.1995106),
  summvc2 = c(-11.6928772,0.4779708))

  # compare H2O output with R outputs
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

doTest("Comparison of H2O to R with HGLM 1", test.HGLMData1)


