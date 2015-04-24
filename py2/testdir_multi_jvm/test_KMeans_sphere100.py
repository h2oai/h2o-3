import unittest, time, sys, random, math
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_kmeans, h2o_browse as h2b, h2o_util, h2o_import as h2i

# a truly uniform sphere
# http://stackoverflow.com/questions/5408276/python-uniform-spherical-distribution
# he offers the exact solution: http://stackoverflow.com/questions/918736/random-number-generator-that-produces-a-power-law-distribution/918782#918782
# In spherical coordinates, taking advantage of the sampling rule:
# http://stackoverflow.com/questions/2106503/pseudorandom-number-generator-exponential-distribution/2106568#2106568
CLUSTERS = 8
SPHERE_PTS = 1000000
RANDOMIZE_SPHERE_PTS = True
DIMENSIONS = 4 # 1,2 or 3
JUMP_RANDOM_ALL_DIRS = True
# should do this, but does it make h2o kmeans fail?
SHUFFLE_SPHERES = False
R_NOISE = True
ALLOWED_CENTER_DELTA = 3

def get_xyz_sphere(R):
    u = random.random() # 0 to 1
    # add a little noise
    r = R * (u ** (1.0/3))
    if R_NOISE:
        rNoise = random.random() * .1 * r
        r += rNoise

    costheta = random.uniform(-1,1)
    theta = math.acos(costheta)

    phi = random.uniform(0, 2 * math.pi)
    # now you have a (r, theta, phi) group which can be transformed to (x, y, z) 
    x = r * math.sin(theta) * math.cos(phi)
    y = r * math.sin(theta) * math.sin(phi)
    z = r * math.cos(theta) 
    # use these for jump dimensions? (picture "time" and other dimensions)
    zz = 0
    yy = 0
    xyzzy = [x, y, z, zz, yy]
    ### print xyz[:DIMENSIONS]
    return xyzzy[:DIMENSIONS]

def write_spheres_dataset(csvPathname, CLUSTERS, n):
    dsf = open(csvPathname, "w+")

    # going to do a bunch of spheres, with differing # of pts and R
    # R is radius of the spheres
    # separate them by 3 * the previous R
    # keep track of the centers so we compare to a sorted result from H2O
    print "To keep life interesting:"
    print "make the multiplier, between 3 and 9 in just one direction"
    print "pick x, y, or z direction randomly"
    print "We tack the centers created, and assert against the H2O results, so 'correct' is checked"
    print "Increasing radius for each basketball. (R)"
    centersList = []
    currentCenter = None
    totalRows = 0
    for sphereCnt in range(CLUSTERS):
        R = 10 * (sphereCnt+1)
        if JUMP_RANDOM_ALL_DIRS:
            jump = random.randint(10*R,(10*R)+10)
            xyzChoice = random.randint(0,DIMENSIONS-1)
        else:
            jump = 10*R
            if DIMENSIONS==5:
                # limit jumps to yy
                xyzChoice = 4
            elif DIMENSIONS==3:
                # limit jumps to z
                # xyzChoice = 2
                xyzChoice = 0
            else:
                # limit jumps to x
                xyzChoice = 0

        zeroes = [0] * DIMENSIONS
        newOffset = zeroes
        # FIX! problems if we don't jump the other dimensions?
        # try jumping in all dimensions
        # newOffset[xyzChoice] = jump
        newOffset = [jump] * DIMENSIONS

        # figure out the next center
        if currentCenter is None:
            currentCenter = zeroes
        else:
            lastCenter = currentCenter
            currentCenter  = [a+b for a,b in zip(currentCenter, newOffset)] 
            if (sum(currentCenter) - sum(lastCenter) < (len(currentCenter)* ALLOWED_CENTER_DELTA)):
                print "ERROR: adjacent centers are too close for our sort algorithm"
                print "currentCenter:", currentCenter, "lastCenter:", lastCenter
                raise Exception
                
        centersList.append(currentCenter)

        # build a sphere at that center
        # fixed number of pts?
        if RANDOMIZE_SPHERE_PTS:
            # pick a random # of points, from .5n to 1.5n
            numPts = random.randint(int(.5*n), int(1.5*n))
        else:
            numPts = n

        print "currentCenter:", currentCenter, "R:", R, "numPts", numPts
        for i in range(numPts):
            xyz = get_xyz_sphere(R)
            xyzShifted  = [a+b for a,b in zip(xyz,currentCenter)] 
            dsf.write(",".join(map(str,xyzShifted))+"\n")
            totalRows += 1

    dsf.close()
    print "Spheres created:", len(centersList), "totalRows:", totalRows
    return centersList


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(2, java_heap_GB=7)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_kmeans_sphere100(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        csvFilename = 'syn_spheres100.csv'
        csvPathname = SYNDATASETS_DIR + '/' + csvFilename

        centersList = write_spheres_dataset(csvPathname, CLUSTERS, SPHERE_PTS)

        if SHUFFLE_SPHERES:
            # since we create spheres in order
            csvFilename2 = 'syn_spheres100_shuffled.csv'
            csvPathname2 = SYNDATASETS_DIR + '/' + csvFilename2
            h2o_util.file_shuffle(csvPathname, csvPathname2)
        else:
            csvFilename2 = csvFilename
            csvPathname2 = csvPathname

        print "\nStarting", csvFilename
        parseResult = h2i.import_parse(path=csvPathname2, schema='put', hex_key=csvFilename2 + ".hex")
        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)
        parse_key = pA.parse_key
        numRows = iA.numRows
        numCols = iA.numCols
        labelList = iA.labelList

        numColsUsed = numCols
        labelListUsed = labelList

        ### h2b.browseTheCloud()

        # try 5 times, to see if all inits by h2o are good
        # does it break if cols is not specified?
        destination_key = 'syn_spheres100.hex'
        cols = ",".join(map(str,range(DIMENSIONS)))
        for trial in range(2):
            parameters = {
                'validation_frame': parse_key,
                'ignored_columns': None,
                'k': CLUSTERS,
                'max_iterations': 50,
                'standardize': False,
                # 'seed': kmeansSeed,
                'init': 'Furthest', # [u'Random', u'PlusPlus', u'Furthest', u'User']
                # 'drop_na20_cols': False,
                # 'user_points': userPointsKey
            }

            timeoutSecs = 100
            model_key = 'sphere100_k.hex'
            kmeansResult = h2o.n0.build_model(
                algo='kmeans',
                destination_key=model_key,
                training_frame=parse_key,
                parameters=parameters,
                timeoutSecs=timeoutSecs)

            modelResult = h2o.n0.models(key=model_key)
            km = h2o_kmeans.KMeansObj(modelResult, parameters, numRows, numColsUsed, labelListUsed)

            # no expected row/error?
            expected = [(None, c, None, None) for c in centersList] 
            expected.sort(key=lambda tup: sum(tup[1]))
            h2o_kmeans.compareResultsToExpected(km.tuplesSorted, expected, allowedDelta=[.01, .01, .01])

            print "Trial #", trial, "completed"

if __name__ == '__main__':
    h2o.unit_main()
