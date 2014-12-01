context("GBM")

library(h2o)
conn <- h2o.init(ip=Sys.getenv("H2O_IP"), port=as.integer(Sys.getenv("H2O_PORT"), startH2O=FALSE))

ausPath <- system.file("extdata", "australia.csv", package="h2o")
australia.hex <- h2o.importFile(conn, path = ausPath)

independent <- c("premax", "salmax","minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs")
dependent <- "runoffnew"

model <- tryCatch({
	h2o.gbm(y=dependent, x=independent, data=australia.hex, ntrees=20, max_depth=3, min_rows=2, learn_rate=0.2)
}, error = function(err) {
	return(err)
})

if(is(model, "H2OGBMModel")) {
	test_that("Correct # of trees grown: ", {
		expect_equal(20, length(model@model$ntrees))
	})
} else {
	test_that("Input permutation foo: ", fail(message=toString(model)))
}
