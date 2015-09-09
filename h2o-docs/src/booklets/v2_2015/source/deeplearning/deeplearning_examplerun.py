# We first encode the response column as categorical for multinomial classification
train["C785"] = train["C785"].asfactor()
test["C785"] = test["C785"].asfactor()

#Train a Deep Learning model and validate on a test set
model = h2o.deeplearning(x = train.names[0:784], 
                         y = train.names[784], 
                         training_frame = train, 
                         validation_frame = test, 
                         distribution = "multinomial",
                         activation = "RectifierWithDropout", 
                         hidden = [200,200,200], 
                         input_dropout_ratio = 0.2, 
                         l1 = 1e-5, 
                         epochs = 10)
