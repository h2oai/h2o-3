import sys
sys.path.insert(1,"../../")
import h2o
from h2o.exceptions import H2OResponseError
from tests import pyunit_utils


def import_file_recursive_reference():
    try:
        base_url = h2o.connection().base_url
        file_url = f"{base_url}/3/ImportFiles?path="
        file_url += file_url + pyunit_utils.locate("smalldata/extdata/iris_wheader.csv")
        
        print(file_url)
        
        h2o.api("POST /3/ImportFiles", data={
          "path": file_url
        })
        assert False, "Test should fail for exception"
    except H2OResponseError as e:
        assert "Recursive path reference" in str(e)


if __name__ == "__main__":
    pyunit_utils.standalone_test(import_file_recursive_reference)
else:
    import_file_recursive_reference()
