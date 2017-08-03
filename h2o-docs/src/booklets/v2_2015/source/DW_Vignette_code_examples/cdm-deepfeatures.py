#http://data.dmlc.ml/mxnet/models/imagenet/inception-bn_old.tar.gz#
import h2o
from h2o.estimators.deepwater import H2ODeepWaterEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# Start or connect to H2O
h2o.init(nthreads=-1, strict_version_check=False)

# Import data and transform data
train = h2o.import_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv")

# Load network
network_model = H2ODeepWaterEstimator(epochs=0, mini_batch_size=32, network="user", network_definition_file="Inception_BN-symbol.json", network_parameters_file="Inception_BN-0039.params", mean_image_file="mean_224.nd", image_shape=[224,224], channels=3)

network_model.train(x=[0], y=1, training_frame=train)

# Extract deep features
extracted_features = network_model.deepfeatures(train, "global_pool_output")
print("shape: " + str(extracted_features.shape))
print(extracted_features[:5,:3])

# Merge deep features with target and split frame
extracted_features["target"] = train[1]
features = [x for x in extracted_features.columns if x not in ["target"]]
train, valid = extracted_features.split_frame(ratios=[0.8])

# Build multinomial GLM
glm_model = H2OGeneralizedLinearEstimator(family="multinomial")
glm_model.train(x=features, y="target", training_frame=train, validation_frame=valid)

# Evaluate model
glm_model.show()
