setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test <- function() {

    mnist <- h2o.importFile(h2oTest.locate("bigdata/laptop/mnist/train.csv.gz"))
    indVars <-  names(mnist[,-785])
    depVars <- "C785"
    alpha <- 0.5
    family_type <- "binomial"
    mnist[,depVars] = mnist[,depVars] == 5

    lower_bound <- -1
    upper_bound <- 1
    bc <- data.frame(names=indVars, lower_bounds=rep(lower_bound,784), upper_bounds=rep(upper_bound,784))

    glm <- h2o.glm(x=indVars, y=depVars, training_frame=mnist, family=family_type, alpha=alpha, beta_constraints=bc)

    
}

h2oTest.doTest("GLM Test: GLM w/ Beta Constraints with constant predictor columns", test)
