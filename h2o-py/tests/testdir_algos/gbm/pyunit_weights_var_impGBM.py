import sys
sys.path.insert(1, "../../../")
import h2o, tests
import random

def weights_var_imp(ip,port):
    
    

    def check_same(data1, data2, min_rows_scale):
        gbm1_regression = h2o.gbm(x=data1[["displacement", "power", "weight", "acceleration", "year"]],
                                  y=data1["economy"],
                                  min_rows=5,
                                  ntrees=5,
                                  max_depth=2)

        gbm2_regression = h2o.gbm(x=data2[["displacement", "power", "weight", "acceleration", "year"]],
                                  y=data2["economy"],
                                  training_frame=data2,
                                  min_rows=5*min_rows_scale,
                                  weights_column="weights",
                                  ntrees=5,
                                  max_depth=2)

        gbm1_binomial = h2o.gbm(x=data1[["displacement", "power", "weight", "acceleration", "year"]],
                                y=data1["economy_20mpg"],
                                min_rows=5,
                                distribution="bernoulli",
                                ntrees=5,
                                max_depth=2)

        gbm2_binomial = h2o.gbm(x=data2[["displacement", "power", "weight", "acceleration", "year"]],
                                y=data2["economy_20mpg"],
                                training_frame=data2,
                                weights_column="weights",
                                min_rows=5*min_rows_scale,
                                distribution="bernoulli",
                                ntrees=5,
                                max_depth=2)

        gbm1_multinomial = h2o.gbm(x=data1[["displacement", "power", "weight", "acceleration", "year"]],
                                   y=data1["cylinders"],
                                   min_rows=5,
                                   distribution="multinomial",
                                   ntrees=5,
                                   max_depth=2)

        gbm2_multinomial = h2o.gbm(x=data2[["displacement", "power", "weight", "acceleration", "year"]],
                                   y=data2["cylinders"],
                                   training_frame=data2,
                                   weights_column="weights",
                                   min_rows=5*min_rows_scale,
                                   distribution="multinomial",
                                   ntrees=5,
                                   max_depth=2)

        reg1_vi = gbm1_regression.varimp(return_list=True)
        reg2_vi = gbm2_regression.varimp(return_list=True)
        bin1_vi = gbm1_binomial.varimp(return_list=True)
        bin2_vi = gbm2_binomial.varimp(return_list=True)
        mul1_vi = gbm1_multinomial.varimp(return_list=True)
        mul2_vi = gbm2_multinomial.varimp(return_list=True)

        print "Varimp (regresson)   no weights vs. weights: {0}, {1}".format(reg1_vi, reg2_vi)
        print "Varimp (binomial)    no weights vs. weights: {0}, {1}".format(bin1_vi, bin2_vi)
        print "Varimp (multinomial) no weights vs. weights: {0}, {1}".format(mul1_vi, mul2_vi)

        for rvi1, rvi2 in zip(reg1_vi, reg2_vi): assert rvi1 == rvi1, "Expected vi's (regression)  to be the same, but got {0}, and {1}".format(rvi1, rvi2)
        for bvi1, bvi2 in zip(bin1_vi, bin2_vi): assert bvi1 == bvi1, "Expected vi's (binomial)    to be the same, but got {0}, and {1}".format(bvi1, bvi2)
        for mvi1, mvi2 in zip(mul1_vi, mul2_vi): assert mvi1 == mvi1, "Expected vi's (multinomial) to be the same, but got {0}, and {1}".format(mvi1, mvi2)

    h2o_cars_data = h2o.import_file(h2o.locate("smalldata/junit/cars_20mpg.csv"))
    h2o_cars_data["economy_20mpg"] = h2o_cars_data["economy_20mpg"].asfactor()
    h2o_cars_data["cylinders"] = h2o_cars_data["cylinders"].asfactor()

    # uniform weights same as no weights
    weight = random.randint(1,10)
    uniform_weights = [[weight] for r in range(406)]
    h2o_uniform_weights = h2o.H2OFrame(python_obj=uniform_weights)
    h2o_uniform_weights.set_names(["weights"])
    h2o_data_uniform_weights = h2o_cars_data.cbind(h2o_uniform_weights)

    print "\n\nChecking that using uniform weights is equivalent to no weights:"
    check_same(h2o_cars_data, h2o_data_uniform_weights, weight)

    # zero weights same as removed observations
    zero_weights = [[0] if random.randint(0,1) else [1] for r in range(406)]
    h2o_zero_weights = h2o.H2OFrame(python_obj=zero_weights)
    h2o_zero_weights.set_names(["weights"])
    h2o_data_zero_weights = h2o_cars_data.cbind(h2o_zero_weights)
    h2o_data_zeros_removed = h2o_cars_data[h2o_zero_weights["weights"] == 1]

    print "\n\nChecking that using some zero weights is equivalent to removing those observations:"
    check_same(h2o_data_zeros_removed, h2o_data_zero_weights, 1)

    # doubled weights same as doubled observations
    doubled_weights = [[1] if random.randint(0,1) else [2] for r in range(406)]
    h2o_doubled_weights = h2o.H2OFrame(python_obj=doubled_weights)
    h2o_doubled_weights.set_names(["weights"])
    h2o_data_doubled_weights = h2o_cars_data.cbind(h2o_doubled_weights)

    doubled_data = h2o.as_list(h2o_cars_data, use_pandas=False)
    colnames = doubled_data.pop(0)
    for idx, w in enumerate(doubled_weights):
        if w[0] == 2: doubled_data.append(doubled_data[idx])
    h2o_data_doubled = h2o.H2OFrame(python_obj=doubled_data)
    h2o_data_doubled.set_names(colnames)

    h2o_data_doubled["economy_20mpg"] = h2o_data_doubled["economy_20mpg"].asfactor()
    h2o_data_doubled["cylinders"] = h2o_data_doubled["cylinders"].asfactor()
    h2o_data_doubled_weights["economy_20mpg"] = h2o_data_doubled_weights["economy_20mpg"].asfactor()
    h2o_data_doubled_weights["cylinders"] = h2o_data_doubled_weights["cylinders"].asfactor()

    print "\n\nChecking that doubling some weights is equivalent to doubling those observations:"
    check_same(h2o_data_doubled, h2o_data_doubled_weights, 1)

if __name__ == "__main__":
    tests.run_test(sys.argv, weights_var_imp)
