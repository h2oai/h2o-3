# Specify a model and the file path where it is to be saved
model_path = h2o.save_model(model = model, 
                            path = "/tmp/mymodel", 
                            force = True)

print model_path
# /tmp/mymodel/DeepLearning_model_python_1441838096933_5