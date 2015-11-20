import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils





def bmp_unicode_chars():
    
    

    # get all h2o-supported utf-8 characters (the basic multilingual plane, minus some control characters)
    codes_in_decimal = open(pyunit_utils.locate("smalldata/unicode/h2o_supported_utf8_codes.csv"))
    codes_in_uni = [[unichr(int(code.strip())).encode('utf-8')] for code in codes_in_decimal]
    print codes_in_uni[0:10]

    # load them into h2o
    codes_in_h2o = h2o.H2OFrame(codes_in_uni)

    # retrieve the codes from h2o and compare them to their ground-truth encoding
    for idx, u in enumerate(codes_in_uni):
        assert codes_in_h2o[idx,0] == u[0], "expected h2o to encode characters correctly, but h2o: {0}, actual: " \
                                            "{1}".format(codes_in_h2o[idx,0],u[0])




if __name__ == "__main__":
    pyunit_utils.standalone_test(bmp_unicode_chars)
else:
    bmp_unicode_chars()
