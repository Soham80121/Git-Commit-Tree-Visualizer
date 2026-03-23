package com.gitvisualizer.model;

import java.util.*;

public class GraphNode {
    public CommitNode commit;
    public List<GraphNode> parents = new ArrayList<>();

    // Layout metadata (set by GitGraphLayout).
    public int lane = -1;
    public int row = -1;

    public double x;
    public double y;

    public GraphNode(CommitNode commit) {
        this.commit = commit;
    }
}
