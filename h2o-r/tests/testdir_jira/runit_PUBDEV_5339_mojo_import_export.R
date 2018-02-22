setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.mojo.import.export <- function() {
    prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")

    prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
    prostate.hex$RACE <- as.factor(prostate.hex$RACE)

    ntrees <- 100
    Log.info(paste("H2O GBM with parameters:\ndistribution = 'bernoulli', ntrees = ", ntrees, ", max_depth = 5, min_rows = 10, learn_rate = 0.1\n", sep = ""))
    model <- h2o.gbm(x = 3:9, y = "CAPSULE", training_frame = prostate.hex, distribution = "bernoulli", ntrees = ntrees, max_depth = 5, min_rows = 10, learn_rate = 0.1)

    tmpdir_name <- sprintf("%s/tmp_model_%s", sandbox(), as.character(Sys.getpid()))
    h2o.saveMojo(model, path = tmpdir_name, force = TRUE) # save mojo
    h2o.saveModel(model, path = tmpdir_name, force = TRUE) # save model to compare mojo/h2o predict offline

    filename = sprintf("%s/in.csv", tmpdir_name) # save the test dataset into a in.csv file.
    h2o.exportFile(prostate.hex, filename)
    twoFrames <- mojoH2Opredict(model, tmpdir_name, filename)

    compareFrames(twoFrames$h2oPredict,twoFrames$mojoPredict, prob=0.1, tolerance = 1e-4)
}

mojoH2Opredict<-function(model, tmpdir_name, filename) {
    newTest <- h2o.importFile(filename)
    predictions1 <- h2o.predict(model, newTest)

    a = strsplit(tmpdir_name, '/')
    endIndex <-(which(a[[1]]=="h2o-r"))-1
    genJar <-
    paste(a[[1]][1:endIndex], collapse='/')

    if (.Platform$OS.type == "windows") {
        cmd <-
        sprintf(
        "java -ea -cp %s/h2o-assemblies/genmodel/build/libs/genmodel.jar -Xmx4g -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --header --mojo %s/%s --input %s/in.csv --output %s/out_mojo.csv",
        genJar,
        tmpdir_name,
        paste(model_key, "zip", sep = '.'),
        tmpdir_name,
        tmpdir_name
        )
    } else {
        cmd <-
        sprintf(
        "java -ea -cp %s/h2o-assemblies/genmodel/build/libs/genmodel.jar -Xmx4g -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --header --mojo %s/%s --input %s/in.csv --output %s/out_mojo.csv --decimal",
        genJar,
        tmpdir_name,
        paste(model@model_id, "zip", sep = '.'),
        tmpdir_name,
        tmpdir_name
        )
    }
    safeSystem(cmd)  # perform mojo prediction
    predictions2 = h2o.importFile(paste(tmpdir_name, "out_mojo.csv", sep =
    '/'), header=T)

    return(list("h2oPredict"=predictions1, "mojoPredict"=predictions2))
}

doTest("Test export and import of GBM mojo", test.mojo.import.export)
