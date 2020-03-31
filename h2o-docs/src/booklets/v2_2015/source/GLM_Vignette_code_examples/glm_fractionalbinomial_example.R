library(h2o)
h2o.init()
path = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/fraction_binommialOrig.csv"
train = h2o.importFile(path)
x <- c("log10conc")
y <- "y"

fractional_binomial <- h2o.glm (y = y, x = x, family = "fractionalbinomial", alpha = 0, lambda = 0, standardize = FALSE, compute_p_values = TRUE, training_frame = train)
