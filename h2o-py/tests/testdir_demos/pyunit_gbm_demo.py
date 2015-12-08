import sys
sys.path.insert(1,"../../")
from h2o import demo
from tests import pyunit_utils




def demo_gbm():

    demo.demo(func="gbm", interactive=False, test=True)



if __name__ == "__main__":
    pyunit_utils.standalone_test(demo_gbm)
else:
    demo_gbm()
