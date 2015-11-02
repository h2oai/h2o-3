import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def weights_and_biases():
    

    print "Test checks if Deep Learning weights and biases are accessible from R"

    covtype = h2o.upload_file(pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()
    dlmodel = h2o.deeplearning(x=covtype[0:54], y=covtype[54], hidden=[17,191], epochs=1, training_frame=covtype,
                               balance_classes=False, reproducible=True, seed=1234, export_weights_and_biases=True)
    print dlmodel

    weights1 = dlmodel.weights(0)
    weights2 = dlmodel.weights(1)
    weights3 = dlmodel.weights(2)

    biases1 = dlmodel.biases(0)
    biases2 = dlmodel.biases(1)
    biases3 = dlmodel.biases(2)

    w1c = weights1.ncol
    w1r = weights1.nrow
    assert w1c == 52, "wrong dimensionality! expected {0}, but got {1}.".format(52, w1c)
    assert w1r == 17, "wrong dimensionality! expected {0}, but got {1}.".format(17, w1r)

    w2c = weights2.ncol
    w2r = weights2.nrow
    assert w2c == 17, "wrong dimensionality! expected {0}, but got {1}.".format(17, w2c)
    assert w2r == 191, "wrong dimensionality! expected {0}, but got {1}.".format(191, w2r)

    w3c = weights3.ncol
    w3r = weights3.nrow
    assert w3c == 191, "wrong dimensionality! expected {0}, but got {1}.".format(191, w3c)
    assert w3r == 7, "wrong dimensionality! expected {0}, but got {1}.".format(7, w3r)

    b1c = biases1.ncol
    b1r = biases1.nrow
    assert b1c == 1, "wrong dimensionality! expected {0}, but got {1}.".format(1, b1c)
    assert b1r == 17, "wrong dimensionality! expected {0}, but got {1}.".format(17, b1r)

    b2c = biases2.ncol
    b2r = biases2.nrow
    assert b2c == 1, "wrong dimensionality! expected {0}, but got {1}.".format(1, b2c)
    assert b2r == 191, "wrong dimensionality! expected {0}, but got {1}.".format(191, b2r)

    b3c = biases3.ncol
    b3r = biases3.nrow
    assert b3c == 1, "wrong dimensionality! expected {0}, but got {1}.".format(1, b3c)
    assert b3r == 7, "wrong dimensionality! expected {0}, but got {1}.".format(7, b3r)



if __name__ == "__main__":
      pyunit_utils.standalone_test(weights_and_biases)
else:
    weights_and_biases()
