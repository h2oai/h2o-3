

import h2o, tests

def pubdev_1839():

    train = h2o.import_file(tests.locate("smalldata/jira/pubdev_1839_repro_train.csv"))
    test  = h2o.import_file(tests.locate("smalldata/jira/pubdev_1839_repro_test.csv"))

    glm0 = h2o.glm(x           =train.drop("bikes"),
                   y           =train     ["bikes"],
                   validation_x=test .drop("bikes"),
                   validation_y=test      ["bikes"],
                   family="poisson")


pyunit_test = pubdev_1839
