package com.gitvisualizer.service;

import com.gitvisualizer.model.*;

import java.util.*;

public class GraphBuilder {

    public List<GraphNode> buildGraph(List<CommitNode> commits) {
        Map<String, GraphNode> map = new HashMap<>();

        // create nodes
        for (CommitNode c : commits) {
            map.put(c.hash, new GraphNode(c));
        }

        // link parents
        for (CommitNode c : commits) {
            GraphNode node = map.get(c.hash);

            for (String parentHash : c.parentHashes) {
                GraphNode parent = map.get(parentHash);
                if (parent != null) {
                    node.parents.add(parent);
                }
            }
        }

        return new ArrayList<>(map.values());
    }
}