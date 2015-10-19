

import h2o, tests

def demo_glm():

    h2o.demo(func="glm", interactive=False, test=True)


pyunit_test = demo_glm
