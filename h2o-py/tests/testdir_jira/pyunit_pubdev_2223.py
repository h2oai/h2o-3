import sys
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_2223():

    covtype = h2o.import_file(tests.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()
    dlmodel = h2o.deeplearning(x=covtype[0:54], y=covtype[54], hidden=[17,191],
                               epochs=1, training_frame=covtype,
                               balance_classes=False, reproducible=True, seed=1234,
                               export_weights_and_biases=True)

    print "Normalization/Standardization multipliers for numeric predictors: {0}\n".format(dlmodel.normmul())
    print "Normalization/Standardization offsets for numeric predictors: {0}\n".format(dlmodel.normsub())
    print "Normalization/Standardization multipliers for numeric response: {0}\n".format(dlmodel.respmul())
    print "Normalization/Standardization offsets for numeric response: {0}\n".format(dlmodel.respsub())
    print "Categorical offsets for one-hot encoding: {0}\n".format(dlmodel.catoffsets())

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_2223)
