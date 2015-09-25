######################################################################
# Test for HEX-1613
# Bad confusion matrix output for mixed inputs
######################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

heading("BEGIN TEST")
conn <- new("H2OConnection", ip=myIP, port=myPort)

path <- locate("smalldata/logreg/prostate.csv")
hex <- h2o.importFile(path, destination_frame="p.hex")

m <- h2o.glm(x = 3:8, y = 2, family = "binomial", training_frame = hex)

pred <- predict(m, hex)

# res <- .h2o.__remoteSend(.h2o.__PAGE_CONFUSION, actual = hex@key, vactual = "CAPSULE", predict = pred@key, vpredict = "predict")
res <- h2o.performance(m)


print(h2o.confusionMatrix(res))

h2o.confusionMatrix(m, hex)

PASS_BANNER()
