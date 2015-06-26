import sys
sys.path.insert(1, "../../../")
import h2o
import random

def random_attack(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    def attack(train, x):
        kwargs = {}

        # randomly select parameters and their corresponding values
        kwargs['k'] = random.randint(1,20)
        if random.randint(0,1): kwargs['model_id'] = "my_model"
        if random.randint(0,1): kwargs['max_iterations'] = random.randint(1,1000)
        if random.randint(0,1): kwargs['standardize'] = [True, False][random.randint(0,1)]
        if random.randint(0,1):
            method = random.randint(0,3)
            if method == 3:
                s = []
                for p in range(kwargs['k']):
                    s.append([random.uniform(train[c].mean()-100,train[c].mean()+100) for c in x])
                start = h2o.H2OFrame(python_obj=s)
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
    ozone = h2o.import_frame(path=h2o.locate("smalldata/glm_test/ozone.csv"))

    for i in range(50):
        attack(ozone, random.sample([0,1,2,3],random.randint(1,4)))

if __name__ == "__main__":
    h2o.run_test(sys.argv, random_attack)
