# Specify the response and predictor columns
y <- "C785"
x <- setdiff(names(train), y)

# We encode the response column as categorical for multinomial classification
train[,y] <- as.factor(train[,y])
test[,y] <- as.factor(test[,y])

# Train a Deep Learning model and validate on a test set
model <- h2o.deeplearning(
        x = x, 
        y = y, 
        training_frame = train,
        validation_frame = test,   
        distribution = "multinomial",
        activation = "RectifierWithDropout", 
        hidden = c(200,200,200), 
        input_dropout_ratio = 0.2, 
        l1 = 1e-5, 
        epochs = 10)
