prostate.hex <- h2o.importFile(path = "https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv"
                               , destination_frame = "prostate.hex")

prostate.glm<-h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex,
              family = "binomial", nfolds = 10, alpha = 0.5)
prostate.glm@model$cross_validation_metrics