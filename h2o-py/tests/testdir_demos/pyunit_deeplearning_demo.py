import sys
sys.path.insert(1, "../../")
import h2o, tests

def demo_deeplearning():

    h2o.demo(func="deeplearning", interactive=False, test=True)

if __name__ == "__main__":
    tests.run_test(sys.argv, demo_deeplearning)
