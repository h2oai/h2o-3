import h2o, tests

def deeplearning_multi():
    

    print("Test checks if Deep Learning works fine with a categorical dataset")

    # print(locate("smalldata/logreg/protstate.csv"))
    prostate = h2o.import_file(path=tests.locate("smalldata/logreg/prostate.csv"))
    prostate[1] = prostate[1].asfactor() #CAPSULE -> CAPSULE
    prostate[2] = prostate[2].asfactor() #AGE -> Factor
    prostate[3] = prostate[3].asfactor() #RACE -> Factor
    prostate[4] = prostate[4].asfactor() #DPROS -> Factor
    prostate[5] = prostate[5].asfactor() #DCAPS -> Factor
    prostate = prostate.drop('ID')       #remove ID
    prostate.describe()


    hh = h2o.deeplearning(x                     = prostate.drop('CAPSULE'),
                          y                     = prostate['CAPSULE'],
                          loss                  = 'CrossEntropy',
                          hidden                = [10, 10],
                          use_all_factor_levels = False)
    hh.show()

pyunit_test = deeplearning_multi
