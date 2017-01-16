from __future__ import print_function
import h2o
from tests import pyunit_utils
from h2o.estimators.kmeans import H2OKMeansEstimator


import random

def random_attack():

    def attack(train, x):
        kwargs = {}

        # randomly select parameters and their corresponding values
        kwargs['k'] = random.randint(1, 20)
        if random.randint(0, 1): kwargs['model_id'] = "my_model"
        if random.randint(0, 1): kwargs['max_iterations'] = random.randint(1, 1000)
        if random.randint(0, 1): kwargs['standardize'] = [True, False][random.randint(0, 1)]
        if random.randint(0, 1):
            method = random.randint(0, 3)
            if method == 3:
                # Can be simplified to: train[x].mean() + (train[x].runif() - 0.5)*200
                # once .runif() is fixed
                s = [[train[c].mean().getrow()[0] + random.uniform(-100, 100)
                      for p in range(kwargs['k'])] for c in x]
                print("s: {0}".format(s))
                start = h2o.H2OFrame(list(zip(*s)))
                kwargs['user_points'] = start
            else:
                kwargs['init'] = ["Furthest", "Random", "PlusPlus"][method]
        if random.randint(0, 1): kwargs['seed'] = random.randint(1, 10000)

        # display the parameters and their corresponding values
        print("-----------------------")
        print("x: {0}".format(x))
        for k, v in kwargs.items():
            if k == 'user_points':
                print(k + ": ")
                start.show()
            else:
                print(k + ": {0}".format(v))


        H2OKMeansEstimator(**kwargs).train(x=x, training_frame=train)
        print("-----------------------")

    print("Import Ozone.csv...")
    ozone = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/ozone.csv"))
    for i in range(50):
        attack(ozone, random.sample([0, 1, 2, 3], random.randint(1, 4)))



if __name__ == "__main__":
    pyunit_utils.standalone_test(random_attack)
else:
    random_attack()
