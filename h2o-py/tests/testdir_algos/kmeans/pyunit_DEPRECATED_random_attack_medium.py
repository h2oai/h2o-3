import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import random

def random_attack():



    def attack(train, x):
        kwargs = {}

        # randomly select parameters and their corresponding values
        kwargs['k'] = random.randint(2,20)
        if random.randint(0,1): kwargs['model_id'] = "my_model"
        if random.randint(0,1): kwargs['max_iterations'] = random.randint(1,1000)
        if random.randint(0,1): kwargs['standardize'] = [True, False][random.randint(0,1)]
        if random.randint(0,1):
            method = random.randint(0,3)
            if method == 3:
                s = [[random.uniform(train[c].mean()[0]-100,train[c].mean()[0]+100) for p in range(kwargs['k'])] for c in x]
                print "s: {0}".format(s)
                start = h2o.H2OFrame(s)
                kwargs['user_points'] = start
            else:
                kwargs['init'] = ["Furthest","Random", "PlusPlus"][method]
        if random.randint(0,1): kwargs['seed'] = random.randint(1,10000)

        # display the parameters and their corresponding values
        print "-----------------------"
        print "x: {0}".format(x)
        for k, v in zip(kwargs.keys(), kwargs.values()):
            if k == 'user_points':
                print k + ": "
                start.show()
            else:
                print k + ": {0}".format(v)
        h2o.kmeans(x=train[x],  **kwargs)
        print "-----------------------"

    print "Import and data munging..."
    ozone = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/ozone.csv"))

    for i in range(50):
        attack(ozone, random.sample([0,1,2,3],random.randint(2,4)))



if __name__ == "__main__":
    pyunit_utils.standalone_test(random_attack)
else:
    random_attack()
