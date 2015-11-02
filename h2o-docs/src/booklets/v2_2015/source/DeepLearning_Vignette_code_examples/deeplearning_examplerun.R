# Specify the response and predictor columns
y <- "C785"
x <- setdiff(names(train), y)

# Encode the response column as categorical for multinomial classification
train[,y] <- as.factor(train[,y])
test[,y] <- as.factor(test[,y])

# Train Deep Learning model and validate on test set
model <- h2o.deeplearning(
        x = x, 
        y = y, 
        training_frame = train,
        validation_frame = test,   
        distribution = "multinomial",
        activation = "RectifierWithDropout", 
        hidden = c(32,32,32),
        input_dropout_ratio = 0.2, 
        sparse = TRUE,
        l1 = 1e-5, 
        epochs = 10)
