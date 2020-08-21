import h2o

from h2o.expr import ExprNode
from tests import pyunit_utils


def pubdev_5180():

    frame = h2o.create_frame(binary_fraction=1, binary_ones_fraction=0.5, missing_fraction=0, rows=1, cols=1)
    exp_str = ExprNode("assign", 123456789123456789123456789, frame)._get_ast_str()
    assert exp_str.find('123456789123456789L') == -1

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5180)
else:
    pubdev_5180()
