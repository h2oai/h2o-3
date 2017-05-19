#http://data.dmlc.ml/mxnet/models/imagenet/inception-bn_old.tar.gz#
import h2o
from h2o.estimators.deepwater import H2ODeepWaterEstimator

# Start or connect to H2O
h2o.init(nthreads=-1, strict_version_check=False)

# Import data and transform data
train = h2o.import_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv")

# Load network
network_model = H2ODeepWaterEstimator(epochs=0, mini_batch_size=32, network="user", network_definition_file="Inception_BN-symbol.json", network_parameters_file="Inception_BN-0039.params", mean_image_file="mean_224.nd", image_shape=[224,224], channels=3)

network_model.train(x=[0], y=1, training_frame=train)

# Extract deep features
extracted_features = network_model.deepfeatures(train, "global_pool_output")

# Seperate records to a references and queries
references = extracted_features[5:,:]
queries = extracted_features[:3,:]

# Compute similarity
similarity = references.distance(queries, "cosine")

# Verify shapes
print("references: " + str(references.shape))
print("queries: " + str(queries.shape))
print("similarity: " + str(similarity.shape))

# View similarity frame
print(similarity.head())
