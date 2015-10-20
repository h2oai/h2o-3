# Specify a model and the file path where it is to be saved
model_path <- h2o.saveModel(
        object = model, 
        path = "/tmp/mymodel", 
        force = TRUE)

print(model_path)
# /tmp/mymodel/DeepLearning_model_R_1441838096933
