context("prcomp")

library(h2o)
conn <- h2o.init(ip=Sys.getenv("H2O_IP"), port=as.integer(Sys.getenv("H2O_PORT"), startH2O=FALSE))

ausPath <- system.file("extdata", "australia.csv", package="h2o")
australia.hex <- h2o.importFile(conn, path = ausPath)

model <- tryCatch({
	h2o.prcomp(data = australia.hex, standardize = TRUE)
}, error = function(err) {
	return(err)
})

if(is(model, "H2OPCAModel")) {
	test_that("Correct # components returned: ", {
		expect_equal(8, length(model@model$sdev))
	})
} else {
	test_that("Input permutation foo: ", fail(message=toString(model)))
}
