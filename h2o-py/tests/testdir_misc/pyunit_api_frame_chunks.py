import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import urllib.request, urllib.parse, urllib.error

################################################################################
#
# Verifying that REST Endpoint is able to provide reasonable information about 
# chunks.
#
################################################################################


def test_frame_chunks():
    hf = h2o.H2OFrame({'A': [1, 'NA', 2], 'B': [1, 2, 3], 'C': [4, 5, 6]})
    result = h2o.api("GET /3/FrameChunks/%s" % urllib.parse.quote(hf.frame_id))
    
    assert result["frame_id"]["name"] == hf.frame_id
    chunks = result["chunks"]
    assert len(chunks) > 0
    for chunk in result["chunks"]:
        assert chunk["node_idx"] >= 0
        assert chunk["node_idx"] < h2o.cluster().cloud_size
    assert sum(map(lambda c : c["row_count"], chunks)) == 3


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_frame_chunks)
else:
    test_frame_chunks()
