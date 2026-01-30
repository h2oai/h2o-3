setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.GLM.offset.fractionalbinomial <- function() {
  hf <- h2o.importFile(locate("smalldata/glm_test/fraction_binommialOrig.csv"))
  offset <- random_dataset_real_only(h2o.nrow(hf), 1, realR = 3)
  hf <- h2o.cbind(hf, offset)
  x <- c("log10conc")
  y <- "y"
  xOffset <- c("log10conc", "C1")

  params                  <- list()
  params$training_frame <- hf
  params$x <- x
  params$y <- y
  params$family <- "fractionalbinomial"
  params$offset_column <- "C1"
  params$alpha <- 0
  params$lambda <- 0

  modelAndDir<-buildModelSaveMojoGLM(params) # build the model and save mojo
  filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
  h2o.downloadCSV(hf[1:100, xOffset], filename)
  twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, col.types=c("numeric", "numeric", "numeric")) # perform H2O and mojo prediction and return frames
  h2o.downloadCSV(twoFrames$h2oPredict, sprintf("%s/h2oPred.csv", modelAndDir$dirName))
  h2o.downloadCSV(twoFrames$mojoPredict, sprintf("%s/mojoOut.csv", modelAndDir$dirName))
  compareFrames(twoFrames$h2oPredict,twoFrames$mojoPredict, prob=1, tolerance = 1e-6)
}

doTest("GLM Test: fractionalbinomial GLM Mojo with offset", test.GLM.offset.fractionalbinomial)
