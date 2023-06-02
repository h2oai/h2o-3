import h2o
from h2o.exceptions import H2OValueError
from h2o.job import H2OJob


class RowToTreeAssignment:

    def _row_to_tree_assignment(self, test_data):
        if not isinstance(test_data, h2o.H2OFrame): raise H2OValueError("test_data must be an instance of H2OFrame")
        j = H2OJob(h2o.api("POST /4/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                           data={"row_to_tree_assignment": True}), "Row to tree assignment")
        j.poll()
        return h2o.get_frame(j.dest_key)
