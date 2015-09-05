# TO DO: Fix bug below

best_model <- h2o.loadModel("/tmp/mymodel")  #this is not working

# Continue training the loaded model (for one additional epoch)
best_model_new = h2o.deeplearning(x = 1:784, y = 785, 
                                  training_frame = train, 
                                  checkpoint = best_model, 
                                  validation_frame = test, 
                                  epochs = 1)