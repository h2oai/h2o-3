setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.GLM.offset.tweedie <- function() {
  hf <- h2o.importFile(locate("smalldata/prostate/prostate_complete.csv.zip"))
  offset <- random_dataset_real_only(h2o.nrow(hf), 1, realR = 3)
  hf <- h2o.cbind(hf, offset)
  x <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")
  y <- "CAPSULE"
  xOffset <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON", "C1")

  params                  <- list()
  params$training_frame <- hf
  params$x <- x
  params$y <- y
  params$family <- "tweedie"
  params$link <- "tweedie"
  params$offset_column <- "C1"
  params$alpha <- 0.5
  params$lambda <- 0

  modelAndDir<-buildModelSaveMojoGLM(params) # build the model and save mojo
  filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
  h2o.downloadCSV(hf[1:100, xOffset], filename)
  twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, col.types=c("numeric", "numeric", "numeric")) # perform H2O and mojo prediction and return frames
  h2o.downloadCSV(twoFrames$h2oPredict, sprintf("%s/h2oPred.csv", modelAndDir$dirName))
  h2o.downloadCSV(twoFrames$mojoPredict, sprintf("%s/mojoOut.csv", modelAndDir$dirName))
  compareFrames(twoFrames$h2oPredict,twoFrames$mojoPredict, prob=1, tolerance = 1e-6)
}

doTest("GLM Test: tweedie GLM Mojo with offset", test.GLM.offset.tweedie)
