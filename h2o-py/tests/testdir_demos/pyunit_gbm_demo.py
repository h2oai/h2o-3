

import h2o, tests

def demo_gbm():

    h2o.demo(func="gbm", interactive=False, test=True)


pyunit_test = demo_gbm
