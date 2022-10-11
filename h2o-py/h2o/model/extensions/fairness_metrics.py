import h2o
from h2o.utils.typechecks import assert_is_type


class FairnessMetrics:

    def fairness_metrics(self, frame, protected_columns, reference, favorable_class):
        """
        Calculate intersectional fairness metrics.

        :param frame: Frame used to calculate the metrics.
        :param protected_columns: List of categorical columns that contain sensitive information
                                  such as race, gender, age etc.
        :param reference: List of values corresponding to a reference for each protected columns.
                          If set to None, it will use the biggest group as the reference.
        :param favorable_class: Positive/favorable outcome class of the response.

        :return: Dictionary of frames. One frame is the overview, other frames contain dependence
                 of performance on threshold for each protected group.
        """
        from h2o.expr import ExprNode
        assert_is_type(frame, h2o.H2OFrame)
        assert_is_type(protected_columns, [str])
        assert_is_type(reference, [str], None)
        assert_is_type(favorable_class, str)

        expr = ExprNode(
            "fairnessMetrics",
            self,
            frame,
            protected_columns,
            reference,
            favorable_class)
        res = expr._eager_map_frame()
        return {n: h2o.get_frame(f["key"]["name"]) for n, f in zip(res.map_keys["string"], res.frames)}
