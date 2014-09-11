######################################################################
# Test for HEX-1613
# Bad confusion matrix output for mixed inputs
######################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../findNSourceUtils.R')

heading("BEGIN TEST")
conn <- new("H2OClient", ip=myIP, port=myPort)

path <- locate("smalldata/logreg/prostate.csv")
hex <- h2o.uploadFile(conn, path, key="p.hex")

m <- h2o.glm(x = 3:8, y = 2, family = "binomial", data = hex)

pred <- h2o.predict(m, hex)

res <- .h2o.__remoteSend(conn, .h2o.__PAGE_CONFUSION, actual = hex@key, vactual = "CAPSULE", predict = pred@key, vpredict = "predict")

print(res$cm)

h2o.confusionMatrix(hex$CAPSULE, pred$predict)

PASS_BANNER()
