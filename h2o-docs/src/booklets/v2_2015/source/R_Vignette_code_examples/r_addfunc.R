# Create an R functional expression
simpleFun <- function(x) { 2*x + 5 }
# Evaluate the expression across prostate's AGE column
calculated <- simpleFun(prostate.hex[,"AGE"])
h2o.cbind(prostate.hex[,"AGE"], calculated)