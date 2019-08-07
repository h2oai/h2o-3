def update_param(name, param, values):
    if name == 'stopping_metric':
        values = ['AUTO', 'anomaly_score']
    return param, values


class_doc = """
Builds an Isolation Forest model. Isolation Forest algorithm samples the training frame
and in each iteration builds a tree that partitions the space of the sample observations until
it isolates each observation. Length of the path from root to a leaf node of the resulting tree
is used to calculate the anomaly score. Anomalies are easier to isolate and their average
tree path is expected to be shorter than paths of regular observations.
"""
