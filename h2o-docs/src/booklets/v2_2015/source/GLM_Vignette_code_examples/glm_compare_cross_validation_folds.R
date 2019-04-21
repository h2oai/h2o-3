library(h2o)
h2o.init()
h2o_df = h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
model = h2o.glm(y = "IsDepDelayed", x = c("Year", "Origin"), training_frame = h2o_df, family = "binomial", nfolds = 5, keep_cross_validation_models = TRUE)
print(paste("full model training auc:", model@model$training_metrics@metrics$AUC))
print(paste("full model cv auc:", model@model$cross_validation_metrics@metrics$AUC))
for (i in 1:5) {
    cv_model_name = model@model$cross_validation_models[[i]]$name
    cv_model = h2o.getModel(cv_model_name)
    print(paste("cv fold ", i, " training auc:", cv_model@model$training_metrics@metrics$AUC, " validation auc: ", cv_model@model$validation_metrics@metrics$AUC))
}
