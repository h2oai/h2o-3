import sys
sys.path.insert(1,"../../")
from h2o import demo
from tests import pyunit_utils




def demo_glm():

    demo.demo(func="glm", interactive=False, test=True)



if __name__ == "__main__":
    pyunit_utils.standalone_test(demo_glm)
else:
    demo_glm()
