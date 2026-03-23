package com.gitvisualizer.ui;

import com.gitvisualizer.model.CommitNode;
import com.gitvisualizer.model.GraphNode;
import com.gitvisualizer.layout.GitGraphLayout;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.stage.Popup;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Commit graph UI with:
 * - scrollable infinite canvas (ScrollPane)
 * - zoom around mouse cursor (Scale on graph group)
 * - pan via click+drag (updates ScrollPane scrollbars)
 */
public class GraphView extends BorderPane {
    // Theme.
    private static final String BG = "#0d1117";
    private static final String CARD = "#161b22";
    private static final String BORDER = "#30363d";
    private static final String TEXT_PRIMARY = "#c9d1d9";
    private static final String TEXT_SECONDARY = "#8b949e";

    // Graph geometry.
    private static final double LEFT_PADDING = 28;
    private static final double TOP_PADDING = 22;
    private static final double BOTTOM_PADDING = 80;

    private static final double LANE_GAP_X = 420;
    private static final double CARD_HEIGHT = 88;
    private static final double CARD_WIDTH = 300;
    private static final double CIRCLE_RADIUS = 8.0;
    private static final double CARD_GAP_Y = 18;
    private static final double V_STEP = CARD_HEIGHT + CARD_GAP_Y;

    // Colors for different "lanes" (branch proxy).
    private static final Color[] BRANCH_COLORS = new Color[]{
            Color.web("#3b82f6"), // blue
            Color.web("#8b5cf6"), // purple
            Color.web("#10b981"), // green
            Color.web("#f59e0b"), // amber
            Color.web("#ef4444"), // red
            Color.web("#06b6d4")  // cyan
    };

    // Viewport / interaction state.
    private final ScrollPane graphScroll = new ScrollPane();
    private final Pane graphPane = new Pane();
    private final Group graphGroup = new Group();
    private final javafx.scene.transform.Scale graphScale = new javafx.scene.transform.Scale(1, 1, 0, 0);

    private double currentScale = 1.0;
    private double baseWidth = 1;
    private double baseHeight = 1;

    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 3.0;

    private double panStartSceneX;
    private double panStartSceneY;
    private double panStartScrollXActual;
    private double panStartScrollYActual;
    private boolean panning;

    // Hover interaction.
    private GraphNode hoverNode;

    // Tooltip popup (GitHub-like).
    private final Popup commitTooltip = new Popup();
    private final VBox tooltipBox = new VBox(6);
    private final Label tooltipHash = new Label();
    private final Label tooltipAuthor = new Label();
    private final Label tooltipDate = new Label();
    private final Text tooltipMessage = new Text();

    // Render state.
    private final Map<GraphNode, CommitCardView> nodeViews = new HashMap<>();
    private final List<EdgeView> edgeViews = new ArrayList<>();
    private final Map<GraphNode, Set<GraphNode>> childrenByNode = new HashMap<>();

    public GraphView() {
        setStyle("-fx-background-color: " + BG + ";");
        setPadding(new Insets(12));

        initTooltip();
        initGraphViewport();
        initInteractions();
    }

    private void initTooltip() {
        tooltipBox.setPadding(new Insets(12, 14, 12, 14));
        tooltipBox.setStyle(
                "-fx-background-color: " + CARD + ";" +
                " -fx-border-color: " + BORDER + ";" +
                " -fx-border-width: 1;" +
                " -fx-background-radius: 12;" +
                " -fx-border-radius: 12;"
        );

        tooltipHash.setTextFill(Color.web(TEXT_PRIMARY));
        tooltipHash.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));

        tooltipAuthor.setTextFill(Color.web(TEXT_SECONDARY));
        tooltipAuthor.setFont(Font.font("System", FontWeight.NORMAL, 12));

        tooltipDate.setTextFill(Color.web(TEXT_SECONDARY));
        tooltipDate.setFont(Font.font("System", FontWeight.NORMAL, 12));

        tooltipMessage.setFill(Color.web(TEXT_PRIMARY));
        tooltipMessage.setFont(Font.font("System", 12));
        tooltipMessage.setWrappingWidth(300);

        tooltipBox.getChildren().addAll(tooltipHash, tooltipAuthor, tooltipDate, tooltipMessage);

        commitTooltip.setAutoFix(true);
        commitTooltip.setAutoHide(false);
        commitTooltip.getContent().clear();
        commitTooltip.getContent().add(tooltipBox);
        tooltipBox.setOpacity(0);
    }

    private void initGraphViewport() {
        // GraphPane is the scroll content; graphGroup is the scaled object inside it.
        graphPane.setStyle("-fx-background-color: " + BG + ";");
        graphGroup.getTransforms().add(graphScale);
        graphPane.getChildren().add(graphGroup);

        graphScroll.setContent(graphPane);
        graphScroll.setFitToWidth(true);
        graphScroll.setFitToHeight(true);
        graphScroll.setPannable(false);
        graphScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        setCenter(graphScroll);
    }

    private void initInteractions() {
        graphScroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() == 0) return;
            // Wheel scroll: zoom.
            double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
            zoomAtViewportPoint(e.getSceneX(), e.getSceneY(), factor);
            e.consume();
        });

        graphScroll.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (!(e.getTarget() instanceof javafx.scene.Node)) return;

            // Do not pan when starting a click on a commit view.
            if (isCommitTarget(e.getTarget())) return;

            panning = true;
            panStartSceneX = e.getSceneX();
            panStartSceneY = e.getSceneY();

            panStartScrollXActual = getScrollXActual();
            panStartScrollYActual = getScrollYActual();
        });

        graphScroll.setOnMouseReleased(e -> panning = false);

        graphScroll.setOnMouseDragged(e -> {
            if (!panning) return;

            double dx = e.getSceneX() - panStartSceneX;
            double dy = e.getSceneY() - panStartSceneY;

            double newScrollXActual = panStartScrollXActual - dx;
            double newScrollYActual = panStartScrollYActual - dy;

            setScrollXActual(newScrollXActual);
            setScrollYActual(newScrollYActual);
        });

        graphScroll.setCursor(Cursor.DEFAULT);
    }

    private boolean isCommitTarget(Object target) {
        if (!(target instanceof javafx.scene.Node node)) return false;
        // Walk up so child targets (Circle/Label) are still treated as part of the commit node.
        javafx.scene.Node cur = node;
        while (cur != null) {
            if ("commit-node".equals(cur.getUserData())) return true;
            cur = cur.getParent();
        }
        return false;
    }

    private double getScrollXActual() {
        Bounds vp = graphScroll.getViewportBounds();
        double vpW = vp.getWidth();
        double contentWActual = baseWidth * currentScale;
        double range = Math.max(0, contentWActual - vpW);
        if (range <= 0) return 0;
        return graphScroll.getHvalue() * range;
    }

    private double getScrollYActual() {
        Bounds vp = graphScroll.getViewportBounds();
        double vpH = vp.getHeight();
        double contentHActual = baseHeight * currentScale;
        double range = Math.max(0, contentHActual - vpH);
        if (range <= 0) return 0;
        return graphScroll.getVvalue() * range;
    }

    private void setScrollXActual(double scrollXActual) {
        Bounds vp = graphScroll.getViewportBounds();
        double vpW = vp.getWidth();
        double contentWActual = baseWidth * currentScale;
        double range = Math.max(0, contentWActual - vpW);
        if (range <= 0) {
            graphScroll.setHvalue(0);
            return;
        }
        double clamped = clamp(scrollXActual, 0, range);
        graphScroll.setHvalue(clamped / range);
    }

    private void setScrollYActual(double scrollYActual) {
        Bounds vp = graphScroll.getViewportBounds();
        double vpH = vp.getHeight();
        double contentHActual = baseHeight * currentScale;
        double range = Math.max(0, contentHActual - vpH);
        if (range <= 0) {
            graphScroll.setVvalue(0);
            return;
        }
        double clamped = clamp(scrollYActual, 0, range);
        graphScroll.setVvalue(clamped / range);
    }

    private void zoomAtViewportPoint(double sceneX, double sceneY, double factor) {
        Bounds vp = graphScroll.getViewportBounds();
        if (vp.getWidth() <= 0 || vp.getHeight() <= 0) return;

        double newScale = clamp(currentScale * factor, MIN_SCALE, MAX_SCALE);
        if (Math.abs(newScale - currentScale) < 0.0001) return;

        // Mouse position within viewport in ACTUAL pixels.
        var topLeftScene = graphScroll.localToScene(vp.getMinX(), vp.getMinY());
        PointInScene vpTopLeft = new PointInScene(topLeftScene.getX(), topLeftScene.getY());
        double mouseXActual = sceneX - vpTopLeft.x;
        double mouseYActual = sceneY - vpTopLeft.y;

        // Content offsets in actual pixels.
        double scrollXActual = getScrollXActual();
        double scrollYActual = getScrollYActual();

        // Convert cursor point to logical content coordinates (before scaling).
        double logicalX = (scrollXActual + mouseXActual) / currentScale;
        double logicalY = (scrollYActual + mouseYActual) / currentScale;

        currentScale = newScale;

        // Apply scale to group + update scrollable content size.
        graphScale.setX(currentScale);
        graphScale.setY(currentScale);
        graphPane.setPrefSize(baseWidth * currentScale, baseHeight * currentScale);
        graphPane.setMinSize(baseWidth * currentScale, baseHeight * currentScale);

        double contentWNew = baseWidth * currentScale;
        double contentHNew = baseHeight * currentScale;

        double rangeXNew = Math.max(0, contentWNew - vp.getWidth());
        double rangeYNew = Math.max(0, contentHNew - vp.getHeight());

        double newScrollXActual = logicalX * currentScale - mouseXActual;
        double newScrollYActual = logicalY * currentScale - mouseYActual;

        if (rangeXNew <= 0) {
            graphScroll.setHvalue(0);
        } else {
            graphScroll.setHvalue(clamp(newScrollXActual, 0, rangeXNew) / rangeXNew);
        }
        if (rangeYNew <= 0) {
            graphScroll.setVvalue(0);
        } else {
            graphScroll.setVvalue(clamp(newScrollYActual, 0, rangeYNew) / rangeYNew);
        }
    }

    private record PointInScene(double x, double y) {}

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public void render(List<GraphNode> nodes) {
        // Reset render state.
        graphGroup.getChildren().clear();
        nodeViews.clear();
        edgeViews.clear();
        childrenByNode.clear();
        hoverNode = null;

        if (nodes == null || nodes.isEmpty()) {
            graphScroll.setHvalue(0);
            graphScroll.setVvalue(0);
            applyHighlights(null);
            commitTooltip.hide();
            return;
        }

        computeChildren(nodes);

        // Compute Git-style lanes and row order (pure layout logic).
        GitGraphLayout.LayoutResult layout = new GitGraphLayout().layout(nodes);
        List<GraphNode> ordered = layout.ordered;
        int laneCount = Math.max(1, layout.laneCount);

        // Layout positions. Vertical timeline: top -> bottom.
        double nodeContentWidth = LEFT_PADDING + (laneCount - 1) * LANE_GAP_X + (CARD_WIDTH + 70);
        double viewportW = graphScroll.getViewportBounds().getWidth();
        if (viewportW <= 0) viewportW = graphScroll.getWidth();
        // Render resets scale to 1.0, so compute centering in logical pixels.
        double logicalViewportW = viewportW;

        // Center nodes horizontally when content is narrower than viewport.
        baseWidth = Math.max(900, Math.max(nodeContentWidth, logicalViewportW));
        double originX = LEFT_PADDING + ((baseWidth - nodeContentWidth) / 2.0);
        baseHeight = TOP_PADDING + ordered.size() * V_STEP + BOTTOM_PADDING;

        // Reset zoom.
        currentScale = 1.0;
        graphScale.setX(1.0);
        graphScale.setY(1.0);
        graphPane.setPrefSize(baseWidth, baseHeight);
        graphPane.setMinSize(baseWidth, baseHeight);
        graphScroll.setHvalue(0);
        graphScroll.setVvalue(0);

        // First, set node coordinates (used by edge anchors and card placement).
        for (GraphNode node : ordered) {
            int lane = node.lane;
            int row = node.row;
            double x = originX + lane * LANE_GAP_X;
            double y = TOP_PADDING + row * V_STEP;

            node.x = x;
            node.y = y;
        }

        // Add edges behind cards.
        for (GraphNode child : nodes) {
            if (child.parents == null) continue;
            Set<GraphNode> distinctParents = new HashSet<>(child.parents);
            for (GraphNode parent : distinctParents) {
                if (parent == null) continue;
                EdgeView edge = new EdgeView(child, parent);
                edgeViews.add(edge);
                graphGroup.getChildren().add(edge.curve);
            }
        }

        // Add cards.
        for (GraphNode node : nodes) {
            CommitCardView view = new CommitCardView(node);
            nodeViews.put(node, view);
            graphGroup.getChildren().add(view.root);
        }

        applyHighlights(null);
    }

    private void computeChildren(List<GraphNode> nodes) {
        for (GraphNode n : nodes) {
            childrenByNode.putIfAbsent(n, new HashSet<>());
        }
        for (GraphNode n : nodes) {
            if (n.parents == null) continue;
            for (GraphNode parent : n.parents) {
                if (parent == null) continue;
                if (!childrenByNode.containsKey(parent)) continue;
                childrenByNode.get(parent).add(n);
            }
        }
    }

    private static long commitTime(CommitNode commit) {
        if (commit == null || commit.timestamp == null) return 0L;
        return commit.timestamp.getTime();
    }

    private static String shortHash(String hash) {
        if (hash == null) return "";
        return hash.length() <= 7 ? hash : hash.substring(0, 7);
    }

    private static String formatDateTime(Date d) {
        if (d == null) return "(unknown time)";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    private static String prettyMessage(String message) {
        if (message == null || message.isBlank()) return "(no message)";
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String previewMessage(String message) {
        String normalized = prettyMessage(message);
        if (normalized.length() <= 64) return normalized;
        return normalized.substring(0, 61) + "...";
    }

    private Set<GraphNode> distinctParents(GraphNode node) {
        Set<GraphNode> set = new HashSet<>();
        if (node == null || node.parents == null) return set;
        set.addAll(node.parents);
        return set;
    }

    private void showTooltip(GraphNode node, double screenX, double screenY) {
        if (node == null || node.commit == null) return;

        CommitNode c = node.commit;

        tooltipHash.setText(shortHash(c.hash));
        tooltipAuthor.setText("Author: " + (c.author == null ? "Unknown" : c.author));
        tooltipDate.setText("Date: " + formatDateTime(c.timestamp));
        tooltipMessage.setText(prettyMessage(c.message));

        tooltipBox.setOpacity(0);
        if (graphScroll.getScene() == null || graphScroll.getScene().getWindow() == null) return;

        commitTooltip.show(graphScroll.getScene().getWindow(), screenX + 12, screenY + 12);

        FadeTransition ft = new FadeTransition(Duration.millis(140), tooltipBox);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void hideTooltip() {
        if (!commitTooltip.isShowing()) return;
        FadeTransition ft = new FadeTransition(Duration.millis(120), tooltipBox);
        ft.setFromValue(tooltipBox.getOpacity());
        ft.setToValue(0);
        ft.setOnFinished(e -> commitTooltip.hide());
        ft.play();
    }

    private void applyHighlights(GraphNode focus) {
        Set<GraphNode> focusParents = new HashSet<>();
        Set<GraphNode> focusChildren = new HashSet<>();
        if (focus != null) {
            focusParents.addAll(distinctParents(focus));
            focusChildren.addAll(childrenByNode.getOrDefault(focus, Set.of()));
        }

        for (CommitCardView view : nodeViews.values()) {
            if (focus == null) {
                view.setState(NodeState.NORMAL);
            } else if (view.node == focus) {
                view.setState(NodeState.FOCUSED);
            } else if (focusParents.contains(view.node)) {
                view.setState(NodeState.PARENT);
            } else if (focusChildren.contains(view.node)) {
                view.setState(NodeState.CHILD);
            } else {
                view.setState(NodeState.DIMMED);
            }
        }

        for (EdgeView e : edgeViews) {
            e.updateState(focus);
        }
    }

    private enum NodeState { NORMAL, DIMMED, FOCUSED, PARENT, CHILD }

    private class EdgeView {
        private final GraphNode child;
        private final GraphNode parent;
        private final CubicCurve curve = new CubicCurve();

        EdgeView(GraphNode child, GraphNode parent) {
            this.child = child;
            this.parent = parent;

            curve.setMouseTransparent(true);
            curve.setFill(null);
            curve.setStrokeWidth(2);
            curve.setStroke(Color.web(TEXT_SECONDARY, 0.55));

            // Anchors: circle center positions.
            double startX = anchorX(child);
            double startY = anchorY(child);
            double endX = anchorX(parent);
            double endY = anchorY(parent);

            curve.setStartX(startX);
            curve.setStartY(startY);
            curve.setEndX(endX);
            curve.setEndY(endY);

            // Smooth curve.
            double midX = (startX + endX) / 2.0;
            double ctrl1X = midX;
            double ctrl1Y = startY;
            double ctrl2X = midX;
            double ctrl2Y = endY;
            curve.setControlX1(ctrl1X);
            curve.setControlY1(ctrl1Y);
            curve.setControlX2(ctrl2X);
            curve.setControlY2(ctrl2Y);
        }

        private double anchorX(GraphNode n) {
            return n.x + CIRCLE_RADIUS;
        }

        private double anchorY(GraphNode n) {
            return n.y + CARD_HEIGHT / 2.0;
        }

        void updateState(GraphNode focus) {
            if (focus == null) {
                curve.setOpacity(1.0);
                curve.setStroke(Color.web(TEXT_SECONDARY, 0.55));
                curve.setStrokeWidth(2);
                return;
            }

            if (child == focus) {
                curve.setOpacity(1.0);
                curve.setStroke(Color.web("#ef4444"));
                curve.setStrokeWidth(2.6);
            } else if (parent == focus) {
                curve.setOpacity(1.0);
                curve.setStroke(Color.web("#10b981"));
                curve.setStrokeWidth(2.6);
            } else {
                curve.setOpacity(0.15);
                curve.setStroke(Color.web(TEXT_SECONDARY, 0.2));
                curve.setStrokeWidth(2);
            }
        }
    }

    private class CommitCardView {
        private final GraphNode node;
        private final Pane root = new Pane();
        private final Circle dot;
        private final VBox card;
        private final Label msg;
        private final Label meta;

        private final ScaleTransition hoverUp;
        private final ScaleTransition hoverDown;

        CommitCardView(GraphNode node) {
            this.node = node;
            root.setLayoutX(node.x);
            root.setLayoutY(node.y);
            root.setPrefSize(LANE_GAP_X, CARD_HEIGHT);
            root.setMinSize(CARD_WIDTH + 80, CARD_HEIGHT);
            root.setUserData("commit-node");
            root.setPickOnBounds(true);

            CommitNode c = node.commit;

            // Lane circle.
            dot = new Circle(CIRCLE_RADIUS, CARD_HEIGHT / 2.0, CIRCLE_RADIUS);
            dot.setStroke(Color.web(BORDER));
            dot.setStrokeWidth(1);
            dot.setFill(branchColor());

            // Card.
            card = new VBox(4);
            card.setLayoutX(CIRCLE_RADIUS * 2 + 12);
            card.setLayoutY(10);
            card.setPrefSize(CARD_WIDTH, CARD_HEIGHT - 20);
            card.setPadding(new Insets(10, 12, 10, 12));
            card.setStyle(
                    "-fx-background-color: " + CARD + ";" +
                    " -fx-border-color: " + BORDER + ";" +
                    " -fx-border-width: 1;" +
                    " -fx-background-radius: 14;" +
                    " -fx-border-radius: 14;"
            );

            msg = new Label(previewMessage(c.message));
            msg.setTextFill(Color.web(TEXT_PRIMARY));
            msg.setFont(Font.font("System", FontWeight.BOLD, 13));
            msg.setWrapText(true);
            msg.setMaxWidth(CARD_WIDTH - 4);

            meta = new Label((c.author == null ? "Unknown" : c.author) + " • " + formatDateTime(c.timestamp));
            meta.setTextFill(Color.web(TEXT_SECONDARY));
            meta.setFont(Font.font("System", 11));
            meta.setWrapText(true);
            meta.setMaxWidth(CARD_WIDTH - 4);

            card.getChildren().addAll(msg, meta);

            root.getChildren().addAll(dot, card);
            root.setCursor(Cursor.HAND);

            setState(NodeState.NORMAL);

            hoverUp = new ScaleTransition(Duration.millis(140), root);
            hoverUp.setToX(1.03);
            hoverUp.setToY(1.03);
            hoverUp.setOnFinished(e -> {});

            hoverDown = new ScaleTransition(Duration.millis(140), root);
            hoverDown.setToX(1.0);
            hoverDown.setToY(1.0);

            root.setOnMouseEntered(e -> {
                if (e.getButton() == MouseButton.PRIMARY) return;
                hoverNode = node;
                applyHighlights(hoverNode);
                hoverUp.playFromStart();
                showTooltip(node, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            root.setOnMouseExited(e -> {
                hoverNode = null;
                applyHighlights(null);
                hideTooltip();
                hoverDown.playFromStart();
                e.consume();
            });
        }

        private Color branchColor() {
            int lane = node.lane < 0 ? 0 : node.lane;
            return BRANCH_COLORS[Math.floorMod(lane, BRANCH_COLORS.length)];
        }

        void setState(NodeState state) {
            switch (state) {
                case NORMAL -> {
                    root.setOpacity(1.0);
                    setCardStroke(Color.web(BORDER), 1.0);
                    dot.setFill(branchColor());
                    dot.setStroke(Color.web(BORDER));
                }
                case DIMMED -> {
                    root.setOpacity(0.25);
                    setCardStroke(Color.web(BORDER), 1.0);
                    dot.setFill(branchColor());
                    dot.setStroke(Color.web(BORDER));
                }
                case FOCUSED -> {
                    root.setOpacity(1.0);
                    setCardStroke(Color.web("#f59e0b"), 1.8);
                    dot.setFill(Color.web("#f59e0b"));
                    dot.setStroke(Color.web("#f59e0b"));
                }
                case PARENT -> {
                    root.setOpacity(1.0);
                    setCardStroke(Color.web("#ef4444"), 1.6);
                    dot.setFill(Color.web("#ef4444"));
                    dot.setStroke(Color.web("#ef4444"));
                }
                case CHILD -> {
                    root.setOpacity(1.0);
                    setCardStroke(Color.web("#10b981"), 1.6);
                    dot.setFill(Color.web("#10b981"));
                    dot.setStroke(Color.web("#10b981"));
                }
            }
        }

        private void setCardStroke(Color stroke, double strokeWidth) {
            // Simple style update. Border radius remains from initial style.
            card.setStyle(
                    "-fx-background-color: " + CARD + ";" +
                    " -fx-border-color: " + toHex(stroke) + ";" +
                    " -fx-border-width: " + strokeWidth + ";" +
                    " -fx-background-radius: 14;" +
                    " -fx-border-radius: 14;"
            );
        }

        private String toHex(Color c) {
            // Produces #rrggbb for JavaFX Color.
            int r = (int) Math.round(c.getRed() * 255);
            int g = (int) Math.round(c.getGreen() * 255);
            int b = (int) Math.round(c.getBlue() * 255);
            return String.format("#%02x%02x%02x", r, g, b);
        }
    }
}