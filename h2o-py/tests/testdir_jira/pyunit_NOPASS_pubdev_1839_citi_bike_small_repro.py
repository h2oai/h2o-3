import sys
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_1839(ip, port):

    train = h2o.import_file(h2o.locate("smalldata/jira/pubdev_1839_repro_train.csv"))
    test  = h2o.import_file(h2o.locate("smalldata/jira/pubdev_1839_repro_test.csv"))

    glm0 = h2o.glm(x           =train.drop("bikes"),
                   y           =train     ["bikes"],
                   validation_x=test .drop("bikes"),
                   validation_y=test      ["bikes"],
                   Lambda=[1e-5],
                   family="poisson")

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_1839)
