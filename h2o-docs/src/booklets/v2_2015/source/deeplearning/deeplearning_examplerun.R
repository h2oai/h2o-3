# We first encode the response column as categorical for multinomial classification
train[,785] <- as.factor(train[,785])
test[,785] <- as.factor(test[,785])

# Train a Deep Learning model and validate on a test set
model <- h2o.deeplearning(x = 1:784, y = 785, 
                          training_frame = train,
                          distribution = "multinomial",
                          activation = "RectifierWithDropout", 
                          hidden = c(200,200,200), 
                          input_dropout_ratio = 0.2, 
                          l1 = 1e-5, 
                          validation_frame = test, 
                          epochs = 10)
