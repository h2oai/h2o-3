

import h2o, tests

def demo_deeplearning():

    h2o.demo(func="deeplearning", interactive=False, test=True)


pyunit_test = demo_deeplearning
