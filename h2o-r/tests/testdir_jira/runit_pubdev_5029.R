setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.5029 <- function() {

    test_file <- "private/test.csv.gz"
    df <- h2o.importFile(test_file)
    y <- "C785"
    x <- setdiff(names(df), y)
    df[,y] <- as.factor(df[,y])
    train <- df[1:1000,]

    train$weights <- as.h2o(as.integer(seq(1000) %% 5 > 0))

    model <- h2o.glm(x = x, y = y, training_frame = train, lambda_search = TRUE,
                     family = 'multinomial', alpha = 0, weights_column = "weights", seed = -1)
    expect_equal("GLM", class(model)) # any assertion will do fine
}

doTest("PUBDEV-5029: GLM crashes if there are too many active predictors", test.pubdev.5029)
