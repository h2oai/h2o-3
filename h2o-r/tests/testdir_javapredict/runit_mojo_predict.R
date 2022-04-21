setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

genmodel_name <- 'h2o-genmodel.jar'

download.mojo <- function(model, mojo_zip_dir, genmodel_path=NULL, genmodel_name=NULL) {
    mojo_zip_dir <- normalizePath(mojo_zip_dir)

    print(paste0("Downloading MOJO to ", mojo_zip_dir))
    if (is.null(genmodel_path) && is.null(genmodel_name)) {
        mojo_file <- h2o.download_mojo(model, path=mojo_zip_dir, get_genmodel_jar=T)
        genmodel_path <- mojo_zip_dir
        genmodel_name <- "h2o-genmodel.jar"
    } else {
        mojo_file <- h2o.download_mojo(model, path=mojo_zip_dir, get_genmodel_jar=T, genmodel_name, genmodel_path)
    }

    model_zip_path <- file.path(mojo_zip_dir, mojo_file)

    expect_true(file.exists(model_zip_path), paste0('mojo zip not found at ', model_zip_path))
    expect_true(file.exists(file.path(genmodel_path, genmodel_name)),
                paste0('genmodel jar not found at ', file.path(genmodel_path, genmodel_name)))
    return(model_zip_path)
}

test.mojo_predict_api_test <- function() {
    prostate <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))
    splits <- h2o.splitFrame(prostate, ratios=c(0.7), seed=0)
    train <- splits[[1]]
    test <- splits[[2]]

    input_csv <- sprintf("%s/in.csv", sandbox())
    output_csv <- sprintf("%s/prediction.csv", sandbox())
    h2o.exportFile(test[1,], input_csv)

    prostate[,1] <- as.factor(prostate[,1])
    regression_gbm1 <- h2o.gbm(x=3:9, y="CAPSULE", training_frame=train)
    model_zip_path <- download.mojo(regression_gbm1, normalizePath(sandbox()))
    print(paste0("MOJO saved at ", model_zip_path))

    # test that we can predict using default paths
    h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path, verbose=TRUE)
    h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path,
                         genmodel_jar_path=file.path(sandbox(), genmodel_name), verbose=TRUE)
    expect_true(file.exists(output_csv), paste0('output csv cannot be found at ', output_csv))
    file.remove(model_zip_path)
    file.remove(file.path(sandbox(), genmodel_name))
    file.remove(output_csv)
    other_sandbox_dir <- normalizePath(file.path(sandbox(), Sys.getpid()), mustWork=FALSE)
    dir.create(other_sandbox_dir, showWarnings=FALSE)
    tryCatch(
        {
            download.mojo(regression_gbm1, normalizePath(sandbox()),
                          genmodel_path = other_sandbox_dir, genmodel_name = "h2o-genmodel-custom.jar")
            genmodel_jar <- file.path(other_sandbox_dir, 'h2o-genmodel-custom.jar')
            expect_true(file.exists(model_zip_path))
            expect_true(file.exists(genmodel_jar))

            expect_error(h2o_utils.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path, verbose=TRUE))

            expect_false(file.exists(file.path(other_sandbox_dir, 'h2o-genmodel.jar')), paste0("There should be no h2o-genmodel.jar at ", other_sandbox_dir))
            expect_false(file.exists(output_csv))

            h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path, genmodel_jar_path=genmodel_jar, verbose=TRUE)
            expect_true(file.exists(output_csv))

            file.remove(output_csv)

            output_csv <- file.path(other_sandbox_dir, "out.prediction")

            h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=model_zip_path, genmodel_jar_path=genmodel_jar, verbose=TRUE, output_csv_path=output_csv)
            expect_true(file.exists(output_csv))
            file.remove(model_zip_path)
            file.remove(genmodel_jar)
            file.remove(output_csv)
        },
        finally={
            unlink(other_sandbox_dir, recursive=TRUE)
        }
    )

    file.remove(input_csv)
}

test.mojo_predict_csv <- function() {
    mojo_file_name <- "prostate_gbm_model.zip"
    mojo_zip_path <- file.path(sandbox(), mojo_file_name)

    prostate <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))

    splits <- h2o.splitFrame(prostate, ratios=0.7, seed=0)
    train <- splits[[1]]
    test <- splits[[2]]

    # Getting first row from test data frame
    pdf <- test[2,]
    input_csv <- sprintf("%s/in.csv", sandbox())
    output_csv <- sprintf("%s/prediction.csv", sandbox())
    h2o.exportFile(pdf, input_csv)

    # =================================================================
    # Regression
    # =================================================================
    regression_gbm1 <- h2o.gbm(x=3:9, y=2, training_frame=train, distribution='gaussian')
    pred_reg <- predict(regression_gbm1, pdf)
    p1 <- pred_reg[1, 1]
    print(paste0("Regression prediction: ", p1))

    mojo_zip_path <- download.mojo(regression_gbm1, sandbox())

    prediction_result <- h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=mojo_zip_path, output_csv_path=output_csv)
    print(paste0("Prediction result: ", prediction_result))
    expect_equal(p1, prediction_result$predict[[1]],
                 info="expected predictions to be the same for binary and MOJO model for regression",
                 check.attributes = FALSE)

    # =================================================================
    # Binomial
    # =================================================================
    train[,2] <- as.factor(train[,2])
    bernoulli_gbm1 <- h2o.gbm(x=3:8, y=2, training_frame=train, distribution='bernoulli')
    pred_bin <- predict(bernoulli_gbm1, pdf)

    binary_prediction_0 <- pred_bin$p0[1, 1]
    binary_prediction_1 <- pred_bin$p1[1, 1]
    print(paste0("Binomial prediction: p0: ", binary_prediction_0))
    print(paste0("Binomial prediction: p1: ", binary_prediction_1))

    mojo_zip_path <- download.mojo(bernoulli_gbm1, sandbox())

    prediction_result <- h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=mojo_zip_path, output_csv_path=output_csv)
    mojo_prediction_0 <- prediction_result$p0
    mojo_prediction_1 <- prediction_result$p1
    print(paste0("Binomial prediction: p0: ", mojo_prediction_0))
    print(paste0("Binomial prediction: p1: ", mojo_prediction_1))

    expect_equal(binary_prediction_0, mojo_prediction_0,
                 info="expected predictions to be the same for binary and MOJO model for Binomial - p0",
                 check.attributes = FALSE)
    expect_equal(binary_prediction_1, mojo_prediction_1,
                 info="expected predictions to be the same for binary and MOJO model for Binomial - p1",
                 check.attributes = FALSE)

    file.remove(input_csv)
    # =================================================================
    # Multinomial
    # =================================================================
    iris <- h2o.importFile(locate("smalldata/iris/iris.csv"))

    splits <- h2o.splitFrame(iris, ratios=0.9, seed=0)
    train <- splits[[1]]
    test <- splits[[2]]

    # Getting first row from test data frame
    pdf <- test[2,]
    input_csv <- sprintf("%s/in.csv", sandbox())
    output_csv <- sprintf("%s/prediction.csv", sandbox())
    h2o.exportFile(pdf, input_csv)

    multi_gbm <- h2o.gbm(x=c('C1', 'C2', 'C3', 'C4'), y='C5', training_frame=train)

    pred_multi <- predict(multi_gbm, pdf)
    print(pred_multi)
    multinomial_prediction_1 <- pred_multi[1, 2]
    multinomial_prediction_2 <- pred_multi[1, 3]
    multinomial_prediction_3 <- pred_multi[1, 4]
    print(paste0("Multinomial prediction (Binary): p0: ", multinomial_prediction_1))
    print(paste0("Multinomial prediction (Binary): p1: ", multinomial_prediction_2))
    print(paste0("Multinomial prediction (Binary): p2: ", multinomial_prediction_3))

    mojo_zip_path <- download.mojo(multi_gbm, sandbox())

    prediction_result <- h2o.mojo_predict_csv(input_csv_path=input_csv, mojo_zip_path=mojo_zip_path, output_csv_path=output_csv)

    mojo_prediction_1 <- prediction_result[1,2]
    mojo_prediction_2 <- prediction_result[1,3]
    mojo_prediction_3 <- prediction_result[1,4]
    print(paste0("Multinomial prediction (MOJO): p0: ", mojo_prediction_1))
    print(paste0("Multinomial prediction (MOJO): p1: ", mojo_prediction_2))
    print(paste0("Multinomial prediction (MOJO): p2: ", mojo_prediction_3))

    expect_equal(multinomial_prediction_1, mojo_prediction_1,
                 info="expected predictions to be the same for binary and MOJO model for Multinomial - p0",
                 check.attributes = FALSE)
    expect_equal(multinomial_prediction_2, mojo_prediction_2,
                 info="expected predictions to be the same for binary and MOJO model for Multinomial - p1",
                 check.attributes = FALSE)
    expect_equal(multinomial_prediction_3, mojo_prediction_3,
                 info="expected predictions to be the same for binary and MOJO model for Multinomial - p2",
                 check.attributes = FALSE)

    file.remove(input_csv)
    file.remove(output_csv)
}

test.mojo_predict_df <- function() {
    prostate <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))

    splits <- h2o.splitFrame(prostate, ratios=0.7, seed=0)
    train <- splits[[1]]
    test <- splits[[2]]

    # Getting first row from test data frame
    pdf <- test[2,]
    input_csv <- sprintf("%s/in.csv", sandbox())
    h2o.exportFile(pdf, input_csv)

    train[,2] <- as.factor(train[,2])
    model <- h2o.gbm(x=3:9, y=2, training_frame=train, distribution='bernoulli')

    h2o_prediction <- predict(model, pdf)

    # download mojo
    model_zip_path <- download.mojo(model, sandbox())
    expect_true(file.exists(model_zip_path))

    frame <- read.csv(input_csv)
    mojo_prediction <- h2o.mojo_predict_df(frame=frame, mojo_zip_path=model_zip_path)
    print(mojo_prediction)
    print(paste0("Binomial Prediction (Binary) - p0: ", h2o_prediction[1,2]))
    print(paste0("Binomial Prediction (Binary) - p1: ", h2o_prediction[1,3]))
    print(paste0("Binomial Prediction (MOJO) - p0: ", mojo_prediction$p0))
    print(paste0("Binomial Prediction (MOJO) - p1: ", mojo_prediction$p1))
    expect_equal(h2o_prediction[1,2], mojo_prediction$p0,
                 info="expected predictions to be the same for binary and MOJO model - p0",
                 check.attributes = FALSE)
    expect_equal(h2o_prediction[1,3], mojo_prediction$p1,
                 info="expected predictions to be the same for binary and MOJO model - p1",
                 check.attributes = FALSE)

    file.remove(input_csv)
}

test.mojo_predict_suite <- function() {
    test.mojo_predict_api_test()
    test.mojo_predict_csv()
    test.mojo_predict_df()
}

doTest("Test mojo_predict", test.mojo_predict_suite)
