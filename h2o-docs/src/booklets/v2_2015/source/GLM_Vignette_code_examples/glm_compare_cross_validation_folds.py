h2o_df = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
model = H2OGeneralizedLinearEstimator(family = "binomial", nfolds = 5)
model.train(y = "IsDepDelayed", x = ["Year", "Origin"], training_frame = h2o_df)


print "full model training auc:", model.auc()
print "full model cv auc:", model.auc(xval=True)
for model_ in model.get_xval_models():
    print model_.model_id, " training auc:", model_.auc(), " validation auc: ", model_.auc(valid=True)

