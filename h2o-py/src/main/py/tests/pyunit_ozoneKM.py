import sys
sys.path.insert(1, "..")  # inserts before index "1"

import h2o

######################################################
#
# Sample Running GBM on prostate.csv

# Connect to a pre-existing cluster
h2o.init()  # connect to localhost:54321

train = h2o.import_frame(path="smalldata/glm_test/ozone.csv")

# See that the data is ready
print train.describe()

# Run KMeans
from h2o import H2OKMeans

my_km = H2OKMeans()
my_km.training_frame = train
my_km.x = range(0, train.ncol(), 1)  # train on the remaining columns
my_km.k = 10
my_km.init = "PlusPlus"
my_km.max_iterations = 100
my_km.fit()

my_km.show()


# Alternative Look:
from h2o.model.h2o_km_builder import H2OKMeansBuilder

my_km2 = H2OKMeansBuilder(training_frame=train,
                          x=range(0, train.ncol(), 1),
                          k=10, max_iterations=100,
                          init="PlusPlus").fit()
my_km2.show()
