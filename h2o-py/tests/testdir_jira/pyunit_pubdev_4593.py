import h2o

from tests import pyunit_utils


def pubdev_4593():
    words = ['A', 'AA', 'AAA', 'AAAA', 'AAAAA', 'AAAAAA']
    compare = ['a', 'aa', 'aaa', 'aaaa', 'aaaaa', 'aaaaaa']
    words = h2o.H2OFrame(words)
    compare = h2o.H2OFrame(compare)
    dist = words.strdistance(compare, 'lv')
    
    for row in range(0, dist[0].nrows):
        assert dist[row, 0] == 0, "Levenshtein outcome must be exactly zero"


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_4593)
else:
    pubdev_4593()
