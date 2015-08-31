import sys
sys.path.insert(1, "../../../")
import h2o, tests

def demo_glm(ip,port):

    h2o.demo(func="glm", interactive=False, test=True)

if __name__ == "__main__":
    tests.run_test(sys.argv, demo_glm)
