#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

import random

def random_attack():

    def cointoss():
        return random.randint(0, 1)

    def attack(family, train, valid, x, y):
        kwargs = {}
        kwargs["family"] = family
        gaussian_links = ["inverse", "log", "identity"]
        binomial_links = ["logit"]
        poisson_links = ["log", "identity"]
        gamma_links = ["inverse", "log", "identity"]

        # randomly select parameters and their corresponding values
        if cointoss(): kwargs["max_iterations"] = random.randint(1, 50)
        if random.random() > 0.8: kwargs["beta_epsilon"] = random.random()
        if cointoss(): kwargs["solver"] = ["AUTO", "IRLSM", "L_BFGS", "COORDINATE_DESCENT_NAIVE",
                                           "COORDINATE_DESCENT"][cointoss()]
        if cointoss(): kwargs["standardize"] = [True, False][cointoss()]
        if cointoss():
            if family == "gaussian": kwargs["link"] = gaussian_links[random.randint(0, 2)]
            elif family == "binomial": kwargs["link"] = binomial_links[0]
            elif family == "poisson": kwargs["link"] = poisson_links[cointoss()]
            elif family == "gamma": kwargs["link"] = gamma_links[random.randint(0, 2)]
        if cointoss(): kwargs["alpha"] = [random.random()]
        if family == "binomial":
            if cointoss(): kwargs["prior"] = random.random()
        if cointoss(): kwargs["lambda_search"] = [True, False][cointoss()]
        if "lambda_search" in list(kwargs.keys()):
            if cointoss(): kwargs["nlambdas"] = random.randint(2, 10)
        do_validation = [True, False][cointoss()]
        # beta constraints
        if cointoss():
            bc = []
            for n in x:
                if train[n].isnumeric()[0]:
                    name = train.names[n]
                    lower_bound = random.uniform(-1, 1)
                    upper_bound = lower_bound + random.random()
                    bc.append([name, lower_bound, upper_bound])
            if len(bc) > 0:
                beta_constraints = h2o.H2OFrame(bc)
                beta_constraints.set_names(["names", "lower_bounds", "upper_bounds"])
                kwargs["beta_constraints"] = beta_constraints

        # display the parameters and their corresponding values
        print("-----------------------")
        print("x: {0}".format(x))
        print("y: {0}".format(y))
        print("validation: {0}".format(do_validation))
        for k, v in kwargs.items():
            if k == "beta_constraints":
                print(k + ": ")
                beta_constraints.show()
            else:
                print(k + ": {0}".format(v))
        if do_validation:
            # h2o.glm(x=train[x], y=train[y], validation_x=valid[x], validation_y=valid[y], **kwargs)
            H2OGeneralizedLinearEstimator(**kwargs).train(x=x, y=y, training_frame=train, validation_frame=valid)
        else:
            # h2o.glm(x=train[x], y=train[y], **kwargs)
            H2OGeneralizedLinearEstimator(**kwargs).train(x=x, y=y, training_frame=train)
        print("-----------------------")

    print("Import and data munging...")
    seed = random.randint(1, 10000)
    print("SEED: {0}".format(seed))
    pros = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))
    pros[1] = pros[1].asfactor()
    r = pros[0].runif(seed=seed)  # a column of length pros.nrow with values between 0 and 1
    # ~80/20 train/validation split
    pros_train = pros[r > .2]
    pros_valid = pros[r <= .2]

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars.csv"))
    r = cars[0].runif(seed=seed)
    cars_train = cars[r > .2]
    cars_valid = cars[r <= .2]

    print()
    print("======================================================================")
    print("============================== Binomial ==============================")
    print("======================================================================")
    for i in range(10):
        attack("binomial", pros_train, pros_valid, random.sample([2, 3, 4, 5, 6, 7, 8], random.randint(1, 7)), 1)

    print()
    print("======================================================================")
    print("============================== Gaussian ==============================")
    print("======================================================================")
    for i in range(10):
        attack("gaussian", cars_train, cars_valid, random.sample([2, 3, 4, 5, 6, 7], random.randint(1, 6)), 1)

    print()
    print("======================================================================")
    print("============================== Poisson  ==============================")
    print("======================================================================")
    for i in range(10):
        attack("poisson", cars_train, cars_valid, random.sample([1, 3, 4, 5, 6, 7], random.randint(1, 6)), 2)

    print()
    print("======================================================================")
    print("==============================  Gamma   ==============================")
    print("======================================================================")
    for i in range(10):
        attack("gamma", pros_train, pros_valid, random.sample([1, 2, 3, 5, 6, 7, 8], random.randint(1, 7)), 4)



if __name__ == "__main__":
    pyunit_utils.standalone_test(random_attack)
else:
    random_attack()
