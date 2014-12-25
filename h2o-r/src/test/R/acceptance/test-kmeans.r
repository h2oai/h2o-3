context("Kmeans")
#source('../../h2o-runit.R')
library(h2o)

print(Sys.getenv("H2O_IP"))
print(Sys.getenv("H2O_PORT"))

#conn <- h2o.init(ip=Sys.getenv("H2O_IP"), port=as.integer(Sys.getenv("H2O_PORT"), startH2O=FALSE))
conn <- h2o.init( startH2O=FALSE )
hex <- as.h2o( conn, iris )

#model <- tryCatch({
#	h2o.kmeans(data=hex, centers=5)
#}, error = function(err) {
#	return(err)
#})

model <- h2o.kmeans(data=hex, centers=5)

if(is(model, "H2OKMeansModel")) {
	test_that("Correct # of centers returned: ", {
		expect_equal(5, length(model@model$centers))
	})
} else {
	test_that("Input permutation foo: ", fail(message=toString(model)))
}
