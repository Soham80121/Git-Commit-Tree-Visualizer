package com.gitvisualizer.service;

import com.gitvisualizer.model.CommitNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.util.*;

public class GitService {

    public List<CommitNode> getAllCommits(String repoPath) {
        List<CommitNode> list = new ArrayList<>();

        try {
            var repo = new FileRepositoryBuilder()
                    .setGitDir(new File(repoPath + "/.git"))
                    .build();

            Git git = new Git(repo);

            Iterable<RevCommit> commits = git.log().all().call();

            for (RevCommit commit : commits) {
                CommitNode node = new CommitNode();

                node.hash = commit.getName();
                node.message = commit.getFullMessage();
                node.author = commit.getAuthorIdent().getName();
                node.timestamp = commit.getAuthorIdent().getWhen();

                for (RevCommit parent : commit.getParents()) {
                    node.parentHashes.add(parent.getName());
                }

                list.add(node);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}