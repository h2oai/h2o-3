package hex.kmeans;

import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Experimental code. Polynomial implementation - slow performance. Need to be parallelize!
 * Calculate Minimal Cost Flow problem using simplex method with go through spanning tree.
 * Used to solve minimal cluster size constraints in K-means.
 * 
 */
class KMeansSimplexSolver {
    public int _constraintsLength;
    public long _numberOfPoints;
    public long _edgeSize;
    public long _nodeSize;
    public long _resultSize;

    // Input graph to store K-means configuration
    public Vec _demands; // store demand of all nodes (-1 for data points, constraints values for constraints nodes, )
    public Vec _capacities; // store capacities of all edges + edges from all node to leader node
    public Frame _weights; // input data + weight column, calculated distances from all points to all centres + columns to store result of cluster assignments 
    public Vec _additiveWeights; // additive weight vector to store edge weight from constraints nodes to additive and leader nodes
    public double _sumWeights; // calculated sum of all weights to calculate maximal capacity value
    public double _maxAbsDemand; // maximal absolute demand to calculate maximal capacity value
    public boolean _hasWeightsColumn; // weight column existence flag
    public long _numberOfNonZeroWeightPoints; //if weights columns is set, how many rows has non zero weight

    // Spanning tree to calculate min cost flow
    public SpanningTree tree;

    /**
     * Construct K-means simplex solver.
     * @param constrains
     * @param weights
     * @param sumDistances
     * @param hasWeights
     */
    public KMeansSimplexSolver(int[] constrains, Frame weights, double sumDistances, boolean hasWeights, long numberOfNonZeroWeightPoints) {
        this._numberOfPoints = weights.numRows();
        this._nodeSize = this._numberOfPoints + constrains.length + 1;
        this._edgeSize = _numberOfPoints * constrains.length + constrains.length;
        this._constraintsLength = constrains.length;

        this._demands = Vec.makeCon(0, _nodeSize, Vec.T_NUM);
        this._capacities = Vec.makeCon(0, _edgeSize + _nodeSize, Vec.T_NUM);
        this._resultSize = this._numberOfPoints * _constraintsLength;
        this._hasWeightsColumn = hasWeights;
        this._numberOfNonZeroWeightPoints = numberOfNonZeroWeightPoints;
        
        this._weights = weights;
        this._additiveWeights = Vec.makeCon(0, _constraintsLength, Vec.T_NUM);
        this._sumWeights = sumDistances;

        long constraintsSum = 0;
        _maxAbsDemand = Double.MIN_VALUE;
        for (long i = 0; i < _nodeSize; i++) {
            if (i < _numberOfPoints) {
                _demands.set(i, -1);
            } else {
                long tmpDemand;
                if (i < _nodeSize - 1) {
                    tmpDemand = constrains[(int)(i - _numberOfPoints)];
                    constraintsSum += constrains[(int)(i - _numberOfPoints)];
                } else {
                    tmpDemand = _numberOfNonZeroWeightPoints - constraintsSum;
                }
                _demands.set(i, tmpDemand);
                if (Math.abs(tmpDemand) > _maxAbsDemand) {
                    _maxAbsDemand = Math.abs(tmpDemand);
                }
            }
        }
        
        int edgeIndexStart = _weights.numCols() - 3 - _constraintsLength;
        long edgeIndex = 0; 
        for (long i = 0; i < _weights.numRows(); i++) {
            for(int j=0; j < _constraintsLength; j++){
                _weights.vec(edgeIndexStart + j).set(i, edgeIndex++);
            }
        }
        
        // Initialize graph and spanning tree.
        // always start with infinity _capacities
        for (long i = 0; i < _edgeSize; i++) {
            _capacities.set(i, Long.MAX_VALUE);
        }

        // find maximum value for capacity
        double maxCapacity = 3 * (_sumWeights > _maxAbsDemand ? _sumWeights : _maxAbsDemand);

        // fill max capacity from the leader node to all others _nodes
        for (long i = 0; i < _nodeSize; i++) {
            _capacities.set(i + _edgeSize, maxCapacity);
        }

        this.tree = new SpanningTree(_nodeSize, _edgeSize, _constraintsLength);
        tree.init(_numberOfPoints, maxCapacity, _demands);
    }

    /**
     * Get weight base on edge index from weights data or from additive weights.
     * @param edgeIndex
     * @return
     */
    public double getWeight(long edgeIndex) {
        long numberOfFrameWeights = this._numberOfPoints * this._constraintsLength;
        if (edgeIndex < numberOfFrameWeights) {
            long i = Math.round(edgeIndex / _constraintsLength);
            int j = _weights.numCols() - 2 * _constraintsLength - 3 + (int)(edgeIndex % _constraintsLength);        
            return _weights.vec(j).at(i);
        }
        return _additiveWeights.at(edgeIndex - numberOfFrameWeights);
    }

    /**
     * Get weight base on edge index from weights data or from additive weights.
     * @param edgeIndex
     * @return
     */
    public boolean isNonZeroWeight(long edgeIndex) {
        if(_hasWeightsColumn) {
            long numberOfFrameWeights = this._numberOfPoints * this._constraintsLength;
            if (edgeIndex < numberOfFrameWeights) {
                long i = Math.round(edgeIndex / _constraintsLength);
                int j = _weights.numCols() - 1 - 2 * _constraintsLength - 3;
                return _weights.vec(j).at8(i) == 1;
            }
        }
        return true;
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

    public long findMinimalReducedWeight() {
        FindMinimalWeightTask t = new FindMinimalWeightTask(tree, _hasWeightsColumn, _constraintsLength).doAll(_weights);
        double minimalWeight = t.minimalWeight;
        long minimalIndex = t.minimalIndex;
        long additiveEdgesIndexStart = _weights.vec(0).length() * _constraintsLength;
        // Iterate over number of constraints, it is size K, MR task is not optimal here
        for(long i = additiveEdgesIndexStart; i < _edgeSize; i++){
            double tmpWeight = tree.reduceWeight(i, getWeight(i));
            boolean countValue = !_hasWeightsColumn || isNonZeroWeight(i);
            if (countValue && tmpWeight < minimalWeight) {
                minimalWeight = tmpWeight;
                minimalIndex = i;
            }
        }
        return minimalIndex;
    }

    /**
     * Find next entering edge to find cycle.
     * @return index of the edge
     */
    public Edge findNextEnteringEdge() {
        if(checkIfContinue()) {
            long minimalIndex = findMinimalReducedWeight();
            if (tree._edgeFlow.at8(minimalIndex) == 0) {
                return new Edge(minimalIndex, tree._sources.at8(minimalIndex), tree._targets.at8(minimalIndex));
            } else {
                return new Edge(minimalIndex, tree._targets.at8(minimalIndex), tree._sources.at8(minimalIndex));
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
        double minResidualCapacity = Double.MAX_VALUE;
        int minIndex = -1;
        for (int i = 0; i < cycle.edgeSize(); i++) {
            double tmpResidualCapacity = tree.getResidualCapacity(cycle.getEdge(i), cycle.getNode(i), _capacities.at(cycle.getEdge(i)));
            boolean countValue = !_hasWeightsColumn || isNonZeroWeight(cycle.getEdge(i));
            if (countValue && tmpResidualCapacity < minResidualCapacity) {
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
            long enteringEdgeIndex = edge.getEdgeIndex();
            long enteringEdgeSourceIndex = edge.getSourceIndex();
            long enteringEdgeTargetIndex = edge.getTargetIndex();
            NodesEdgesObject cycle = getCycle(enteringEdgeIndex, enteringEdgeSourceIndex, enteringEdgeTargetIndex);
            Edge leavingEdge = getLeavingEdge(cycle);
            long leavingEdgeIndex = leavingEdge.getEdgeIndex();
            long leavingEdgeSourceIndex = leavingEdge.getSourceIndex();
            long leavingEdgeTargetIndex = leavingEdge.getTargetIndex();
            double residualCap = tree.getResidualCapacity(leavingEdgeIndex, leavingEdgeSourceIndex, _capacities.at(leavingEdgeIndex));
            if(residualCap != 0) {
                tree.augmentFlow(cycle, residualCap);
            }
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
        assert !tree.isInfeasible(): "Spanning tree to calculate K-means cluster assignments is not infeasible.";
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
        int dataStopLength = _weights.numCols() - (_hasWeightsColumn ? 1 : 0) - 2 * _constraintsLength - 3;

        int[] numberOfPointsInCluster = new int[_constraintsLength];
        for(int i = 0; i<_constraintsLength; i++){
            numberOfPointsInCluster[i] = 0;
        }

        for (long i = 0; i < _weights.numRows(); i++) {
            if(!_hasWeightsColumn || _weights.vec(dataStopLength).at8(i) == 1) {
                for (int j = 0; j < _constraintsLength; j++) {
                    //long edgeIndex = i + j * _weights.numRows();
                    long edgeIndex = i * _constraintsLength + j;
                    if (tree._edgeFlow.at8(edgeIndex) == 1) {
                        // old assignment
                        _weights.vec(oldAssignmentIndex).set(i, _weights.vec(newAssignmentIndex).at(i));
                        // new assignment
                        _weights.vec(newAssignmentIndex).set(i, j);
                        // distances
                        _weights.vec(distanceAssignmentIndex).set(i, _weights.vec(dataStopLength + j + (_hasWeightsColumn ? 1 : 0)).at(i));
                        numberOfPointsInCluster[j]++;
                        break;
                    }
                }
            }
        }
        checkConstraintsCondition(numberOfPointsInCluster);
        //remove distances columns
        for(int i = 0; i < 2 * _constraintsLength; i++) {
            _weights.remove(dataStopLength+(_hasWeightsColumn ? 1 : 0));
        }
        return _weights;
    }
}

/**
 * Experimental
 * Class to store calculation of flow in minimal cost flow problem.
 */
class SpanningTree extends Iced<SpanningTree> {

    public long _nodeSize;
    public long _edgeSize;
    public long _secondLayerSize;


    public Vec _sources;   //    edge size + node size
    public Vec _targets;   //    edge size + node size

    public Vec _edgeFlow;          //         edge size + node size, integer
    public Vec _nodePotentials;    //         node size, long
    public Vec _parents;           //         node size + 1, integer
    public Vec _parentEdges;       //         node size + 1, integer
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
        this._parentEdges =  Vec.makeCon(0, nodeSize+1, Vec.T_NUM);
        this._subtreeSize =  Vec.makeCon(1, nodeSize+1, Vec.T_NUM);
        this._nextDepthFirst =  Vec.makeCon(0, nodeSize+1, Vec.T_NUM);
        this._previousNodes =  Vec.makeCon(0, nodeSize+1, Vec.T_NUM);
        this._lastDescendants =  Vec.makeCon(0, nodeSize+1, Vec.T_NUM);
    }

    public void init(long numberOfPoints, double maxCapacity, Vec demands){
        for (long i = 0; i < _nodeSize; i++) {
            if (i < numberOfPoints) {
                for (int j = 0; j < _secondLayerSize; j++) {
                    _sources.set(i * _secondLayerSize + j, i);
                    _targets.set(i * _secondLayerSize + j, numberOfPoints + j);
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
            if (demand >= 0) {
                _sources.set(_edgeSize + i, _nodeSize);
                _targets.set(_edgeSize + i, i);
            } else {
                _sources.set(_edgeSize + i, i);
                _targets.set(_edgeSize + i, _nodeSize);
            }
            if (i < _nodeSize - 1) {
                _nextDepthFirst.set(i, i + 1);
            }
            _edgeFlow.set(_edgeSize +i, Math.abs(demand));
            _nodePotentials.set(i, demand < 0 ? maxCapacity : -maxCapacity);
            _parents.set(i, _nodeSize);
            _parentEdges.set(i, i + _edgeSize);
            _previousNodes.set(i, i - 1);
            _lastDescendants.set(i, i);
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

    public double reduceWeight(long edgeIndex, double weight) {
        double newWeight = weight - _nodePotentials.at(_sources.at8(edgeIndex)) + _nodePotentials.at(_targets.at8(edgeIndex));
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

    public double getResidualCapacity(long edgeIndex, long nodeIndex, double capacity) {
        return nodeIndex == _sources.at8(edgeIndex) ? capacity - _edgeFlow.at(edgeIndex) : _edgeFlow.at(edgeIndex);
    }

    public void augmentFlow(NodesEdgesObject nodesEdges, double flow) {
        for (int i = 0; i < nodesEdges.edgeSize(); i++) {
            long edge = nodesEdges.getEdge(i);
            long node = nodesEdges.getNode(i);
            if (node == _sources.at8(edge)) {
                _edgeFlow.set(edge, _edgeFlow.at(edge) + flow);
            } else {
                _edgeFlow.set(edge, _edgeFlow.at(edge) - flow);
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


    public void updatePotentials(long edgeIndex, long sourceIndex, long targetIndex, double weight) {
        double potential;
        if (targetIndex == _targets.at8(edgeIndex)) {
            potential = _nodePotentials.at(sourceIndex) - weight - _nodePotentials.at(targetIndex);
        } else {
            potential = _nodePotentials.at(sourceIndex) + weight - _nodePotentials.at(targetIndex);
        }
        _nodePotentials.set(targetIndex, _nodePotentials.at(targetIndex) + potential);
        long last = _lastDescendants.at8(targetIndex);
        while (targetIndex != last) {
            targetIndex = _nextDepthFirst.at8(targetIndex);
            _nodePotentials.set(targetIndex, _nodePotentials.at(targetIndex) + potential);
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

    public long getEdgeIndex() {
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


/**
 * Map Reduce task to find minimal reduced weight (distance).
 */
class FindMinimalWeightTask extends MRTask<FindMinimalWeightTask> {
    // IN
    SpanningTree _tree;
    boolean _hasWeightsColumn;
    int _constraintsLength;

    //OUT
    double minimalWeight = Double.MAX_VALUE;
    long minimalIndex = -1;

    FindMinimalWeightTask(SpanningTree tree, boolean hasWeightsColumn, int constraintsLength) {
        _tree = tree;
        _hasWeightsColumn = hasWeightsColumn;
        _constraintsLength = constraintsLength;
    }

    @Override
    public void map(Chunk[] cs) {
        int startDistancesIndex = cs.length - 2 * _constraintsLength - 3;
        int startEdgeIndex = cs.length - 3 - _constraintsLength;
        for (int i = 0; i < cs[0]._len; i++) {
            for (int j = 0; j < _constraintsLength; j++) {
                double weight = cs[startDistancesIndex + j].atd(i);
                long edgeIndex = cs[startEdgeIndex + j].at8(i);
                double tmpWeight = _tree.reduceWeight(edgeIndex, weight);
                boolean countValue = !_hasWeightsColumn || cs[startDistancesIndex-1].at8(i) == 1;
                if (countValue && tmpWeight < minimalWeight) {
                    minimalWeight = tmpWeight;
                    minimalIndex = edgeIndex;
                }
            }
        }
    }

    @Override
    public void reduce(FindMinimalWeightTask mrt) {
        if (mrt.minimalWeight < minimalWeight) {
            minimalIndex = mrt.minimalIndex;
            minimalWeight = mrt.minimalWeight;
        }
    }
}
