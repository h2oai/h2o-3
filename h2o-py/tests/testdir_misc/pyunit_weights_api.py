import sys
sys.path.insert(1, "../../")
import h2o, tests

def weights_api():
    
    

    h2o_iris_data = h2o.import_file(h2o.locate("smalldata/iris/iris.csv"))
    r = h2o_iris_data.runif()
    iris_train = h2o_iris_data[r > 0.2]
    iris_valid = h2o_iris_data[r <= 0.2]

    # training_frame specified, weights column part of x
    gbm1_multinomial = h2o.gbm(x=iris_train[["C1","C2","C3"]],
                               y=iris_train[4],
                               training_frame=iris_train,
                               ntrees=5,
                               distribution="multinomial",
                               weights_column="C3")

    # training_frame specified, weights not part of x
    gbm2_multinomial = h2o.gbm(x=iris_train[["C1","C2","C3"]],
                               y=iris_train[4],
                               training_frame=iris_train,
                               ntrees=5,
                               distribution="multinomial",
                               weights_column="C4")

    # training_frame not specified, weights part of x
    gbm3_multinomial = h2o.gbm(x=iris_train[["C1","C2","C3"]],
                               y=iris_train[4],
                               ntrees=5,
                               distribution="multinomial",
                               weights_column="C2",
                               training_frame=iris_train)

    # training_frame not specified, weights not part of x
    try:
        gbm4_multinomial = h2o.gbm(x=iris_train[["C1","C2","C3"]],
                                   y=iris_train[4],
                                   ntrees=5,
                                   distribution="multinomial",
                                   weights_column="C4",
                                   training_frame=iris_train)

        assert False, "expected an error"
    except:
        assert True

    ########################################################################################################################


    # validation_frame specified, weights column part of validation_x
    gbm1_multinomial = h2o.gbm(x=iris_train[["C1","C2","C3"]],
                               y=iris_train[4],
                               training_frame=iris_train,
                               validation_x=iris_valid[["C1","C2","C3"]],
                               validation_y=iris_valid[4],
                               validation_frame=iris_valid,
                               ntrees=5,
                               distribution="multinomial",
                               weights_column="C3")

    # validation_frame specified, weights not part of validation_x
    gbm2_multinomial = h2o.gbm(x=iris_train[["C1","C2","C3"]],
                               y=iris_train[4],
                               training_frame=iris_train,
                               validation_x=iris_valid[["C1","C2","C3"]],
                               validation_y=iris_valid[4],
                               validation_frame=iris_valid,
                               ntrees=5,
                               distribution="multinomial",
                               weights_column="C4")

    # validation_frame not specified, weights part of validation_x
    gbm3_multinomial = h2o.gbm(x=iris_train[["C1","C2","C3"]],
                               y=iris_train[4],
                               validation_x=iris_valid[["C1","C2","C3"]],
                               validation_y=iris_valid[4],
                               ntrees=5,
                               distribution="multinomial",
                               weights_column="C2",
                               training_frame=iris_train,
                               validation_frame=iris_valid)

    # validation_frame not specified, weights not part of validation_x
    try:
        gbm4_multinomial = h2o.gbm(x=iris_train[["C1","C2","C3"]],
                                   y=iris_train[4],
                                   validation_x=iris_valid[["C1","C2","C3"]],
                                   validation_y=iris_valid[4],
                                   ntrees=5,
                                   distribution="multinomial",
                                   weights_column="C4")

        assert False, "expected an error"
    except:
        assert True


if __name__ == "__main__":
    tests.run_test(sys.argv, weights_api)
