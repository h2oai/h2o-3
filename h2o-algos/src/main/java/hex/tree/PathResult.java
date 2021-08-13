package hex.tree;

class PathResult {
    StringBuilder path;
    int nodeId;

    PathResult(int nodeId) {
        path = new StringBuilder();
        this.nodeId = nodeId;
    }
}
