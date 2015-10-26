import sys
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_1443():
    col = []
    for i in range(10000): col=col+[0, 0, 1, 1, 2, 3, 0]
    fr = h2o.H2OFrame.fromPython([[c] for c in col])
    fr.set_names(['rank'])

    mapping = h2o.H2OFrame.fromPython([[0,6], [1,7], [2,8], [3,9]])
    mapping.set_names(['rank', 'outcome'])

    merged = fr.merge(mapping,allLeft=True)

    rows, cols = merged.dim
    assert rows == 70000 and cols == 2, "Expected 70000 rows and 2 cols, but got {0} rows and {1} cols".format(rows, cols)

    threes = merged[merged['rank'] == 3].nrow
    assert threes == 10000, "Expected 10000 3's, but got {0}".format(threes)

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_1443)
