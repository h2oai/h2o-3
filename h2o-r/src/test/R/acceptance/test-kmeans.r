context("Kmeans")

library(h2o)

print(Sys.getenv("H2O_IP"))
print(Sys.getenv("H2O_PORT"))

conn <- h2o.init(ip=Sys.getenv("H2O_IP"), port=as.integer(Sys.getenv("H2O_PORT"), startH2O=FALSE))
hex <- as.h2o(conn, iris)

#model <- tryCatch({
#	h2o.kmeans(data=hex, centers=5)
#}, error = function(err) {
#	return(err)
#})

model <- h2o.kmeans(data=hex, centers=5)

if(is(model, "H2OKMeansModel")) {
	test_that("Correct # of centers returned: ", {
		expect_equal(5, length(model@model$clusters))
	})
} else {
	test_that("Input permutation x: ", fail(message=toString(model)))
}
