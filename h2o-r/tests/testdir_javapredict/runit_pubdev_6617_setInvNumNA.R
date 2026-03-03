setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

test.mojo.setInvNumNA <-
  function() {
    #----------------------------------------------------------------------
    # Run the test
    #----------------------------------------------------------------------
    training_frame <- h2o.importFile(locate("smalldata/glm_test/pubdev_6617_setInvNumNA_train.csv"))
    testModel <- h2o.importFile(locate("smalldata/glm_test/pubdev_6617_setInvNumNA_test_model.csv"))
    testPath <- locate("smalldata/glm_test/pubdev_6617_setInvNumNA_test.csv")
    params                  <- list()
    params$missing_values_handling <- "MeanImputation"
    params$training_frame <- training_frame
    params$x <- c("C1")
    params$y <- "C2"
    params$family <- "gaussian"
    modelAndDir<-buildModelSaveMojoGLM(params) # build the model and save mojo
    modelPred <- h2o.predict(modelAndDir$model, testModel) # predict with invalid row value replaced with mean value
    # get genmodel.jar pathname
    a = strsplit(modelAndDir$dirName, '/')
    endIndex <-(which(a[[1]]=="h2o-r"))-1
    genJar <-
      paste(a[[1]][1:endIndex], collapse='/')
    jarName <- paste(genJar, 'h2o-assemblies/genmodel/build/libs/genmodel.jar',sep='/')
    # generate zip file with path
    mojozip = paste(modelAndDir$model@model_id, "zip", sep='.')
    mojozipAll = paste(modelAndDir$dirName, mojozip, sep='/')
    # get mojo-model-predict
    mojoPredict = h2o.mojo_predict_csv(testPath, mojozipAll, genmodel_jar_path=jarName, verbose=TRUE, setInvNumNA=TRUE)
   # mojo predict and model predict should agree
    for (ind in c(1:5)) {
      expect_true(abs(mojoPredict[ind,1]-modelPred[ind,1])<1e-6)
    }
  }

doTest("Test mojo_predict_csv setInvNumNA", test.mojo.setInvNumNA)
