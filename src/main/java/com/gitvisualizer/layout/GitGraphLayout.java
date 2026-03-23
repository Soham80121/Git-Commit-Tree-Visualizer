package com.gitvisualizer.layout;

import com.gitvisualizer.model.GraphNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Git-style commit graph layout.
 *
 * Layout approach (similar to "git log --graph"):
 * - Traverse commits in newest->oldest topological order.
 * - Maintain "activeLanes" (columns) representing current branch pointers.
 * - For each commit, place it in the lane where it currently appears.
 * - Move lane pointers to the commit's parents:
 *   - First parent stays in the same lane
 *   - Additional parents are inserted in new lanes to the right
 *   - Duplicate lane pointers are collapsed
 *
 * This class is pure layout logic (no JavaFX).
 */
public class GitGraphLayout {

    public LayoutResult layout(List<GraphNode> nodes) {
        if (nodes == null) {
            return new LayoutResult(List.of(), Map.of(), 0);
        }

        List<GraphNode> nodeList = new ArrayList<>(nodes);
        if (nodeList.isEmpty()) {
            return new LayoutResult(List.of(), Map.of(), 0);
        }

        // 1) Topological order: child -> parents (commit comes before its parents).
        List<GraphNode> ordered = topologicalNewestToOldest(nodeList);

        // 2) Lane assignment using active lane pointers.
        LaneState laneState = new LaneState();
        int maxLane = 0;

        for (GraphNode commit : ordered) {
            int laneIndex = laneState.activeLanes.indexOf(commit);
            if (laneIndex < 0) {
                laneIndex = laneState.activeLanes.size();
                laneState.activeLanes.add(commit);
            }

            laneState.commitToLane.put(commit, laneIndex);
            maxLane = Math.max(maxLane, laneIndex);

            List<GraphNode> parents = distinctParentsInOrder(commit);
            if (parents.isEmpty()) {
                laneState.activeLanes.remove(laneIndex);
                continue;
            }

            // First parent continues in the same lane.
            laneState.activeLanes.set(laneIndex, parents.get(0));

            // Additional parents open new lanes to the right.
            for (int i = 1; i < parents.size(); i++) {
                laneState.activeLanes.add(laneIndex + i, parents.get(i));
            }

            // Collapse duplicate lane pointers (prevents redundant lanes after merges).
            laneState.collapseDuplicateLanes();
        }

        // Store lane + row back onto nodes (so UI can read directly).
        Map<GraphNode, Integer> laneByNode = laneState.commitToLane;
        for (int row = 0; row < ordered.size(); row++) {
            GraphNode n = ordered.get(row);
            n.row = row;
            n.lane = laneByNode.getOrDefault(n, 0);
        }

        return new LayoutResult(ordered, laneByNode, maxLane + 1);
    }

    private static List<GraphNode> topologicalNewestToOldest(List<GraphNode> nodes) {
        // Kahn's algorithm where edges are: commit -> its parents.
        // Nodes with indegree 0 are the "tips" (newest) in this direction.
        Map<GraphNode, Integer> indegree = new HashMap<>();
        for (GraphNode n : nodes) {
            indegree.put(n, 0);
        }

        for (GraphNode child : nodes) {
            for (GraphNode parent : distinctParentsInOrder(child)) {
                if (!indegree.containsKey(parent)) continue;
                indegree.put(parent, indegree.get(parent) + 1);
            }
        }

        PriorityQueue<GraphNode> pq = new PriorityQueue<>(new NewestFirstComparator());
        for (Map.Entry<GraphNode, Integer> e : indegree.entrySet()) {
            if (e.getValue() == 0) pq.add(e.getKey());
        }

        List<GraphNode> ordered = new ArrayList<>(nodes.size());

        while (!pq.isEmpty()) {
            GraphNode node = pq.poll();
            ordered.add(node);

            for (GraphNode parent : distinctParentsInOrder(node)) {
                if (!indegree.containsKey(parent)) continue;
                int d = indegree.get(parent) - 1;
                indegree.put(parent, d);
                if (d == 0) {
                    pq.add(parent);
                }
            }
        }

        // Defensive: if something goes wrong, keep the app usable.
        if (ordered.size() != nodes.size()) {
            Set<GraphNode> inOrder = new HashSet<>(ordered);
            List<GraphNode> remaining = new ArrayList<>();
            for (GraphNode n : nodes) {
                if (!inOrder.contains(n)) remaining.add(n);
            }
            remaining.sort(new NewestFirstComparator());
            ordered.addAll(remaining);
        }

        return ordered;
    }

    private static List<GraphNode> distinctParentsInOrder(GraphNode node) {
        if (node == null || node.parents == null || node.parents.isEmpty()) return List.of();
        // Preserve the order from the model, but de-duplicate by reference/hash.
        List<GraphNode> out = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>();
        for (GraphNode p : node.parents) {
            if (p == null || p.commit == null || p.commit.hash == null) {
                // Keep null-hash parents out to avoid weird layout.
                continue;
            }
            if (seenHashes.add(p.commit.hash)) {
                out.add(p);
            }
        }
        return out;
    }

    private static final class NewestFirstComparator implements Comparator<GraphNode> {
        @Override
        public int compare(GraphNode a, GraphNode b) {
            long ta = (a == null || a.commit == null || a.commit.timestamp == null) ? 0 : a.commit.timestamp.getTime();
            long tb = (b == null || b.commit == null || b.commit.timestamp == null) ? 0 : b.commit.timestamp.getTime();
            int byTime = Long.compare(tb, ta); // descending
            if (byTime != 0) return byTime;

            String ha = (a == null || a.commit == null || a.commit.hash == null) ? "" : a.commit.hash;
            String hb = (b == null || b.commit == null || b.commit.hash == null) ? "" : b.commit.hash;
            return ha.compareTo(hb);
        }
    }

    private static final class LaneState {
        private final List<GraphNode> activeLanes = new ArrayList<>();
        private final Map<GraphNode, Integer> commitToLane = new HashMap<>();

        private void collapseDuplicateLanes() {
            // Keep first occurrence order; remove duplicates.
            Set<GraphNode> seen = new HashSet<>();
            for (int i = 0; i < activeLanes.size(); ) {
                GraphNode laneHead = activeLanes.get(i);
                if (!seen.add(laneHead)) {
                    activeLanes.remove(i);
                } else {
                    i++;
                }
            }
        }
    }

    public static final class LayoutResult {
        public final List<GraphNode> ordered;
        public final Map<GraphNode, Integer> laneByNode;
        public final int laneCount;

        public LayoutResult(List<GraphNode> ordered, Map<GraphNode, Integer> laneByNode, int laneCount) {
            this.ordered = ordered;
            this.laneByNode = laneByNode;
            this.laneCount = laneCount;
        }
    }
}

