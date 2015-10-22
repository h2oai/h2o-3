import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pub_445_long_request_uri():
    
    

    mnistTrain = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
    mnistTest = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))

    mnistTrain.set_name(col=784, name="label")
    mnistTest.set_name(col=784, name="label")

    mnistModel = h2o.gbm(x=mnistTrain[0:784], y=mnistTrain["label"], validation_x=mnistTest[0:784],
                     validation_y=mnistTest["label"], ntrees=100, max_depth=10)



if __name__ == "__main__":
    pyunit_utils.standalone_test(pub_445_long_request_uri)
else:
    pub_445_long_request_uri()
