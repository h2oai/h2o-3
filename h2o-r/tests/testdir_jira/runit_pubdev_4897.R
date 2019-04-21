setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_4897 <- function() {
    data <- h2o.importFile(path = locate('smalldata/jira/pubdev_4897.csv'))
    model <- h2o.gbm(x = "Var2", y = "Var1", training_frame = data, ntrees = 50)
    pdf("plot")
    dev.control(displaylist="enable")
    h2o.partialPlot(object = model, data = data, cols = "Var2", plot = TRUE)
    recordedPlot <- recordPlot()
    dev.off()
    unlink("plot")
    recordedPlotAttributes <- capture.output(str(recordedPlot))
    xAxisAlignment = grep(pattern = "\\$ x   : num \\[1:3\\] 1 2 3", x = recordedPlotAttributes, ignore.case = TRUE)
    expect_true(length(xAxisAlignment) > 0)
}

doTest("PUBDEV-$4897: PDP use different order of categorical", test.pubdev_4897)