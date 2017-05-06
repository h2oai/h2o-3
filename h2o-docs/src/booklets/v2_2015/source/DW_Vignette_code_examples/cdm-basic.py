import h2o
from h2o.estimators.deepwater import H2ODeepWaterEstimator

# Start or connect to H2O
h2o.init()

# Import data and transform data
train = h2o.import_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv")

# Build model
model = H2ODeepWaterEstimator(epochs=10, network="lenet", problem_type="image", image_shape=[28,28], channels=3)

model.train(x=[0], y=1, training_frame=train)

# Evaluate model
model.show()
