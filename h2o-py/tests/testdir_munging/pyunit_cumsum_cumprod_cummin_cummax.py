import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def cumsumminprodmax():
    # TODO PUBDEV-1748
    foo = h2o.H2OFrame(zip(*[[x,y] for x,y in zip(range(10),range(9,-1,-1))]))
    foo.show()

    cumsum1 = foo[0].cumsum()
    cummin1 = foo[0].cummin()
    cumprod1 = foo[1:10,0].cumprod()
    cummax1 = foo[0].cummax()

    cumsum2 = foo[1].cumsum()
    cummin2 = foo[1].cummin()
    cumprod2 = foo[0:9,1].cumprod()
    cummax2 = foo[1].cummax()

    assert cumsum1[9,0] == cumsum2[9,0] == 45, "expected cumsums to be 45, but got {0} and {1}".format(cumsum1[9,0],
                                                                                                       cumsum2[9,0])

    assert cummin1[9,0] == cummin2[9,0] == 0, "expected cummin to be 0, but got {0} and {1}".format(cummin1[9,0],
                                                                                                    cummin2[9,0])

    assert cummax1[9,0] == cummax2[9,0] == 9, "expected cummin to be 9, but got {0} and {1}".format(cummin1[9,0],
                                                                                                    cummin2[9,0])

    cumprod1.show()
    print cumprod1.dim
    assert cumprod1[8,0] == cumprod2[8,0] == 362880, "expected cumprod to be 362880, but got {0} and " \
                                                     "{1}".format(cumprod1[8,0], cumprod2[8,0])

    h2o.remove(foo)



if __name__ == "__main__":
    pyunit_utils.standalone_test(cumsumminprodmax)
else:
    cumsumminprodmax()
