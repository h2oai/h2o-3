import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def runif_check():
    # Connect to a pre-existing cluster


    uploaded_frame = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
    r_u = uploaded_frame[0].runif(1234)

    imported_frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/mnist/train.csv.gz"))
    r_i = imported_frame[0].runif(1234)

    print "This demonstrates that seeding runif on identical frames with different chunk distributions provides " \
          "different results. upload_file: {0}, import_frame: {1}.".format(r_u.mean()[0], r_i.mean()[0])



if __name__ == "__main__":
    pyunit_utils.standalone_test(runif_check)
else:
    runif_check()
