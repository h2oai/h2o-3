package hex.kmeans;

import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Arrays;

public class KMeansSimplexSolverLongVec {
    // input graph
    public ArrayList<Integer> nodes;
    public ArrayList<Integer> edges;
    public ArrayList<Integer> demands;   //    node size
    public ArrayList<Integer> sources;   //    edge size + node size
    public ArrayList<Integer> targets;   //    edge size + node size
    public ArrayList<Long> capacities;  //     edge size + node size
    public Frame weights;
    public ArrayList<Long> additiveWeights; //  node size
    public long sumWeights;
    public long maxAbsDemand;
    public boolean hasWeights;
    public int precision;


    public int resultSize;
    public int constraintsLength;
    public int edgeSize;
    public int nodeSize;
    public int numberOfPoints;

    // working spanning tree
    public ArrayList<Integer> edgeFlow;       //         edge size + node size
    public ArrayList<Long> nodePotentials;    //         node size
    public ArrayList<Integer> parents;        //         node size + 1
    public ArrayList<Integer> parentEdges;    //         edge size 
    public ArrayList<Integer> subtreeSize;    //         node size + 1
    public ArrayList<Integer> nextDepthFirst; //         node size + 1
    public ArrayList<Integer> previousNodes;  //         node size + 1
    public ArrayList<Integer> lastDescendats; //         node size + 1

    int numberOfConsecutiveBlocks = 0;
    int firstEdgeInBlock = 0;
    int nextBlockOfEdges;

    public KMeansSimplexSolverLongVec(int[] constrains, Frame weights, long sumWeights, boolean hasWeights, int precision) {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.demands = new ArrayList<>();
        this.sources = new ArrayList<>();
        this.targets = new ArrayList<>();
        this.capacities = new ArrayList<>();
        this.numberOfPoints = (int) weights.numRows();
        this.resultSize = this.numberOfPoints * constrains.length;
        this.constraintsLength = constrains.length;
        this.hasWeights = hasWeights;
        this.precision = precision;

        this.nodeSize = this.numberOfPoints + constrains.length + 1;
        int constrainsSum = 0;
        maxAbsDemand = Long.MIN_VALUE;
        for (int i = 0; i < nodeSize; i++) {
            nodes.add(i);
            if (i < numberOfPoints) {
                demands.add(-1);
                for (int j = 0; j < constrains.length; j++) {
                    sources.add(i);
                    targets.add(numberOfPoints + j);
                }
            } else {
                int tmpDemand;
                if (i < nodeSize - 1) {
                    sources.add(i);
                    targets.add(nodeSize - 1);
                    tmpDemand = constrains[i - numberOfPoints];
                    demands.add(tmpDemand);
                    constrainsSum += constrains[i - numberOfPoints];
                } else {
                    tmpDemand = numberOfPoints - constrainsSum;
                    demands.add(tmpDemand);
                }
                if (tmpDemand > Math.abs(maxAbsDemand)) {
                    maxAbsDemand = Math.abs(tmpDemand);
                }
            }

        }
        int edgeSize = numberOfPoints * constrains.length + constrains.length;
        for (int i = 0; i < edgeSize; i++) {
            edges.add(i);
        }

        // edit _weights depends on _precision?
        this.weights = weights;
        this.additiveWeights = new ArrayList<>();
        this.sumWeights = sumWeights;
        this.edgeSize = edges.size();

        // spanning tree
        this.edgeFlow = new ArrayList<>();
        this.nodePotentials = new ArrayList<>();
        this.parents = new ArrayList<>();
        this.parentEdges = new ArrayList<>();
        this.subtreeSize = new ArrayList<>();
        this.nextDepthFirst = new ArrayList<>();
        this.previousNodes = new ArrayList<>();
        this.lastDescendats = new ArrayList<>();
    }

    public void checkInfeasibility() {
        for(int i=edgeFlow.size() - constraintsLength + 1; i< edgeFlow.size(); i++) {
            assert edgeFlow.get(i) == 0: "Edge flow at index "+i+" should be 0 but is:"+ edgeFlow.get(i);
        }
    }

    public void initialize() {
        // add root node with index = _nodeSize
        for (int i = 0; i < nodeSize; i++) {
            Integer d = demands.get(i);
            if (d > 0) {
                sources.add(nodeSize);
                targets.add(i);
            } else {
                sources.add(i);
                targets.add(nodeSize);
            }
        }
        // always start with infinity _capacities
        for (int i = 0; i < edges.size(); i++) {
            capacities.add(Long.MAX_VALUE);
        }

        // find maximum value for capacity
        long maxCapacity = 3 * (sumWeights > maxAbsDemand ? sumWeights : maxAbsDemand);

        // fill zeros from constraint node to the leader node
        for (int i = 0; i < constraintsLength; i++) {
            additiveWeights.add(0L);
        }

        // fill max capacity from the leader node to all others _nodes
        for (int i = 0; i < nodeSize; i++) {
            additiveWeights.add(maxCapacity);
            capacities.add(maxCapacity);
        }

        //init spanning tree
        for (int i = 0; i < edgeSize; i++) {
            edgeFlow.add(0);
        }
        for (int i = 0; i < nodeSize; i++) {
            Integer demand = demands.get(i);
            edgeFlow.add(Math.abs(demand));
            nodePotentials.add(demand <= 0 ? maxCapacity : -maxCapacity);
            parents.add(nodeSize);
            parentEdges.add(i + edgeSize);
            subtreeSize.add(1);
            previousNodes.add(i - 1);
            lastDescendats.add(i);
            if (i < nodeSize - 1) {
                nextDepthFirst.add(i + 1);
            }
        }
        parents.add(null);
        subtreeSize.add(nodeSize + 1);
        nextDepthFirst.add(nodeSize);
        nextDepthFirst.add(0);
        previousNodes.set(0, nodeSize);
        previousNodes.add(nodeSize - 1);
        lastDescendats.add(nodeSize - 1);
    }

    public long getWeight(Integer edgeIndex) {
        int numberOfFrameWeights = this.numberOfPoints * this.constraintsLength;
        if (edgeIndex < numberOfFrameWeights) {
            int i = edgeIndex % numberOfPoints;
            int j = weights.numCols() - constraintsLength - 3 + (int) Math.floor(edgeIndex / numberOfPoints);
            return (long) (weights.vec(j).at(i)*precision);
        }
        return additiveWeights.get(edgeIndex - numberOfFrameWeights);
    }

    public Long reduceWeight(Integer edgeIndex) {
        Long weight = getWeight(edgeIndex) - nodePotentials.get(sources.get(edgeIndex)) + nodePotentials.get(targets.get(edgeIndex));
        return edgeFlow.get(edgeIndex).equals(0) ? weight : -weight;
    }

    public int findMinimalReducedWeight(int from, int to) {
        Long minimalWeight = Long.MAX_VALUE;
        int minimalIndex = -1;
        for (int i = from; i < to; i++) {
            Long tmpWeight = reduceWeight(i);
            if (tmpWeight < minimalWeight) {
                minimalWeight = tmpWeight;
                minimalIndex = i;
            }
        }
        return minimalIndex;
    }


    private boolean checkIfContinue() {
        for (int i = edgeFlow.size() - 1; i > edgeFlow.size() - constraintsLength - 2; i--) {
            if (!edgeFlow.get(i).equals(0)) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<Integer> nextEnteringEdge() {
        if (checkIfContinue()) {
            int blockSize = (int) Math.ceil(Math.sqrt(edgeSize));
            int numberOfBlocks = Math.floorDiv(edgeSize + blockSize - 1, blockSize);
            if (numberOfConsecutiveBlocks < numberOfBlocks) {
                nextBlockOfEdges = firstEdgeInBlock + blockSize;
                int minimalIndex;
                if (nextBlockOfEdges <= edgeSize) {
                    minimalIndex = findMinimalReducedWeight(firstEdgeInBlock, nextBlockOfEdges);
                } else {
                    nextBlockOfEdges -= edgeSize;
                    int fIndex = findMinimalReducedWeight(firstEdgeInBlock, edgeSize);
                    int sIndex = findMinimalReducedWeight(0, nextBlockOfEdges);
                    if (fIndex == -1) {
                        minimalIndex = sIndex;
                    } else if (sIndex == -1) {
                        minimalIndex = fIndex;
                    } else {
                        minimalIndex = reduceWeight(fIndex) < reduceWeight(sIndex) ? fIndex : sIndex;
                    }
                    assert minimalIndex != -1;
                }
                firstEdgeInBlock = nextBlockOfEdges;
                Long minimalWeight = reduceWeight(minimalIndex);
                if (minimalWeight >= 0) {
                    numberOfConsecutiveBlocks += 1;
                    return nextEnteringEdge();
                } else {
                    numberOfConsecutiveBlocks = 0;
                    if (edgeFlow.get(minimalIndex) == 0) {
                        return new ArrayList<>(Arrays.asList(minimalIndex, sources.get(minimalIndex), targets.get(minimalIndex)));
                    } else {
                        return new ArrayList<>(Arrays.asList(minimalIndex, targets.get(minimalIndex), sources.get(minimalIndex)));
                    }
                }
            } else {
                return null;
            }
        }
        return null;
    }

    public Integer findAncestor(Integer p, Integer q) {
        Integer treeSizeP = subtreeSize.get(p);
        Integer treeSizeQ = subtreeSize.get(q);
        while (true) {
            while (treeSizeP < treeSizeQ) {
                p = parents.get(p);
                treeSizeP = subtreeSize.get(p);
            }
            while (treeSizeP > treeSizeQ) {
                q = parents.get(q);
                treeSizeQ = subtreeSize.get(q);
            }
            if (treeSizeP.equals(treeSizeQ)) {
                if (!p.equals(q)) {
                    p = parents.get(p);
                    treeSizeP = subtreeSize.get(p);
                    q = parents.get(q);
                    treeSizeQ = subtreeSize.get(q);
                } else {
                    return p;
                }
            }
        }
    }

    public ArrayList<Integer>[] getPath(Integer p, Integer w) {
        ArrayList<Integer> nodes = new ArrayList<>();
        nodes.add(p);
        ArrayList<Integer> edges = new ArrayList<>();
        while (!p.equals(w)) {
            edges.add(parentEdges.get(p));
            p = parents.get(p);
            nodes.add(p);
        }
        return new ArrayList[]{nodes, edges};
    }


    public ArrayList<Integer> reverseArrayList(ArrayList<Integer> list) {
        ArrayList<Integer> reversed = new ArrayList<Integer>();
        for (int i = list.size() - 1; i >= 0; i--) {
            reversed.add(list.get(i));
        }
        return reversed;
    }

    public ArrayList<Integer>[] getCycle(Integer i, Integer p, Integer q) {
        Integer w = findAncestor(p, q);
        ArrayList<Integer>[] resultPath = getPath(p, w);
        ArrayList<Integer> nodes = reverseArrayList(resultPath[0]);
        ArrayList<Integer> edges = reverseArrayList(resultPath[1]);
        if (edges.size() != 1 || !edges.get(0).equals(i)) {
            edges.add(i);
        }
        ArrayList<Integer>[] resultPathBack = getPath(q, w);
        ArrayList<Integer> nodesBack = resultPathBack[0];
        ArrayList<Integer> edgesBack = resultPathBack[1];
        nodesBack.remove(nodesBack.size() - 1);
        nodes.addAll(nodesBack);
        edges.addAll(edgesBack);
        return new ArrayList[]{nodes, edges};
    }

    public long getResidualCapacity(Integer i, Integer p) {
        boolean bo = sources.get(i).equals(p);
        long a = capacities.get(i) - edgeFlow.get(i);
        long b = edgeFlow.get(i);
        return sources.get(i).equals(p) ? capacities.get(i) - edgeFlow.get(i) : edgeFlow.get(i);
    }

    public ArrayList<Integer> getLeavingEdge(ArrayList<Integer> nodes, ArrayList<Integer> edges) {
        ArrayList<Integer> reversedNodes = reverseArrayList(nodes);
        ArrayList<Integer> reversedEdges = reverseArrayList(edges);
        Long minResidualCapacity = Long.MAX_VALUE;
        int minIndex = -1;
        for (int i = 0; i < nodes.size(); i++) {
            Long tmpResidualCapacity = getResidualCapacity(reversedEdges.get(i), reversedNodes.get(i));
            if (tmpResidualCapacity < minResidualCapacity) {
                minResidualCapacity = tmpResidualCapacity;
                minIndex = i;
            }
        }
        assert minIndex != -1;
        ArrayList<Integer> result = new ArrayList<>();
        Integer nodeIndex = reversedNodes.get(minIndex);
        Integer edgeIndex = reversedEdges.get(minIndex);
        result.add(edgeIndex);
        result.add(nodeIndex);
        result.add(sources.get(edgeIndex).equals(nodeIndex) ? targets.get(edgeIndex) : sources.get(edgeIndex));
        return result;
    }

    public void augmentedFlow(ArrayList<Integer> nodes, ArrayList<Integer> edges, int flow) {
        for (int i = 0; i < nodes.size(); i++) {
            Integer edge = edges.get(i);
            Integer node = nodes.get(i);
            if (sources.get(edge).equals(node)) {
                edgeFlow.set(edge, edgeFlow.get(edge) + flow);
            } else {
                edgeFlow.set(edge, edgeFlow.get(edge) - flow);
            }
        }
    }

    public void removeParentEdge(Integer s, Integer t) {
        Integer sizeT = subtreeSize.get(t);
        Integer previousT = previousNodes.get(t);
        Integer lastT = lastDescendats.get(t);
        Integer nextT = nextDepthFirst.get(lastT);

        System.out.println("remove parent edge "+sizeT+" "+previousT+" "+lastT+" "+nextT);

        parents.set(t, null);
        parentEdges.set(t, null);
        nextDepthFirst.set(previousT, nextT);
        previousNodes.set(nextT, previousT);
        nextDepthFirst.set(lastT, t);
        previousNodes.set(t, lastT);
        Integer tmpS = s;
        while (tmpS != null) {
            System.out.println("remove parent edge while "+tmpS);
            subtreeSize.set(tmpS, subtreeSize.get(tmpS) - sizeT);
            if (lastDescendats.get(tmpS).equals(lastT)) {
                System.out.println("remove parent edge set last "+tmpS);
                lastDescendats.set(tmpS, previousT);
            }
            tmpS = parents.get(tmpS);
        }
    }

    public void makeRoot(Integer q) {
        ArrayList<Integer> ancestors = new ArrayList<>();
        Integer tmpQ = q;
        while (tmpQ != null) {
            ancestors.add(tmpQ);
            tmpQ = parents.get(tmpQ);
        }
        System.out.println("make root "+tmpQ);
        ancestors = reverseArrayList(ancestors);
        for (int i = 0; i < ancestors.size() - 1; i++) {
            Integer p = ancestors.get(i);
            Integer qq = ancestors.get(i + 1);
            Integer sizeP = subtreeSize.get(p);
            Integer lastP = lastDescendats.get(p);
            Integer prevQ = previousNodes.get(qq);
            Integer lastQ = lastDescendats.get(qq);
            Integer nextQ = nextDepthFirst.get(lastQ);

            System.out.println("make root for "+p+" "+qq+" "+sizeP+" "+lastP+" "+prevQ+" "+lastQ+" "+nextQ);
            
            parents.set(p, qq);
            parents.set(qq, null);
            parentEdges.set(p, parentEdges.get(qq));
            parentEdges.set(qq, null);
            subtreeSize.set(p, sizeP - subtreeSize.get(qq));
            subtreeSize.set(qq, sizeP);

            nextDepthFirst.set(prevQ, nextQ);
            previousNodes.set(nextQ, prevQ);
            nextDepthFirst.set(lastQ, qq);
            previousNodes.set(qq, lastQ);

            if (lastP.equals(lastQ)) {
                lastDescendats.set(p, prevQ);
                lastP = prevQ;
                System.out.println("make root if "+lastP);
            }
            previousNodes.set(p, lastQ);
            nextDepthFirst.set(lastQ, p);
            nextDepthFirst.set(lastP, qq);
            previousNodes.set(qq, lastP);
            lastDescendats.set(qq, lastP);
        }
    }

    public void addEdge(Integer i, Integer p, Integer q) {
        Integer lastP = lastDescendats.get(p);
        Integer nextP = nextDepthFirst.get(lastP);
        Integer sizeQ = subtreeSize.get(q);
        Integer lastQ = lastDescendats.get(q);

        System.out.println("add edge "+lastP+" "+nextP+" "+sizeQ+" "+lastQ);

        parents.set(q, p);
        parentEdges.set(q, i);

        nextDepthFirst.set(lastP, q);
        previousNodes.set(q, lastP);
        previousNodes.set(nextP, lastQ);
        nextDepthFirst.set(lastQ, nextP);

        while (p != null) {
            System.out.println("add edge while "+p);
            subtreeSize.set(p, subtreeSize.get(p) + sizeQ);
            if (lastDescendats.get(p).equals(lastP)) {
                lastDescendats.set(p, lastQ);
                System.out.println("add edge while if "+p);
            }
            p = parents.get(p);
        }
    }

    public void updatePotentials(Integer i, Integer p, Integer q) {
        Long potential;
        if (q.equals(targets.get(i))) {
            potential = nodePotentials.get(p) - getWeight(i) - nodePotentials.get(q);
        } else {
            potential = nodePotentials.get(p) + getWeight(i) - nodePotentials.get(q);
        }
        nodePotentials.set(q, nodePotentials.get(q) + potential);
        Integer l = lastDescendats.get(q);
        System.out.println("update potentials "+i+" "+p+" "+q+" "+getWeight(i)+" "+potential+" "+l);
        while (!q.equals(l)) {
            q = nextDepthFirst.get(q);
            nodePotentials.set(q, nodePotentials.get(q) + potential);
            System.out.println("update potentials while "+q);
        }
    }

    public void pivotLoop() {
        ArrayList<Integer> edge = nextEnteringEdge();
        while (edge != null) {
            Integer i = edge.get(0);
            Integer p = edge.get(1);
            Integer q = edge.get(2);
            //System.out.println("i:"+i+" p:"+p+" q:"+q);
            System.out.println("entering: "+i+" "+p+" "+q);
            ArrayList<Integer>[] cycle = getCycle(i, p, q);
            ArrayList<Integer> nodes = cycle[0];
            ArrayList<Integer> edges = cycle[1];
            ArrayList<Integer> leavingEdge = getLeavingEdge(nodes, edges);
            Integer j = leavingEdge.get(0);
            Integer s = leavingEdge.get(1);
            Integer t = leavingEdge.get(2);
            System.out.println("leaving: "+j+" "+s+" "+t);
            augmentedFlow(nodes, edges, (int) getResidualCapacity(j, s));
            if (!i.equals(j)) {
                if (!s.equals(parents.get(t))) {
                    Integer tmpS = s;
                    s = t;
                    t = tmpS;
                }
                if (edges.indexOf(i) > edges.indexOf(j)) {
                    Integer tmpP = p;
                    p = q;
                    q = tmpP;
                }
                removeParentEdge(s, t);
                makeRoot(q);
                addEdge(i, p, q);
                updatePotentials(i, p, q);
            }
            edge = nextEnteringEdge();
        }
    }

    public ArrayList<Integer> calculateFlowCost() {
        initialize();
        pivotLoop();
        checkInfeasibility();
        return new ArrayList<>(edgeFlow.subList(0, resultSize));
    }

    public Frame assignClusters() {
        ArrayList<Integer> clusterAssignments = calculateFlowCost();
        Vec[] vecs = weights.vecs();
        int distanceAssigmnentIndex = vecs.length - 3;
        int oldAssigmnentIndex = vecs.length - 2;
        int newAssigmnentIndex = vecs.length - 1;
        int dataStopLength = vecs.length - (hasWeights ? 1 : 0) - constraintsLength - 3;
        for (int i = 0; i < vecs[0].length(); i++) {
            for (int j = 0; j < constraintsLength; j++) {
                if (clusterAssignments.get(i * constraintsLength + j) == 1) {
                    // old assignment
                    vecs[oldAssigmnentIndex].set(i, vecs[newAssigmnentIndex].at(i));
                    // new assignment
                    vecs[newAssigmnentIndex].set(i, j);
                    // distances
                    vecs[distanceAssigmnentIndex].set(i, vecs[dataStopLength + j].at(i));
                    break;
                }
            }
        }
        return new Frame(vecs);
    }
}
