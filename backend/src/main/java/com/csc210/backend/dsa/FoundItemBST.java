
package com.csc210.backend.dsa;

import com.csc210.backend.model.FoundItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

public class FoundItemBST {

    // Each node stores one found item
    private static class Node {
        FoundItem item;
        Node left;
        Node right;

        Node(FoundItem item) {
            this.item = item;
        }
    }

    private Node root;

    // Compare items by date, then by unique ID
    private int compare(FoundItem a, FoundItem b) {
        int dateCompare = a.getDateTime()
            .compareTo(b.getDateTime());

        if (dateCompare != 0) {
            return dateCompare;
        }

        return Long.compare(a.getId(), b.getId());
    }

    // Insert a found item into the BST
    public void insert(FoundItem item) {
        if (item == null || item.getId() == null
                || item.getDateTime() == null) {
            throw new IllegalArgumentException(
                "Item must have an ID and date");
        }

        root = insertRecursive(root, item);
    }

    private Node insertRecursive(Node node, FoundItem item) {
        if (node == null) {
            return new Node(item);
        }

        int comparison = compare(item, node.item);

        if (comparison < 0) {
            node.left = insertRecursive(node.left, item);
        } else if (comparison > 0) {
            node.right = insertRecursive(node.right, item);
        } else {
            // Update existing item with same ID and date
            node.item = item;
        }

        return node;
    }

    // Search for a found item using its date and ID
    public FoundItem search(LocalDateTime dateTime, Long id) {
        if (dateTime == null || id == null) {
            return null;
        }

        Node current = root;

        while (current != null) {
            int dateComparison =
                dateTime.compareTo(current.item.getDateTime());

            int comparison = (dateComparison != 0)
                ? dateComparison
                : Long.compare(id, current.item.getId());

            if (comparison == 0) {
                return current.item;
            } else if (comparison < 0) {
                current = current.left;
            } else {
                current = current.right;
            }
        }

        return null;
    }

    // Find all items within an inclusive date range
    public List<FoundItem> rangeSearch(
            LocalDateTime start, LocalDateTime end) {

        if (start == null || end == null
                || start.isAfter(end)) {
            throw new IllegalArgumentException(
                "Invalid date range");
        }

        List<FoundItem> results = new ArrayList<>();

        rangeSearchRecursive(root, start, end, results);

        return results;
    }

    private void rangeSearchRecursive(
            Node node,
            LocalDateTime start,
            LocalDateTime end,
            List<FoundItem> results) {

        if (node == null) {
            return;
        }

        LocalDateTime date = node.item.getDateTime();

        // Search left, including items with the same date
        if (!date.isBefore(start)) {
            rangeSearchRecursive(
                node.left, start, end, results);
        }

        // Include items within the date range
        if (!date.isBefore(start) && !date.isAfter(end)) {
            results.add(node.item);
        }

        // Search right, including items with the same date
        if (!date.isAfter(end)) {
            rangeSearchRecursive(
                node.right, start, end, results);
        }
    }

    // Delete a found item using its date and ID
    public void delete(LocalDateTime dateTime, Long id) {
        if (dateTime == null || id == null) {
            throw new IllegalArgumentException(
                "Date and ID cannot be null");
        }

        root = deleteRecursive(root, dateTime, id);
    }

    private Node deleteRecursive(
            Node node, LocalDateTime dateTime, Long id) {

        if (node == null) {
            return null;
        }

        int dateComparison =
            dateTime.compareTo(node.item.getDateTime());

        int comparison = (dateComparison != 0)
            ? dateComparison
            : Long.compare(id, node.item.getId());

        if (comparison < 0) {
            node.left = deleteRecursive(
                node.left, dateTime, id);

        } else if (comparison > 0) {
            node.right = deleteRecursive(
                node.right, dateTime, id);

        } else {
            // Case 1: No children
            if (node.left == null && node.right == null) {
                return null;
            }

            // Case 2: Only one child
            if (node.left == null) {
                return node.right;
            }

            if (node.right == null) {
                return node.left;
            }

            // Case 3: Two children
            // Find the smallest node in the right subtree
            Node successor = findMin(node.right);

            node.item = successor.item;

            node.right = deleteRecursive(
                node.right,
                successor.item.getDateTime(),
                successor.item.getId());
        }

        return node;
    }

    // Find the minimum node in a subtree
    private Node findMin(Node node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }
}

