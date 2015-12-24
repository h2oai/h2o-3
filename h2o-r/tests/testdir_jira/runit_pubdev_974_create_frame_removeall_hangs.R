setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
options(echo=TRUE)

heading("BEGIN TEST")

myframe <- h2o.createFrame(rows = 100, cols = 10,
                           seed = 12345, randomize = T, value = 0, real_range = 100, 
                           categorical_fraction = 0.0, factors = 10, 
                           integer_fraction = 0.4, integer_range = 100, 
                           missing_fraction = 0, response_factors = 1, has_response = TRUE)

h2o.removeAll(timeout_secs=120)

PASS_BANNER()