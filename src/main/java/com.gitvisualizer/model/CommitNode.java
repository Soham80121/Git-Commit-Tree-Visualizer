package com.gitvisualizer.model;

import java.util.*;

public class CommitNode {
    public String hash;
    public String message;
    public String author;
    public Date timestamp;
    public List<String> parentHashes = new ArrayList<>();
}