
import h2o
import pandas as pd
import tempfile
from tests import pyunit_utils


def pubdev_5394():
    l = list(range(100))
    l.append("#")
    tmp = tempfile.NamedTemporaryFile(suffix='.tsv')
    try:
        df = pd.DataFrame({1: l, 2: l, 3: l, 4: l, 5: l})
        df.to_csv(tmp.name, sep="\t", index=False)

        # One line ignored in default settings
        df = h2o.import_file(tmp.name, header=1)
        assert df.nrows == len(l) - 1

        # No lines ignored, as non-data line markers are overridden/empty
        df = h2o.import_file(tmp.name, header=1, custom_non_data_line_markers='')
        assert df.nrows == len(l)
    finally:
        tmp.close()

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5394)
else:
    pubdev_5394()
