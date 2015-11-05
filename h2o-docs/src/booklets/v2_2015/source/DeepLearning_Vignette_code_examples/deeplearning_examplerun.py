from h2o.estimators.deeplearning import H2ODeepLearningEstimator

# Specify the response and predictor columns
y = "C785"
x = train.names[0:784]

# Encode the response column as categorical for multinomial classification
train[y] = train[y].asfactor()
test[y] = test[y].asfactor()

# Train Deep Learning model and validate on test set
model = H2ODeepLearningEstimator(
        distribution="multinomial",
        activation="RectifierWithDropout", 
        hidden=[32,32,32],
        input_dropout_ratio=0.2, 
        sparse=True, 
        l1=1e-5, 
        epochs=10)
model.train(
        x=x, 
        y=y, 
        training_frame=train, 
        validation_frame=test)