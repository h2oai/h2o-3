library(h2o)

# Start or connect to H2O
h2o.init()

# Import data and transform data
train <- h2o.importFile("bigdata/laptop/mnist/train.csv.gz")

target <- "C785"
features <- setdiff(names(train), target)

train[target] <- as.factor(train[target])

# Build model
model <-  h2o.deepwater(x=features, y=target, training_frame=train, epochs=100, activation="Rectifier", hidden=c(200,200), ignore_const_cols=FALSE, mini_batch_size=256, input_dropout_ratio=0.1, hidden_dropout_ratios=c(0.5,0.5), stopping_rounds=3, stopping_tolerance=0.05, stopping_metric="misclassification", score_interval=2, score_duty_cycle=0.5, score_training_samples=1000, score_validation_samples=1000, nfolds=5, gpu=TRUE, seed=1234)

# Evaluate model
summary(model)
