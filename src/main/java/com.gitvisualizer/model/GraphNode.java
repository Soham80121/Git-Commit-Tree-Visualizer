package com.gitvisualizer.model;

import java.util.*;

public class GraphNode {
    public CommitNode commit;
    public List<GraphNode> parents = new ArrayList<>();

    public double x;
    public double y;

    public GraphNode(CommitNode commit) {
        this.commit = commit;
    }
}
