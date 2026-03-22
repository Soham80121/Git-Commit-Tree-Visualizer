package com.gitvisualizer.ui;

import com.gitvisualizer.model.GraphNode;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.paint.Color;

import java.util.List;

public class GraphView extends Pane {

    public void render(List<GraphNode> nodes) {

        // simple layout
        int i = 0;
        for (GraphNode node : nodes) {
            node.x = 100 + (i % 5) * 100;
            node.y = 50 + (i * 60);
            i++;
        }

        // draw edges
        for (GraphNode node : nodes) {
            for (GraphNode parent : node.parents) {
                Line line = new Line(
                        node.x, node.y,
                        parent.x, parent.y
                );
                line.setStroke(Color.GRAY);
                getChildren().add(line);
            }
        }

        // draw nodes
        for (GraphNode node : nodes) {
            Circle circle = new Circle(node.x, node.y, 6);
            circle.setFill(Color.DODGERBLUE);

            circle.setOnMouseClicked(e -> {
                System.out.println("Commit: " + node.commit.hash);
                System.out.println("Message: " + node.commit.message);
            });

            getChildren().add(circle);
        }
    }
}