setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

heading("BEGIN TEST")
conn <- new("H2OConnection", ip=myIP, port=myPort)

path = locate("smalldata/iris/iris_wheader.nonspd.csv")
iris.hex = h2o.importFile( path, destination_frame="iris.hex")

expect_warning(h2o.glm(x = c(1:4,6:8), y = "class_REC", training_frame = iris.hex, family = "binomial", lambda = 0))
expect_warning(h2o.glm(x = c(1:4,6:8), y = "class_REC", training_frame = iris.hex, family = "binomial", lambda = c(0,1e-5,0.1)))

PASS_BANNER()
