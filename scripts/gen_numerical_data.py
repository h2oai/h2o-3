import sys
#sys.path.insert(1,"../../")
import h2o
#from tests import pyunit_utils
from random import randint
from random import uniform
from random import shuffle

# This test is used to generate a dataframe that contains two columns, one integer and one double which
# should contain both positive, negative, zero.  Used to test sort float and the new TopN command.
def gen_data():
    floatA = []
    intA = []
    sizeMat = range(0,30)   # use to generate data of values 0, +/- 2^0 to +/1 2^64
    lowBoundF = -100000
    upperBoundF = -1*lowBoundF      # 2 million rows
    upperBoundL = pow(2,35)
    lowBoundL = upperBoundL-100000
    numZeros = 0
    numNans = 0   # generate Nans
    numInfs = 500
    numRep = 2    # number of times to repeat array
    csvFile = "/Users/wendycwong/temp/TopBottomNRep4.csv"
    fMult = 1.1

    fintA = []
    ffloatA = []
    for ind in range(0,1000):
        floatA = []
        intA = []
        genRandomData(intA,floatA, sizeMat)
        fintA.extend(intA)
        ffloatA.extend(floatA)


    shuffle(fintA)
    shuffle(ffloatA)
    bottom20FrameL = h2o.H2OFrame(python_obj=zip(fintA))
    bottom20FrameF = h2o.H2OFrame(python_obj=zip(ffloatA))
    h2o.download_csv(bottom20FrameL.cbind(bottom20FrameF), "/Users/wendycwong/temp/smallIntFloats.csv" )

    genStaticData(intA, floatA, upperBoundL, lowBoundF, upperBoundF, fMult)
    # save the correct sequence before shuffling for comparison purpose
    tempL = intA[0:int(round(len(intA)*0.2))]   # comes in decreasing value
    tempF = floatA[0:int(round(len(floatA)*0.2))]   # comes in decreasing value
    # save the correct sequence before shuffling for comparison purpose
    bottom20FrameL = h2o.H2OFrame(python_obj=zip(tempL))
    bottom20FrameF = h2o.H2OFrame(python_obj=zip(tempF))
    h2o.download_csv(bottom20FrameL.cbind(bottom20FrameF), "/Users/wendycwong/temp/Bottom20Per.csv" )

    tempL = intA[int(round(len(intA)*0.8)):len(intA)]
    tempL.sort()
    tempF = floatA[int(round(len(floatA)*0.8)):len(floatA)]
    tempF.sort()
    bottom20FrameL = h2o.H2OFrame(python_obj=zip(tempL))
    bottom20FrameF = h2o.H2OFrame(python_obj=zip(tempF))
    h2o.download_csv(bottom20FrameL.cbind(bottom20FrameF), "/Users/wendycwong/temp/Top20Per.csv" )


    # repeat the columns a few times to seriously test the algo with duplicated data.

    for val in range(0, numRep):
        intA.extend(intA)
        floatA.extend(floatA)

    shuffle(intA)   # randomly shuffle the indices
    shuffle(floatA) #

    intFrame = h2o.H2OFrame(python_obj=zip(intA))
    floatFrame = h2o.H2OFrame(python_obj=zip(floatA))
    h2o.download_csv(intFrame.cbind(floatFrame), csvFile)


def genDataFrame(sizeMat, lowBound, uppderBound, numRep, numZeros, numNans, numInfs):
    '''
    This function will generate an H2OFrame of two columns.  One column will be float and the other will
    be long.
    
    :param sizeMat: integer denoting size of bounds
    :param lowBound: lower bound
    :param uppderBound: 
    :param trueRandom: 
    :param numRep: number of times to repeat arrays in order to generate duplicated rows
    :param numZeros: 
    :param numNans: 
    :param numInfs: 
    :return: 
    '''
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

def genRandomData(intA, floatA, sizeMat):
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

def genStaticData(intA, floatA, upperBoundL, lowBoundF, upperBoundF, fMult):
    for val in range(lowBoundF, upperBoundF):
        floatA.append(val*fMult)
        intA.append(upperBoundL)
        upperBoundL=upperBoundL-1
    intA.reverse()

def main(argv):
    h2o.init(strict_version_check=False)
    gen_data()

if __name__ == "__main__":
    main(sys.argv)
# if __name__ == "__main__":
#     pyunit_utils.standalone_test(gen_data)
# else:
#     gen_data()

