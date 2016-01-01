setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.hex.1613 <- function() {
    h2oTest.heading("BEGIN TEST")

    path <- h2oTest.locate("smalldata/logreg/prostate.csv")
    hex <- h2o.importFile(path, destination_frame="p.hex")

    m <- h2o.glm(x = 3:8, y = 2, family = "binomial", training_frame = hex)

    pred <- predict(m, hex)
    res <- h2o.performance(m)

    print(h2o.confusionMatrix(res))
    h2o.confusionMatrix(m, hex)
}

h2oTest.doTest("HEX-1613", test.hex.1613)
