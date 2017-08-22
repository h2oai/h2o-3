import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from random import randint
from random import uniform
from random import shuffle

# This test is used to generate a dataframe that contains two columns, one integer and one double which
# should contain both positive, negative, zero.
def gen_data():
    floatA = []
    intA = []
    sizeMat = range(0,64)   # use to generate data of values 0, +/- 2^0 to +/1 2^64
    numZeros = 500
    numNans = 0   # generate Nans
    numInfs = 500

    if (numNans > 0):
        floatA = [float('NaN')]*numNans
        intA = [float('NaN')]*numNans

    if (numInfs > 0):
        floatA.extend([float('inf')]*numInfs)
        intA.extend([float('inf')]*numInfs)
        floatA.extend([-1.0*float('inf')]*numInfs)
        intA.extend([-1*float('inf')]*numInfs)

    # first generate the zeros into floatA and intA.  Multiply them with +/-1 to mess them up a little
    for index in range(numZeros):
        floatA.append(0.0*randint(-1,1))
        intA.append(0*randint(-1,1))

    # next generate +/- integers or various magnitude
    for rad in sizeMat:
        tempInt = pow(2,rad)
        tempIntN = pow(2,rad+1)
        intA.append(tempInt)
        intA.append(-1*tempInt)
        randInt = randint(tempInt, tempIntN)    # randomly generated integer, add both +/- values
        intA.append(randInt)
        intA.append(-1*randInt)
        intA.append(randint(tempInt, tempIntN))     # randomly generated integer
        intA.append(-1*(randint(tempInt, tempIntN)))    # randomly generated negative integer

        floatA.append(tempInt*1.0)
        floatA.append(-1.0*tempInt)
        tempD = uniform(tempInt, tempIntN)
        floatA.append(tempD)
        floatA.append(-1.0*tempD)
        floatA.append(uniform(tempInt, tempIntN))
        floatA.append(-1.0*uniform(tempInt, tempIntN))


    # repeat the columns a few times to seriously test the algo with duplicated data.
    intA.extend(intA)
    intA.extend(intA)
    intA.extend(intA)
    shuffle(intA)   # randomly shuffle the indices

    floatA.extend(floatA)
    floatA.extend(floatA)
    floatA.extend(floatA)
    shuffle(floatA) #

    intFrame = h2o.H2OFrame(python_obj=intA)
    floatFrame = h2o.H2OFrame(python_obj=floatA)
    finalFrame = intFrame.concat([intFrame, floatFrame])




if __name__ == "__main__":
    pyunit_utils.standalone_test(gen_data)
else:
    gen_data()
