import sys
sys.path.insert(1, "..")  # inserts before index "1"

import h2o

h2o.init()

a = h2o.upload_file("../../../../../smalldata/logreg/prostate.csv")

print a.describe()