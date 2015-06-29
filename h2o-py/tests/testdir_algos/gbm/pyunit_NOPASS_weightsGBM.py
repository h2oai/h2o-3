import sys
sys.path.insert(1, "../../../")
import h2o
import random
import copy

def weights_check(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    def check_same(data1, data2, min_rows_scale):
        gbm1_regression = h2o.gbm(x=data1[2:20], y=data1[1], min_rows=5, ntrees=5, max_depth=5)
        gbm2_regression = h2o.gbm(x=data2[2:21], y=data2[1], min_rows=5*min_rows_scale, weights_column="weights", ntrees=5, max_depth=5)
        gbm1_binomial = h2o.gbm(x=data1[1:20], y=data1[0], min_rows=5, distribution="bernoulli", ntrees=5, max_depth=5)
        gbm2_binomial = h2o.gbm(x=data2[1:21], y=data2[0], weights_column="weights", min_rows=5*min_rows_scale, distribution="bernoulli", ntrees=5, max_depth=5)
        gbm1_multinomial = h2o.gbm(x=data1[1:20], y=data1[0], min_rows=5, distribution="multinomial", ntrees=5, max_depth=2)
        gbm2_multinomial = h2o.gbm(x=data2[1:21], y=data2[0], weights_column="weights", min_rows=5*min_rows_scale, distribution="multinomial", ntrees=5, max_depth=5)

        assert abs(gbm1_regression.mse() - gbm2_regression.mse()) < 1e-6, "Expected mse's to be the same, but got {0}, " \
                                                                          "and {1}".format(gbm1_regression.mse(),
                                                                                           gbm2_regression.mse())
        assert abs(gbm1_binomial.auc() - gbm2_binomial.auc()) < 1e-6, "Expected auc's to be the same, but got {0}, and " \
                                                                      "{1}".format(gbm1_binomial.auc(), gbm2_binomial.auc())
        assert abs(gbm1_multinomial.auc() - gbm2_multinomial.auc()) < 1e-6, "Expected auc's to be the same, but got {0}, and " \
                                                                      "{1}".format(gbm1_multinomial.auc(), gbm2_multinomial.auc())

    data = [["ab"[random.randint(0,1)] if c==0 else random.gauss(0,1) for c in range(20)] for r in range(100)]
    h2o_data = h2o.H2OFrame(python_obj=data)

    # uniform weights same as no weights
    weight = random.uniform(.1,5)
    uniform_weights = [[weight] for r in range(100)]
    h2o_uniform_weights = h2o.H2OFrame(python_obj=uniform_weights)
    h2o_uniform_weights.setNames(["weights"])
    h2o_data_uniform_weights = h2o_data.cbind(h2o_uniform_weights)

    print "Checking that using uniform weights is equivalent to no weights:"
    print
    check_same(h2o_data, h2o_data_uniform_weights, weight)

    # zero weights same as removed observations
    zero_weights = [[0] if random.randint(0,1) else [1] for r in range(100)]
    h2o_zero_weights = h2o.H2OFrame(python_obj=zero_weights)
    h2o_zero_weights.setNames(["weights"])
    h2o_data_zero_weights = h2o_data.cbind(h2o_zero_weights)
    h2o_data_zeros_removed = h2o_data[h2o_zero_weights["weights"] == 1]

    print "Checking that using some zero weights is equivalent to removing those observations:"
    print
    check_same(h2o_data_zeros_removed, h2o_data_zero_weights, 1)

    # doubled weights same as doubled observations
    doubled_weights = [[1] if random.randint(0,1) else [2] for r in range(100)]
    h2o_doubled_weights = h2o.H2OFrame(python_obj=doubled_weights)
    h2o_doubled_weights.setNames(["weights"])
    h2o_data_doubled_weights = h2o_data.cbind(h2o_doubled_weights)

    doubled_data = copy.deepcopy(data)
    for d, w in zip(data,doubled_weights):
        if w[0] == 2: doubled_data.append(d)
    h2o_data_doubled = h2o.H2OFrame(python_obj=doubled_data)

    print "Checking that doubling some weights is equivalent to doubling those observations:"
    print
    check_same(h2o_data_doubled, h2o_data_doubled_weights, 1)

    # TODO: random weights

    # TODO: all zero weights???

    # TODO: negative weights???

if __name__ == "__main__":
    h2o.run_test(sys.argv, weights_check)
