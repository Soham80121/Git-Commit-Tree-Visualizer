package com.gitvisualizer;

import com.gitvisualizer.service.*;
import com.gitvisualizer.model.*;
import com.gitvisualizer.ui.GraphView;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {

        String repoPath = "YOUR_LOCAL_REPO_PATH"; // change this

        GitService gitService = new GitService();
        List<CommitNode> commits = gitService.getAllCommits(repoPath);

        GraphBuilder builder = new GraphBuilder();
        List<GraphNode> graph = builder.buildGraph(commits);

        GraphView view = new GraphView();
        view.render(graph);

        Scene scene = new Scene(view, 800, 600);

        stage.setTitle("Git Commit Visualizer");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}