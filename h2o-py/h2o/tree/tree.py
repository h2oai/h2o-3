import h2o
import math
from h2o.estimators import H2OXGBoostEstimator

class H2OTree():
    """
    Represents a model of a Tree built by one of H2O's tree algorithms (GBM, Random Forest, XGBoost, Isolation Forest).
    
    The internal structure mimics the behavior of Scikit's internal tree representation and contains all the information available about every node in the graph.
    (https://scikit-learn.org/stable/auto_examples/tree/plot_unveil_tree_structure.html)
    It provides both human-readable output and formatting suitable for machine processing.
    
    In the fetched object, there are two representations of the graph contained:

    - graph-oriented representation, starting with a root node with edges
    - array/vector representation, useful for quick machine processing of the tree’s structure
    
    Every graph starts with a root node, which is in fact an instance of H2OSplitNode and is naturally the very beginning of all decision paths in the graph.
    It points to its children, which again point to their children, unless an H2OLeafNode is hit. Nodes are always numbered from the top of the tree down to the lowest level, from left to right.
    
    At the tree level, the following information is provided:

    - Number of nodes in the tree
    - Model the tree belongs to
    - Tree class (if applicable,
    - Pointer to a root node for tree traversal (breadth-first, depth-first) and manual tree walking
    
    Each node in the tree is uniquely identified by an ID, regardless of its type. Also for each node type, a human-redable description is available. There are two types nodes distinguished:
    
    - Split node
    - Leaf node
        
    **Split Node**: A split node is a single non-terminal node with either numerical or categorical feature split. The root node is guaranteed to be a split node, as a zero-depth tree :math:`t` of cardinality :math:`|t| = 1` contains no decisions at all. 
    Every split node consists of:
    
    1. H2O-specific node identifier (ID - all nodes have it)
    2. Left child node & right child node
    3. Split feature name (split column name)
    4. Split threshold (mainly for numerical splits)
    5. Categorical features split (categorical splits only)
    6. Direction of NA values (which way NA values go - left child, right child, or nowhere)
    
    **Leaf Node**: A leaf node is a single node with no children, thus being a terminal node at the end of the decision path in a tree. Leaf node consists of:

    1. H2O-specific node identifier (ID - all nodes have it)
    2. Prediction value (floating point number)
    
    :param model: The model this tree is related to.
    :param tree_number: An integer representing the order in which the tree has been built in the model.
    :param tree_class: A string representing the name of the tree's class. Specifies the class of the tree requested. Required for multi-class classification. The number of tree classes equals the number of levels in categorical response column. As there is exactly one class per categorical level, the name of the tree's class is equal to the corresponding categorical level of the response column. In case of regression and binomial models, the name of the categorical level is ignored and can be omitted.

    :examples:
    
    >>> from h2o.tree import H2OTree
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
    >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
    >>> gbm.train(x=["Origin", "Dest"],
    ...           y="IsDepDelayed",
    ...           training_frame=airlines)
    >>> # Obtaining a tree is a matter of a single call
    >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
    >>> tree.model_id
    >>> tree.tree_number
    >>> tree.tree_class
    
    """

    def __init__(self, model, tree_number, tree_class=None):
        params = {"model": model.model_id,
                  "tree_number": tree_number,
                  "tree_class": tree_class}
        response = h2o.api(endpoint="GET /3/Tree", data=params)

        self._left_children = response['left_children']
        self._right_children = response['right_children']
        self._node_ids = self.__extract_internal_ids(response['root_node_id'])
        self._descriptions = response['descriptions']
        self._model_id = model.model_id
        self._tree_number = response['tree_number']
        self._tree_class = response['tree_class']
        self._thresholds = self.__convert_threshold_nans(response['thresholds'])
        self._features = response['features']
        self._levels = self.__decode_categoricals(model, response['levels'])
        self._nas = response['nas']
        self._predictions = response['predictions']
        self._root_node = self.__assemble_tree(0)

    @property
    def left_children(self):
        """
        An array with left child nodes of tree's nodes.  Holds indices of each node’s left child.
        
        Use the node's ID to obtain the index of the left child for a given node in the underlying array. 

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.left_children
        """
        return self._left_children

    @property
    def right_children(self):
        """
        An array with right child nodes of tree's nodes. Holds indices of each node’s right child.
        
        Use node's ID to obtain index of the right child for given node in the underlying array. 

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.right_children
        """
        return self._right_children

    @property
    def node_ids(self):
        """
        Array with identification numbers of nodes. Node IDs are generated by H2O.
        Serves as the node’s unique identifier inside H2O. (May differ from index.)
        Nodes are always numbered from the top of the tree down to the lowest level, from left to right.
        
        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.node_ids
        """
        return self._node_ids

    @property
    def descriptions(self):
        """
        Descriptions for each node to be found in the tree, in human-readable format. Provides a human-readable summary of each node.
        Contains split threshold if the split is based on numerical column.
        For categorical splits, it contains a list of categorical levels for transition from the parent node.
        
        Use the node's ID to access the description for a given node in the underlying array.

        :examples:
        
        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.descriptions
        """
        return self._descriptions

    @property
    def model_id(self):
        """
        Name (identification) of the model this tree is related to.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.model_id
        """
        return self._model_id

    @property
    def tree_number(self):
        """The order in which the tree has been built in the model.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.tree_number
        """
        return self._tree_number

    @property
    def tree_class(self):
        """The name of a tree's class.
        
        The number of tree classes equals the number of levels in the categorical response column. As there 
        is exactly one class per categorical level, the name of tree's class is equal to the corresponding 
        categorical level of the response column.
        
        In the case of regression and binomial, the name of the categorical level is ignored and can be omitted, as 
        there is exactly one tree built in both cases.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.tree_class
        """
        return self._tree_class

    @property
    def thresholds(self):
        """
        Node split thresholds. Split thresholds are not only related to numerical splits but might be present
        in case of categorical split as well.
        
        Use the node's ID to access the threshold for a given node in the underlying array.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.thresholds
        """
        return self._thresholds

    @property
    def features(self):
        """
        Names of the feature/column used for the split. The array tells which feature is used for the split on a given node.
        
        Use the node's ID to access the split feature for a given node in the underlying array. 

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.features
        """
        return self._features

    @property
    def levels(self):
        """
        Categorical levels on the split from the parent's node belonging into this node. None for root node or
        non-categorical splits. Show list of categorical levels inherited by each node from the parent.
        
        Use the node's ID to access inherited categorical levels for a given node in the underlying array. 

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.levels
        """
        return self._levels

    @property
    def nas(self):
        """
        NA value direction on split. Shows whether NA values go to the left node or the right node. The value may be None if node is a leaf
        or if there is no possibility of an NA value occuring during a split, typically due to filtering all NAs out to a different path in the graph.
        
        Use the node's ID to access NA direction for a given node in the underlying array. 

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.nas
        """
        return self._nas

    @property
    def root_node(self):
        """An instance of H2ONode representing the beginning of the tree behind the model.
        Allows further tree traversal. 

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.root_node
         """
        return self._root_node

    @property
    def predictions(self):
        """
        Values predicted on tree's nodes.
        
        Use the node's ID to access predictions for a given node in the underlying array. 

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.predictions
        """
        return self._predictions

    def __convert_threshold_nans(self, thresholds):
        for i in range(0, len(thresholds)):
            if thresholds[i] == "NaN": thresholds[i] = float('nan')
        return thresholds

    def __assemble_tree(self, node):
        if node == -1: return None

        left_child = self._left_children[node]
        right_child = self._right_children[node]

        if left_child == -1 and right_child == -1:
            return H2OLeafNode(node_id=self._node_ids[node],
                               prediction=self._predictions[node])
        else:
            return H2OSplitNode(node_id=self._node_ids[node],
                                left_child=self.__assemble_tree(left_child),
                                right_child=self.__assemble_tree(right_child),
                                threshold=self._thresholds[node],
                                split_feature=self._features[node],
                                na_direction=self._nas[node],
                                left_levels=self._levels[left_child],
                                right_levels = self._levels[right_child])

    def __decode_categoricals(self, model, levels):
        string_levels = len(self._left_children) * [None]

        if type(model) is H2OXGBoostEstimator:
            return string_levels

        for i in range(0, len(self._left_children)):
            if (self._features[i] is None): continue
            left_node = self._left_children[i]
            right_node = self._right_children[i]
            split_column_index = model._model_json["output"]["names"].index(self._features[i])
            domain = model._model_json["output"]["domains"][split_column_index]
            if domain is None: continue

            if left_node != -1:
                left_levels = []
                if levels[left_node] is not None:
                    for lvl_index in levels[left_node]:
                        left_levels.append(domain[lvl_index])

                string_levels[left_node] = left_levels

            if right_node != -1:
                right_levels = []
                if levels[right_node] is not None:
                    for lvl_index in levels[right_node]:
                        right_levels.append(domain[lvl_index])

                string_levels[right_node] = right_levels

        return string_levels

    def __extract_internal_ids(self, root_node_id):
        node_index = 0
        node_ids = [root_node_id]
        for i in range(0, len(self._left_children)):
            if (self._left_children[i] != -1):
                node_index = node_index + 1
                node_ids.append(self._left_children[i])
                self._left_children[i] = node_index
            else:
                self._left_children[i] = -1

            if (self._right_children[i] != -1):
                node_index = node_index + 1
                node_ids.append(self._right_children[i])
                self._right_children[i] = node_index
            else:
                self._right_children[i] = -1

        return node_ids

    def __len__(self):
        """
        Returns number of nodes inside the tree.
        
        :examples:
        
        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> len(tree)
        """
        return len(self._node_ids)

    def __str__(self):
        return "Tree related to model {}. Tree number is {}, tree class is '{}'\n\n".format(self._model_id,
                                                                                            self._tree_number,
                                                                                            self._tree_class)
    def show(self):
        """Summarizes the H2OTree.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.show()
        """
        print(self.__str__())


class H2ONode():
    """
    Represents a single abstract node in an H2OTree.

    :param id: Node's unique identifier (integer). Generated by H2O.

    :examples:

    >>> from h2o.tree import H2OTree
    >>> from h2o.tree import H2ONode
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
    >>> gbm.train(x=["Origin", "Dest"],
    ...           y="IsDepDelayed",
    ...           training_frame=airlines)
    >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
    >>> tree.node_ids
    """

    def __init__(self, node_id):
        self._id = node_id

    @property
    def id(self):
        """Node's unique identifier (integer). Generated by H2O.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2ONode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.node_ids
        >>> node0 = H2ONode(0)
        >>> node0
        """
        return self._id

    def __str__(self):
        return "Node ID {} \n".format(self._id)


class H2OLeafNode(H2ONode):
    """
    Represents a single terminal node in an H2OTree with final prediction.

    :param id: Node's unique identifier (integer). Generated by H2O.
    :param prediction: The prediction value in the terminal node (numeric floating point).

    :examples:

    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> from h2o.tree import H2OTree
    >>> from h2o.tree import H2OLeafNode
    >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
    >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
    >>> gbm.train(x=["Origin", "Dest"],
    ...           y="IsDepDelayed",
    ...           training_frame=airlines)
    # Retrieve the node ids      
    >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
    >>> tree.node_ids
    # Retrieve the predictions
    >>> tree.predictions
    # Set the id and prediction
    >>> H2OLeafNode(0, -0.23001842)
    """

    def __init__(self, node_id, prediction):
        H2ONode.__init__(self, node_id)
        self._prediction = prediction

    @property
    def id(self):
        """Node's unique identifier (integer). Generated by H2O.

        :examples:

        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OLeafNode
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        # Retrieve the node ids      
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.node_ids
        # Retrieve the predictions
        >>> tree.predictions
        # Set the id and prediction
        >>> leaf_node = H2OLeafNode(0, -0.23001842)
        # Retrieve the node id
        >>> leaf_node.id        
        """
        return self._id

    @property
    def prediction(self):
        """Prediction value in the terminal node (numeric floating point).

        :examples:

        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OLeafNode
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        # Retrieve the node ids      
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.node_ids
        # Retrieve the predictions
        >>> tree.predictions
        # Set the id and prediction
        >>> leaf_node = H2OLeafNode(0, -0.23001842)
        # Retrieve the node prediction
        >>> leaf_node.prediction
        """
        return self._prediction
    
    def __str__(self):
        return "Leaf node ID {}. Predicted value at leaf node is {} \n".format(self._id, self._prediction)

    def show(self):
        """
        :examples:
        
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OLeafNode
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin", "Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        # Retrieve the predictions
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> tree.predictions
        # Retrieve predicted value for a node
        >>> leaf_node = H2OLeafNode(0, -0.23001842)
        >>> leaf_node.show()
        """
        print(self.__str__())
       


class H2OSplitNode(H2ONode):
    """
    Represents a single node with either numerical or categorical split in an H2OTree with all its attributes.

    :param id: Node's unique identifier (integer). Generated by H2O.
    :param threshold: Split threshold, typically when the split column is numerical.
    :param left_child: Integer identifier of the left child node, if there is any. Otherwise None.
    :param right_child: Integer identifier of the right child node, if there is and. Otherwise None.
    :param split_feature: The name of the column this node splits on.
    :param na_direction: The direction of NA values. LEFT means NA values go to the left child node; RIGHT means NA values go to the right child node. A value of None means occurence of NA for the given split column is not possible on this node due to an earlier split on the very same feature.
    :param left_levels: Categorical levels on the edge from this node to the left child node. None for non-categorical splits.
    :param right_levels: Categorical levels on the edge from this node to the right cild node. None for non-categorical splits.

    :examples:

    >>> from h2o.tree import H2OTree
    >>> from h2o.tree import H2OSplitNode
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
    >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
    >>> gbm.train(x=["Origin","Dest"],
    ...           y="IsDepDelayed",
    ...           training_frame=airlines)
    >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
    >>> threshold = 'nan'
    >>> left_child = 9
    >>> right_child = 10
    >>> split_feature = "DepTime"
    >>> na_direction = "Left"
    >>> left_levels = ['FAT', 'LAS', 'PSP']
    >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
    ...                 'PHL', 'PHX', 'RIC', 'TPA']
    >>> H2OSplitNode(5, threshold, left_child,
    ...              right_child, split_feature,
    ...              na_direction, left_levels,
    ...              right_levels)
    """

    def __init__(self, node_id, threshold, left_child, right_child, split_feature, na_direction, left_levels, right_levels):
        H2ONode.__init__(self, node_id)
        self._threshold = threshold
        self._left_child = left_child
        self._right_child = right_child
        self._split_feature = split_feature
        self._na_direction = na_direction
        self._left_levels = left_levels
        self._right_levels = right_levels

    @property
    def id(self):
        """Node's unique identifier (integer). Generated by H2O.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OSplitNode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin","Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> threshold = 'nan'
        >>> left_child = 9
        >>> right_child = 10
        >>> split_feature = "DepTime"
        >>> na_direction = "Left"
        >>> left_levels = ['FAT', 'LAS', 'PSP']
        >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
        ...                 'PHL', 'PHX', 'RIC', 'TPA']
        >>> split_node = H2OSplitNode(5, threshold, left_child,
        ...                           right_child, split_feature,
        ...                           na_direction, left_levels,
        ...                           right_levels)
        >>> split_node.id
        """
        return self._id

    @property
    def threshold(self):
        """
        Split threshold for each node. Not only numerical features have numerical split. For splits on categorical features,
        the numerical split threshold represents an index in the categorical feature's domain.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OSplitNode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin","Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> threshold = 'nan'
        >>> left_child = 9
        >>> right_child = 10
        >>> split_feature = "DepTime"
        >>> na_direction = "Left"
        >>> left_levels = ['FAT', 'LAS', 'PSP']
        >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
        ...                 'PHL', 'PHX', 'RIC', 'TPA']
        >>> split_node = H2OSplitNode(5, threshold, left_child,
        ...                           right_child, split_feature,
        ...                           na_direction, left_levels,
        ...                           right_levels)
        >>> split_node.threshold
        """
        return self._threshold

    @property
    def left_child(self):
        """
        Integer identifier of the left child node, if there is any. Otherwise None.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OSplitNode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin","Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> threshold = 'nan'
        >>> left_child = 9
        >>> right_child = 10
        >>> split_feature = "DepTime"
        >>> na_direction = "Left"
        >>> left_levels = ['FAT', 'LAS', 'PSP']
        >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
        ...                 'PHL', 'PHX', 'RIC', 'TPA']
        >>> split_node = H2OSplitNode(5, threshold, left_child,
        ...                           right_child, split_feature,
        ...                           na_direction, left_levels,
        ...                           right_levels)
        >>> split_node.left_child
        """
        return self._left_child

    @property
    def right_child(self):
        """Integer identifier of the right child node, if there is any. Otherwise None.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OSplitNode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin","Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> threshold = 'nan'
        >>> left_child = 9
        >>> right_child = 10
        >>> split_feature = "DepTime"
        >>> na_direction = "Left"
        >>> left_levels = ['FAT', 'LAS', 'PSP']
        >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
        ...                 'PHL', 'PHX', 'RIC', 'TPA']
        >>> split_node = H2OSplitNode(5, threshold, left_child,
        ...                           right_child, split_feature,
        ...                           na_direction, left_levels,
        ...                           right_levels)
        >>> split_node.right_child
        """
        return self._right_child

    @property
    def split_feature(self):
        """The name of the column this node splits on.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OSplitNode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin","Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> threshold = 'nan'
        >>> left_child = 9
        >>> right_child = 10
        >>> split_feature = "DepTime"
        >>> na_direction = "Left"
        >>> left_levels = ['FAT', 'LAS', 'PSP']
        >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
        ...                 'PHL', 'PHX', 'RIC', 'TPA']
        >>> split_node = H2OSplitNode(5, threshold, left_child,
        ...                           right_child, split_feature,
        ...                           na_direction, left_levels,
        ...                           right_levels)
        >>> split_node.split_feature
        """
        return self._split_feature

    @property
    def na_direction(self):
        """
        The direction of NA values. LEFT means NA values go to the left child node; RIGHT means NA values go to the right child node. A value of None means occurance of NA for the given split column is not possible on this node due to an earlier split on the very same feature.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OSplitNode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin","Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> threshold = 'nan'
        >>> left_child = 9
        >>> right_child = 10
        >>> split_feature = "DepTime"
        >>> na_direction = "Left"
        >>> left_levels = ['FAT', 'LAS', 'PSP']
        >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
        ...                 'PHL', 'PHX', 'RIC', 'TPA']
        >>> split_node = H2OSplitNode(5, threshold, left_child,
        ...                           right_child, split_feature,
        ...                           na_direction, left_levels,
        ...                           right_levels)
        >>> split_node.na_direction
         """
        return self._na_direction

    @property
    def left_levels(self):
        """
        Categorical levels on the edge from this node to the left child node. None for non-categorical splits.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OSplitNode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin","Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> threshold = 'nan'
        >>> left_child = 9
        >>> right_child = 10
        >>> split_feature = "DepTime"
        >>> na_direction = "Left"
        >>> left_levels = ['FAT', 'LAS', 'PSP']
        >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
        ...                 'PHL', 'PHX', 'RIC', 'TPA']
        >>> split_node = H2OSplitNode(5, threshold, left_child,
        ...                           right_child, split_feature,
        ...                           na_direction, left_levels,
        ...                           right_levels)
        >>> split_node.left_levels
         """
        return self._left_levels

    @property
    def right_levels(self):
        """
        Categorical levels on the edge from this node to the right child node. None for non-categorical splits.

        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OSplitNode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin","Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> threshold = 'nan'
        >>> left_child = 9
        >>> right_child = 10
        >>> split_feature = "DepTime"
        >>> na_direction = "Left"
        >>> left_levels = ['FAT', 'LAS', 'PSP']
        >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
        ...                 'PHL', 'PHX', 'RIC', 'TPA']
        >>> split_node = H2OSplitNode(5, threshold, left_child,
        ...                           right_child, split_feature,
        ...                           na_direction, left_levels,
        ...                           right_levels)
        >>> split_node.right_levels
        """
        return self._right_levels

    def __str__(self):
        out = "Node ID {} \n".format(self._id)
        if self._split_feature is not None:
            if self._left_child is not None:
                out += "Left child node ID = {}\n".format(self.left_child.id)
            else:
                out += "There is no left child\n"
            if self._right_child is not None:
                out += "Right child node ID = {}\n".format(self.right_child.id)
            else:
                out += "There is no right child\n"

            out += "\nSplits on column {}\n".format(self._split_feature)

        else:
            out += "This is a terminal node"

        if math.isnan(self._threshold):
            if self._left_child is not None:
                out += "  - Categorical levels going to the left node: {}\n".format(self._left_levels)
            if self._right_child is not None:
                out += "  - Categorical levels going to the right node: {}\n".format(self._right_levels)

        else:
            out += "Split threshold < {} to the left node, >= {} to the right node \n".format(self._threshold,
                                                                                              self._threshold)

        if self._na_direction is not None: out += "\nNA values go to the {}".format(self._na_direction)

        return out

    def show(self):
        """
        :examples:

        >>> from h2o.tree import H2OTree
        >>> from h2o.tree import H2OSplitNode
        >>> from h2o.estimators import H2OGradientBoostingEstimator
        >>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
        >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
        >>> gbm.train(x=["Origin","Dest"],
        ...           y="IsDepDelayed",
        ...           training_frame=airlines)
        >>> tree = H2OTree(model = gbm, tree_number = 0 , tree_class = "NO")
        >>> threshold = 'nan'
        >>> left_child = 9
        >>> right_child = 10
        >>> split_feature = "DepTime"
        >>> na_direction = "Left"
        >>> left_levels = ['FAT', 'LAS', 'PSP']
        >>> right_levels = ['BWI', 'CLT', 'IND', 'MCO',
        ...                 'PHL', 'PHX', 'RIC', 'TPA']
        >>> split_node = H2OSplitNode(5, threshold, left_child,
        ...                           right_child, split_feature,
        ...                           na_direction, left_levels,
        ...                           right_levels)
        >>> split_node.show
        """
        print(self.__str__())
        
