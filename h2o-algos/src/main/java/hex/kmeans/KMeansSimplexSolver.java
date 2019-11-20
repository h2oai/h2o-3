package hex.kmeans;

import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Calculate Minimal Cost Flow problem using simplex method with go through spanning tree.
 * Used to solve minimal cluster size constraints in K-means.
 */
class KMeansSimplexSolver {
    public int _constraintsLength;
    public long _numberOfPoints;
    public long _edgeSize;
    public long _nodeSize;
    public long _resultSize;

    // Input graph to store K-means configuration
    public Vec _nodes; // store data points indices, constraints + additive node
    public Vec _edges; // store edges indices between data points and constraints (distances * precision), edges between constraints and additive node
    public Vec _demands; // store demand of all nodes (-1 for data points, constraints values for constraints nodes, )
    public Vec _capacities; // store capacities of all edges + edges from all node to leader node
    public Frame _weights; // input data + weight column, calculated distances from all points to all centres + columns to store result cluster assignments 
    public Vec _additiveWeights; // additive weight vector to store edge weight from constraints nodes to additive and leader nodes
    public long _sumWeights; // calculated sum of all weights to calculate maximal capacity value
    public long _maxAbsDemand; // maximal absolute demand to calculate maximal capacity value
    public boolean _hasWeightsColumn; // weight column existence flag
    public int _precision; // precision for calculation where weight is originally double number 

    // Spanning tree to calculate min cost flow
    public SpanningTree tree;
    
    // variables to split calculation in smaller blocks to speed up calculation
    long numberOfConsecutiveBlocks = 0;
    long firstEdgeInBlock = 0;
    long nextBlockOfEdges;

    /**
     * Construct K-means solver.
     * @param constrains 
     * @param weights
     * @param sumWeights
     * @param hasWeights
     * @param precision
     */
    public KMeansSimplexSolver(int[] constrains, Frame weights, long sumWeights, boolean hasWeights, int precision) {
        this._numberOfPoints = (int) weights.numRows();
        this._nodeSize = this._numberOfPoints + constrains.length + 1;
        this._edgeSize = _numberOfPoints * constrains.length + constrains.length;
        this._constraintsLength = constrains.length;

        this._nodes = Vec.makeCon(0, _nodeSize, Vec.T_NUM);
        this._edges = Vec.makeCon(0, _edgeSize, Vec.T_NUM);
        this._demands = Vec.makeCon(0, _nodeSize, Vec.T_NUM);
        this._capacities = Vec.makeCon(0, _edgeSize + _nodeSize, Vec.T_NUM);
        this._resultSize = this._numberOfPoints * _constraintsLength;
        this._hasWeightsColumn = hasWeights;
        this._precision = precision;

        this._weights = weights;
        this._additiveWeights = Vec.makeCon(0, _nodeSize + _constraintsLength, Vec.T_NUM);
        this._sumWeights = sumWeights;


        long constrainsSum = 0;
        _maxAbsDemand = Long.MIN_VALUE;
        for (long i = 0; i < _nodeSize; i++) {
            _nodes.set(i, i);
            if (i < _numberOfPoints) {
                _demands.set(i, -1);
            } else {
                long tmpDemand;
                if (i < _nodeSize - 1) {
                    tmpDemand = constrains[(int)(i - _numberOfPoints)];
                    _demands.set(i, tmpDemand);
                    constrainsSum += constrains[(int)(i - _numberOfPoints)];
                } else {
                    tmpDemand = _numberOfPoints - constrainsSum;
                    _demands.set(i, tmpDemand);
                }
                if (tmpDemand > Math.abs(_maxAbsDemand)) {
                    _maxAbsDemand = Math.abs(tmpDemand);
                }
            }
        }

        for (long i = 0; i < _edgeSize; i++) {
            _edges.set(i, i);
        }

        this.tree = new SpanningTree(_nodeSize, _edgeSize, _constraintsLength);
    }

    /**
     * Initialize graph and spanning tree.
     */
    public void init() {
        // always start with infinity _capacities
        for (long i = 0; i < _edgeSize; i++) {
            _capacities.set(i, Long.MAX_VALUE);
        }

        // find maximum value for capacity
        long maxCapacity = 3 * (_sumWeights > _maxAbsDemand ? _sumWeights : _maxAbsDemand);

        // fill max capacity from the leader node to all others _nodes
        for (long i = 0; i < _nodeSize; i++) {
            _additiveWeights.set(i + _constraintsLength, maxCapacity);
            _capacities.set(i + _edgeSize, maxCapacity);
        }

        tree.init(_numberOfPoints, maxCapacity, _demands);
    }

    /**
     * Get weight base on edge index from weights data or from additive weights.
     * @param edgeIndex
     * @return
     */
    public long getWeight(long edgeIndex) {
        long numberOfFrameWeights = this._numberOfPoints * this._constraintsLength;
        if (edgeIndex < numberOfFrameWeights) {
            long i = edgeIndex % _numberOfPoints;
            int j = _weights.numCols() - _constraintsLength - 3 + (int) Math.floor(edgeIndex / _numberOfPoints);
            return (long) (_weights.vec(j).at(i)* _precision);
        }
        // warning: possible long cast problem !
        return _additiveWeights.at8(edgeIndex - numberOfFrameWeights);
    }

    /**
     * Find minimal reduced weight to select entering edge to find cycle
     * @param from
     * @param to
     * @return
     */
    public long findMinimalReducedWeight(long from, long to) {
        long minimalWeight = Long.MAX_VALUE;
        long minimalIndex = -1;
        for (long i = from; i < to; i++) {
            long tmpWeight = tree.reduceWeight(i, getWeight(i));
            if (tmpWeight < minimalWeight) {
                minimalWeight = tmpWeight;
                minimalIndex = i;
            }
        }
        return minimalIndex;
    }

    /**
     * Check edges flow where constraints flows and additive node flow should be zero at the end of calculation.
     * @return true if the flows are not zero yet
     */
    private boolean checkIfContinue() {
        for (long i = tree._edgeFlow.length() - 1; i > tree._edgeFlow.length() - _constraintsLength - 2; i--) {
            if (tree._edgeFlow.at8(i) != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find next entering edge to find cycle.
     * @return index of the edge
     */
    public Edge findNextEnteringEdge() {
        if(checkIfContinue()) {
            // split calculation to block
            // place where parallelisation is needed
            long blockSize = (long) Math.ceil(Math.sqrt(_edgeSize));
            long numberOfBlocks = Math.floorDiv(_edgeSize + blockSize - 1, blockSize);
            if (numberOfConsecutiveBlocks < numberOfBlocks) {
                nextBlockOfEdges = firstEdgeInBlock + blockSize;
                long minimalIndex;
                if (nextBlockOfEdges <= _edgeSize) {
                    minimalIndex = findMinimalReducedWeight(firstEdgeInBlock, nextBlockOfEdges);
                } else {
                    nextBlockOfEdges -= _edgeSize;
                    long fIndex = findMinimalReducedWeight(firstEdgeInBlock, _edgeSize);
                    long sIndex = findMinimalReducedWeight(0, nextBlockOfEdges);
                    if (fIndex == -1) {
                        minimalIndex = sIndex;
                    } else if (sIndex == -1) {
                        minimalIndex = fIndex;
                    } else {
                        minimalIndex = tree.reduceWeight(fIndex, getWeight(fIndex)) < tree.reduceWeight(sIndex, getWeight(sIndex)) ? fIndex : sIndex;
                    }
                    assert minimalIndex != -1;
                }
                firstEdgeInBlock = nextBlockOfEdges;
                long minimalWeight = tree.reduceWeight(minimalIndex, getWeight(minimalIndex));
                if (minimalWeight >= 0) {
                    numberOfConsecutiveBlocks += 1;
                    return findNextEnteringEdge();
                } else {
                    numberOfConsecutiveBlocks = 0;
                    if (tree._edgeFlow.at8(minimalIndex) == 0) {
                        return new Edge(minimalIndex, tree._sources.at8(minimalIndex), tree._targets.at8(minimalIndex));
                    } else {
                        return new Edge(minimalIndex, tree._targets.at8(minimalIndex), tree._sources.at8(minimalIndex));
                    }
                }
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * Find cycle from the edge defined by source and target nodes to leader node and back.
     * @param edgeIndex
     * @param sourceIndex
     * @param targetIndex
     * @return
     */
    public NodesEdgesObject getCycle(long edgeIndex, long sourceIndex, long targetIndex) {
        long ancestor = tree.findAncestor(sourceIndex, targetIndex);
        NodesEdgesObject resultPath = tree.getPath(sourceIndex, ancestor);
        resultPath.reverseNodes();
        resultPath.reverseEdges();
        if (resultPath.edgeSize() != 1 || resultPath.getEdge(0) != edgeIndex) {
            resultPath.addEdge(edgeIndex);
        }
        NodesEdgesObject resultPathBack = tree.getPath(targetIndex, ancestor);
        resultPathBack.removeLastNode();
        resultPath.addAllNodes(resultPathBack.getNodes());
        resultPath.addAllEdges(resultPathBack.getEdges());
        return resultPath;
    }

    /**
     * Find the leaving edge with minimal residual capacity.
     * @param cycle
     * @return the edge with minimal residual capacity
     */
    public Edge getLeavingEdge(NodesEdgesObject cycle) {
        cycle.reverseNodes();
        cycle.reverseEdges();
        long minResidualCapacity = Long.MAX_VALUE;
        int minIndex = -1;
        for (int i = 0; i < cycle.nodeSize(); i++) {
            long tmpResidualCapacity = tree.getResidualCapacity(cycle.getEdge(i), cycle.getNode(i), _capacities.at8(cycle.getEdge(i)));
            if (tmpResidualCapacity < minResidualCapacity) {
                minResidualCapacity = tmpResidualCapacity;
                minIndex = i;
            }
        }
        assert minIndex != -1;
        long nodeIndex = cycle.getNode(minIndex);
        long edgeIndex = cycle.getEdge(minIndex);
        return new Edge(edgeIndex, nodeIndex, nodeIndex == tree._sources.at8(edgeIndex) ? tree._targets.at8(edgeIndex) : tree._sources.at8(edgeIndex));
    }

    /**
     * Loop over all entering edges to find minimal cost flow in spanning tree.
     */
    public void pivotLoop() {
        Edge edge = findNextEnteringEdge();
        while (edge != null) {
            long enteringEdgeIndex = edge.get_edgeIndex();
            long enteringEdgeSourceIndex = edge.getSourceIndex();
            long enteringEdgeTargetIndex = edge.getTargetIndex();
            NodesEdgesObject cycle = getCycle(enteringEdgeIndex, enteringEdgeSourceIndex, enteringEdgeTargetIndex);
            Edge leavingEdge = getLeavingEdge(cycle);
            long leavingEdgeIndex = leavingEdge.get_edgeIndex();
            long leavingEdgeSourceIndex = leavingEdge.getSourceIndex();
            long leavingEdgeTargetIndex = leavingEdge.getTargetIndex();
            
            tree.augmentFlow(cycle, tree.getResidualCapacity(leavingEdgeIndex, leavingEdgeSourceIndex, _capacities.at8(leavingEdgeIndex)));
            if (enteringEdgeIndex != leavingEdgeIndex) {
                if (leavingEdgeSourceIndex != tree._parents.at8(leavingEdgeTargetIndex)) {
                    long tmpS = leavingEdgeSourceIndex;
                    leavingEdgeSourceIndex = leavingEdgeTargetIndex;
                    leavingEdgeTargetIndex = tmpS;
                }
                if (cycle.indexOfEdge(enteringEdgeIndex) < cycle.indexOfEdge(leavingEdgeIndex)) {
                    long tmpP = enteringEdgeSourceIndex;
                    enteringEdgeSourceIndex = enteringEdgeTargetIndex;
                    enteringEdgeTargetIndex = tmpP;
                }
                tree.removeParentEdge(leavingEdgeSourceIndex, leavingEdgeTargetIndex);
                tree.makeRoot(enteringEdgeTargetIndex);
                tree.addEdge(enteringEdgeIndex, enteringEdgeSourceIndex, enteringEdgeTargetIndex);
                tree.updatePotentials(enteringEdgeIndex, enteringEdgeSourceIndex, enteringEdgeTargetIndex, getWeight(enteringEdgeIndex));
            }
            edge = findNextEnteringEdge();
        }
    }

    public void checkInfeasibility() {
        assert !tree.isInfeasible();
    }
    
    public void checkConstraintsCondition(int[] numberOfPointsInCluster){
        for(int i = 0; i<_constraintsLength; i++){
            assert numberOfPointsInCluster[i] >= _demands.at8(_numberOfPoints+i) : String.format("Cluster %d has %d assigned points however should has assigned at least %d points.", i+1, numberOfPointsInCluster[i], _demands.at8(_numberOfPoints+i));
        }
    }

    /**
     * Initialize graph and working spanning tree, calculate minimal cost flow and check if result flow is correct. 
     */
    private void calculateMinimalCostFlow() {
        init();
        pivotLoop();
        checkInfeasibility();
    }

    /**
     * Calculate minimal cost flow and based on flow assign cluster to all data points.
     * @return input data with new cluster assignments
     */
    public Frame assignClusters() {
        calculateMinimalCostFlow();
        int distanceAssignmentIndex = _weights.numCols() - 3;
        int oldAssignmentIndex = _weights.numCols() - 2;
        int newAssignmentIndex = _weights.numCols() - 1;
        int dataStopLength = _weights.numCols() - (_hasWeightsColumn ? 1 : 0) - _constraintsLength - 3;
        
        int[] numberOfPointsInCluster = new int[_constraintsLength];
        for(int i = 0; i<_constraintsLength; i++){
            numberOfPointsInCluster[i] = 0;
        }
        
        for (long i = 0; i < _weights.numRows(); i++) {
            for (int j = 0; j < _constraintsLength; j++) {
                if (tree._edgeFlow.at8(i * _constraintsLength + j) == 1) {
                    // old assignment
                    _weights.vec(oldAssignmentIndex).set(i, _weights.vec(newAssignmentIndex).at(i));
                    // new assignment
                    _weights.vec(newAssignmentIndex).set(i, j);
                    // distances
                    _weights.vec(distanceAssignmentIndex).set(i, _weights.vec(dataStopLength + j).at(i));
                    numberOfPointsInCluster[j]++;
                    break;
                }
            }
        }
        checkConstraintsCondition(numberOfPointsInCluster);
        return _weights;
    }
}

/**
 * Class to store calculation of flow in minimal cost flow problem.
 */
class SpanningTree {

    public long _nodeSize;
    public long _edgeSize;
    public long _secondLayerSize;


    public Vec _sources;   //    edge size + node size
    public Vec _targets;   //    edge size + node size

    public Vec _edgeFlow;          //         edge size + node size, integer
    public Vec _nodePotentials;    //         node size, long
    public Vec _parents;           //         node size + 1, integer
    public Vec _parentEdges;       //         edge size, integer
    public Vec _subtreeSize;       //         node size + 1, integer
    public Vec _nextDepthFirst;    //         node size + 1, integer
    public Vec _previousNodes;     //         node size + 1, integer
    public Vec _lastDescendants;    //         node size + 1, integer

    SpanningTree(long nodeSize, long edgeSize, long secondLayerSize){
        this._nodeSize = nodeSize;
        this._edgeSize = edgeSize;
        this._secondLayerSize = secondLayerSize;

        this._sources = Vec.makeCon(0, edgeSize+nodeSize, Vec.T_NUM);
        this._targets = Vec.makeCon(0, edgeSize+nodeSize, Vec.T_NUM);

        this._edgeFlow =  Vec.makeCon(0, edgeSize+nodeSize, Vec.T_NUM);
        this._nodePotentials =  Vec.makeCon(0, nodeSize, Vec.T_NUM);
        this._parents =  Vec.makeCon(0, nodeSize+1, Vec.T_NUM);
        this._parentEdges =  Vec.makeCon(0, edgeSize, Vec.T_NUM);
        this._subtreeSize =  Vec.makeCon(1, nodeSize+1, Vec.T_NUM);
        this._nextDepthFirst =  Vec.makeCon(0, nodeSize+1, Vec.T_NUM);
        this._previousNodes =  Vec.makeCon(0, nodeSize+1, Vec.T_NUM);
        this._lastDescendants =  Vec.makeCon(0, nodeSize+1, Vec.T_NUM);
    }

    public void init(long numberOfPoints, long maxCapacity, Vec demands){
        for (long i = 0; i < _nodeSize; i++) {
            if (i < numberOfPoints) {
                for (int j = 0; j < _secondLayerSize; j++) {
                    _sources.set(i* _secondLayerSize +j, i);
                    _targets.set(i* _secondLayerSize +j, numberOfPoints + j);
                }
            } else {
                if (i < _nodeSize - 1) {
                    _sources.set(numberOfPoints* _secondLayerSize +i-numberOfPoints, i);
                    _targets.set(numberOfPoints* _secondLayerSize +i-numberOfPoints, _nodeSize - 1);
                }
            }
        }

        for (long i = 0; i < _nodeSize; i++) {
            long demand = demands.at8(i);
            if (demand > 0) {
                _sources.set(_edgeSize +i, _nodeSize);
                _targets.set(_edgeSize +i, i);
            } else {
                _sources.set(_edgeSize +i, i);
                _targets.set(_edgeSize +i, _nodeSize);
            }
            _edgeFlow.set(_edgeSize +i, Math.abs(demand));
            _nodePotentials.set(i, demand <= 0 ? maxCapacity : -maxCapacity);
            _parents.set(i, _nodeSize);
            _parentEdges.set(i, i + _edgeSize);
            _previousNodes.set(i, i - 1);
            _lastDescendants.set(i, i);
            if (i < _nodeSize - 1) {
                _nextDepthFirst.set(i, i + 1);
            }
        }
        _parents.set(_nodeSize, -1);
        _subtreeSize.set(_nodeSize, _nodeSize + 1);
        _nextDepthFirst.set(_nodeSize - 1, _nodeSize);
        _previousNodes.set(0, _nodeSize);
        _previousNodes.set(_nodeSize, _nodeSize - 1);
        _lastDescendants.set(_nodeSize, _nodeSize - 1);
    }

    public boolean isInfeasible() {
        for(long i = _edgeFlow.length() - _secondLayerSize + 1; i < _edgeFlow.length(); i++) {
            if(_edgeFlow.at8(i) > 0){
                return true;
            }
        }
        return false;
    }

    public long findAncestor(long sourceIndex, long targetIndex) {
        long subtreeSizeSource = _subtreeSize.at8(sourceIndex);
        long subtreeSizeTarget = _subtreeSize.at8(targetIndex);
        while (true) {
            while (subtreeSizeSource < subtreeSizeTarget) {
                sourceIndex = _parents.at8(sourceIndex);
                subtreeSizeSource = _subtreeSize.at8(sourceIndex);
            }
            while (subtreeSizeSource > subtreeSizeTarget) {
                targetIndex = _parents.at8(targetIndex);
                subtreeSizeTarget = _subtreeSize.at8(targetIndex);
            }
            if (subtreeSizeSource == subtreeSizeTarget) {
                if (sourceIndex !=targetIndex) {
                    sourceIndex = _parents.at8(sourceIndex);
                    subtreeSizeSource = _subtreeSize.at8(sourceIndex);
                    targetIndex = _parents.at8(targetIndex);
                    subtreeSizeTarget = _subtreeSize.at8(targetIndex);
                } else {
                    return sourceIndex;
                }
            }
        }
    }

    public Long reduceWeight(long edgeIndex, long weight) {
        long newWeight = weight - _nodePotentials.at8(_sources.at8(edgeIndex)) + _nodePotentials.at8(_targets.at8(edgeIndex));
        return _edgeFlow.at8(edgeIndex) == 0 ? newWeight : -newWeight;
    }

    public NodesEdgesObject getPath(long node, long ancestor) {
        NodesEdgesObject result = new NodesEdgesObject();
        result.addNode(node);
        while (node != ancestor) {
            result.addEdge(_parentEdges.at8(node));
            node = _parents.at8(node);
            result.addNode(node);
        }
        return result;
    }

    public long getResidualCapacity(long edgeIndex, long nodeIndex, long capacity) {
        return nodeIndex == _sources.at8(edgeIndex) ? capacity - _edgeFlow.at8(edgeIndex) : _edgeFlow.at8(edgeIndex);
    }

    public void augmentFlow(NodesEdgesObject nodesEdges, long flow) {
        for (int i = 0; i < nodesEdges.nodeSize(); i++) {
            long edge = nodesEdges.getEdge(i);
            long node = nodesEdges.getNode(i);
            if (node == _sources.at8(edge)) {
                _edgeFlow.set(edge, _edgeFlow.at8(edge) + flow);
            } else {
                _edgeFlow.set(edge, _edgeFlow.at8(edge) - flow);
            }
        }
    }

    public void removeParentEdge(long sourceIndex, long targetIndex) {
        long subtreeSizeTarget = _subtreeSize.at8(targetIndex);
        long previousTargetIndex = _previousNodes.at8(targetIndex);
        long lastTargetIndex = _lastDescendants.at8(targetIndex);
        long nextTargetIndex = _nextDepthFirst.at8(lastTargetIndex);
   
        _parents.set(targetIndex, -1);
        _parentEdges.set(targetIndex, -1);
        _nextDepthFirst.set(previousTargetIndex, nextTargetIndex);
        _previousNodes.set(nextTargetIndex, previousTargetIndex);
        _nextDepthFirst.set(lastTargetIndex, targetIndex);
        _previousNodes.set(targetIndex, lastTargetIndex);
        while (sourceIndex != -1) {
            _subtreeSize.set(sourceIndex, _subtreeSize.at8(sourceIndex) - subtreeSizeTarget);
            if (lastTargetIndex == _lastDescendants.at8(sourceIndex)) {
                _lastDescendants.set(sourceIndex, previousTargetIndex);
            }
            sourceIndex = _parents.at8(sourceIndex);
        }
    }

    public void makeRoot(long nodeIndex) {
        ArrayList<Long> ancestors = new ArrayList<>();
        while (nodeIndex != -1) {
            ancestors.add(nodeIndex);
            nodeIndex = _parents.at8(nodeIndex);
        }
        Collections.reverse(ancestors);
        for (int i = 0; i < ancestors.size() - 1; i++) {
            long sourceIndex = ancestors.get(i);
            long targetIndex = ancestors.get(i + 1);
            long subtreeSizeSource = _subtreeSize.at8(sourceIndex);
            long lastSourceIndex = _lastDescendants.at8(sourceIndex);
            long prevTargetIndex = _previousNodes.at8(targetIndex);
            long lastTargetIndex = _lastDescendants.at8(targetIndex);
            long nextTargetIndex = _nextDepthFirst.at8(lastTargetIndex);

            _parents.set(sourceIndex, targetIndex);
            _parents.set(targetIndex, -1);
            _parentEdges.set(sourceIndex, _parentEdges.at8(targetIndex));
            _parentEdges.set(targetIndex, -1);
            _subtreeSize.set(sourceIndex, subtreeSizeSource - _subtreeSize.at8(targetIndex));
            _subtreeSize.set(targetIndex, subtreeSizeSource);

            _nextDepthFirst.set(prevTargetIndex, nextTargetIndex);
            _previousNodes.set(nextTargetIndex, prevTargetIndex);
            _nextDepthFirst.set(lastTargetIndex, targetIndex);
            _previousNodes.set(targetIndex, lastTargetIndex);

            if (lastSourceIndex == lastTargetIndex) {
                _lastDescendants.set(sourceIndex, prevTargetIndex);
                lastSourceIndex = prevTargetIndex;
            }
            _previousNodes.set(sourceIndex, lastTargetIndex);
            _nextDepthFirst.set(lastTargetIndex, sourceIndex);
            _nextDepthFirst.set(lastSourceIndex, targetIndex);
            _previousNodes.set(targetIndex, lastSourceIndex);
            _lastDescendants.set(targetIndex, lastSourceIndex);
        }
    }

    public void addEdge(long edgeIndex, long sourceIndex, long targetIndex) {
        long lastSourceIndex = _lastDescendants.at8(sourceIndex);
        long nextSourceIndex = _nextDepthFirst.at8(lastSourceIndex);
        long subtreeSizeTarget = _subtreeSize.at8(targetIndex);
        long lastTargetIndex = _lastDescendants.at8(targetIndex);

        _parents.set(targetIndex, sourceIndex);
        _parentEdges.set(targetIndex, edgeIndex);

        _nextDepthFirst.set(lastSourceIndex, targetIndex);
        _previousNodes.set(targetIndex, lastSourceIndex);
        _previousNodes.set(nextSourceIndex, lastTargetIndex);
        _nextDepthFirst.set(lastTargetIndex, nextSourceIndex);

        while (sourceIndex != -1) {
            _subtreeSize.set(sourceIndex, _subtreeSize.at8(sourceIndex) + subtreeSizeTarget);
            if (lastSourceIndex == _lastDescendants.at8(sourceIndex)) {
                _lastDescendants.set(sourceIndex, lastTargetIndex);
            }
            sourceIndex = _parents.at8(sourceIndex);
            
        }
    }


    public void updatePotentials(long edgeIndex, long sourceIndex, long targetIndex, long weight) {
        long potential;
        if (targetIndex == _targets.at8(edgeIndex)) {
            potential = _nodePotentials.at8(sourceIndex) - weight - _nodePotentials.at8(targetIndex);
        } else {
            potential = _nodePotentials.at8(sourceIndex) + weight - _nodePotentials.at8(targetIndex);
        }
        _nodePotentials.set(targetIndex, _nodePotentials.at8(targetIndex) + potential);
        long last = _lastDescendants.at8(targetIndex);
        while (targetIndex != last) {
            targetIndex = _nextDepthFirst.at8(targetIndex);
            _nodePotentials.set(targetIndex, _nodePotentials.at8(targetIndex) + potential);
        }
    }
}

class Edge {
    
    private long _edgeIndex;
    private long _sourceIndex;
    private long _targetIndex;

    public Edge(long edgeIndex, long sourceIndex, long targetIndex) {
        this._edgeIndex = edgeIndex;
        this._sourceIndex = sourceIndex;
        this._targetIndex = targetIndex;
    }

    public long get_edgeIndex() {
        return _edgeIndex;
    }

    public long getSourceIndex() {
        return _sourceIndex;
    }

    public long getTargetIndex() {
        return _targetIndex;
    }

    @Override
    public String toString() {
        return _edgeIndex+" "+_sourceIndex+" "+_targetIndex;
    }
}

class NodesEdgesObject {

    private ArrayList<Long> _nodes;
    private ArrayList<Long> _edges;

    public NodesEdgesObject() {
        this._nodes = new ArrayList<>();
        this._edges = new ArrayList<>();
    }

    public void addNode(long node){
        _nodes.add(node);
    }

    public void removeLastNode(){
        _nodes.remove(_nodes.size()-1);
    }

    public long getNode(int index){
        return _nodes.get(index);
    }

    public ArrayList<Long> getNodes() {
        return _nodes;
    }

    public int nodeSize(){
        return _nodes.size();
    }

    public void addEdge(long edge){
        _edges.add(edge);
    }

    public long getEdge(int index){
        return _edges.get(index);
    }

    public ArrayList<Long> getEdges() {
        return _edges;
    }

    public int edgeSize(){
        return _edges.size();
    }

    public int indexOfEdge(long value){
        return _edges.indexOf(value);
    }


    public ArrayList<Long> getReversedNodes() {
        ArrayList<Long> reversed = new ArrayList<>(_nodes);
        Collections.reverse(reversed);
        return reversed;
    }

    public ArrayList<Long> getReversedEdges() {
        ArrayList<Long> reversed = new ArrayList<>(_edges);
        Collections.reverse(reversed);
        return reversed;
    }
    
    public void reverseNodes(){
        Collections.reverse(_nodes);
    }
    
    public  void reverseEdges(){
        Collections.reverse(_edges);
    }

    public void addAllNodes(ArrayList<Long> newNodes){
        _nodes.addAll(newNodes);
    }

    public void addAllEdges(ArrayList<Long> newEdges){
        _edges.addAll(newEdges);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("NEO: nodes: ");
        for (long i: _nodes) {
            sb.append(i+" ");
        }
        sb.append("edges: ");
        for (long i: _edges) {
            sb.append(i+" ");
        }
        sb.deleteCharAt(sb.length()-1);
        return sb.toString();
    }
}
