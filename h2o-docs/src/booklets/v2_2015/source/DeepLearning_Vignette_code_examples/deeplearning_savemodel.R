# Specify a model and the file path where it is to be saved. If no path is specified, the model will be saved to the current working directory
model_path <- h2o.saveModel(object = model,
                            path=getwd(), force = TRUE)

print(model_path)
# /tmp/mymodel/DeepLearning_model_R_1441838096933
