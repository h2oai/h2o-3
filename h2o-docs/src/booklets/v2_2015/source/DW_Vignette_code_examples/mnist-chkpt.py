import h2o
from h2o.estimators.deepwater import H2ODeepWaterEstimator

# Start or connect to H2O
h2o.init(nthreads=-1, strict_version_check=False)

# Import data and transform data
train = h2o.import_file("bigdata/laptop/mnist/train.csv.gz")
valid = h2o.import_file("bigdata/laptop/mnist/test.csv.gz")

features = list(range(0,784))
target = 784

train[target] = train[target].asfactor()
valid[target] = valid[target].asfactor()

# Build model
model = H2ODeepWaterEstimator(epochs=20, activation="Rectifier", hidden=[200,200], ignore_const_cols=False, mini_batch_size=256, input_dropout_ratio=0.1, hidden_dropout_ratios=[0.5,0.5], stopping_rounds=3, stopping_tolerance=0.05, stopping_metric="misclassification", score_interval=2, score_duty_cycle=0.5, score_training_samples=1000, score_validation_samples=1000, gpu=True, seed=1234)

model.train(x=features, y=target, training_frame=train, validation_frame=valid)

# Evaluate model
model.show()
print(model.scoring_history())

# Checkpoint model
model_path = h2o.save_model(model=model, force=True)

# Load model
model_ckpt = h2o.load_model(model_path)

# Start training from checkpoint
model_warm = H2ODeepWaterEstimator(checkpoint=model_ckpt.model_id, epochs=100, activation="Rectifier", hidden=[200,200], ignore_const_cols=False, mini_batch_size=256, input_dropout_ratio=0.1, hidden_dropout_ratios=[0.5,0.5], stopping_rounds=3, stopping_tolerance=0.05, stopping_metric="misclassification", score_interval=2, score_duty_cycle=0.5, score_training_samples=1000, score_validation_samples=1000, gpu=True, seed=1234)

model_warm.train(x=features, y=target, training_frame=train, validation_frame=valid)

# Evaluate checkpointed model
model_warm.show()
print(model_warm.scoring_history())
