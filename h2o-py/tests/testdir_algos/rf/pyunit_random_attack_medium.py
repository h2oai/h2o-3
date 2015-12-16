from __future__ import print_function
from builtins import zip
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator



import random

def random_attack():

    def attack(train, valid, x, y):
        kwargs = {}

        # randomly select parameters and their corresponding values
        if random.randint(0,1): kwargs['mtries'] = random.randint(1,len(x))
        if random.randint(0,1): kwargs['sample_rate'] = random.random()
        if random.randint(0,1): kwargs['build_tree_one_node'] = True
        if random.randint(0,1): kwargs['ntrees'] = random.randint(1,10)
        if random.randint(0,1): kwargs['max_depth'] = random.randint(1,5)
        if random.randint(0,1): kwargs['min_rows'] = random.randint(1,10)
        if random.randint(0,1): kwargs['nbins'] = random.randint(2,20)
        if random.randint(0,1):
            kwargs['balance_classes'] = True
            if random.randint(0,1): kwargs['max_after_balance_size'] = random.uniform(0,10)
        if random.randint(0,1): kwargs['seed'] = random.randint(1,10000)
        do_validation = [True, False][random.randint(0,1)]

        # display the parameters and their corresponding values
        print("-----------------------")
        print("x: {0}".format(x))
        print("y: {0}".format(y))
        print("validation: {0}".format(do_validation))
        for k, v in zip(list(kwargs.keys()), list(kwargs.values())): print(k + ": {0}".format(v))
        if do_validation:
            H2ORandomForestEstimator(**kwargs).train(x=x,y=y,training_frame=train,validation_frame=valid)
#            h2o.random_forest(x=train[x], y=train[y], validation_x=valid[x], validation_y=valid[y], **kwargs)
        else:
            H2ORandomForestEstimator(**kwargs).train(x=x,y=y,training_frame=train)
#            h2o.random_forest(x=train[x], y=train[y], **kwargs)
        print("-----------------------")

    print("Import and data munging...")
    pros = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))
    pros[1] = pros[1].asfactor()
    pros[4] = pros[4].asfactor()
    pros[5] = pros[5].asfactor()
    pros[8] = pros[8].asfactor()
    r = pros[0].runif() # a column of length pros.nrow with values between 0 and 1
    # ~80/20 train/validation split
    pros_train = pros[r > .2]
    pros_valid = pros[r <= .2]

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars.csv"))
    r = cars[0].runif()
    cars_train = cars[r > .2]
    cars_valid = cars[r <= .2]

    print()
    print("======================================================================")
    print("============================== Binomial ==============================")
    print("======================================================================")
    for i in range(10):
        attack(pros_train, pros_valid, random.sample([2,3,4,5,6,7,8],random.randint(1,7)), 1)

    print()
    print("======================================================================")
    print("============================== Gaussian ==============================")
    print("======================================================================")
    for i in range(10):
        attack(cars_train, cars_valid, random.sample([2,3,4,5,6,7],random.randint(1,6)), 1)

    print()
    print("======================================================================")
    print("============================= Multinomial ============================")
    print("======================================================================")
    cars_train[2] = cars_train[2].asfactor()
    cars_valid[2] = cars_valid[2].asfactor()
    for i in range(10):
        attack(cars_train, cars_valid, random.sample([1,3,4,5,6,7],random.randint(1,6)), 2)



if __name__ == "__main__":
    pyunit_utils.standalone_test(random_attack)
else:
    random_attack()
