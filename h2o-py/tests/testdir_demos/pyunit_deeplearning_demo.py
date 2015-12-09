import sys
sys.path.insert(1,"../../")
from h2o import demo
from tests import pyunit_utils




def demo_deeplearning():

    demo.demo(func="deeplearning", interactive=False, test=True)



if __name__ == "__main__":
    pyunit_utils.standalone_test(demo_deeplearning)
else:
    demo_deeplearning()
