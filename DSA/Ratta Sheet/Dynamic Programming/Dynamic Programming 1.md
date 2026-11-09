---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-11-09
---
# Dynamic Programming

> [!abstract] Standard DP questions: memorise the patterns, don't reinvent during interviews.

## House Robber III (LeetCode, Tree/DP, Medium)

**Problem Statement:** The thief has found a new place for his thievery where all houses form a binary tree. There is only one entrance to this area, called root. The police will be alerted if two directly-linked houses (a parent and its child) are broken into on the same night. Given the root of the binary tree, return the maximum amount of money the thief can rob without alerting the police.

### Brute (Recursive DFS)

**Intuition:** At every house (node), the robber has two choices: rob this house and move to its grandchildren, or skip this house and move to its direct children. We can explore both possibilities recursively and return the maximum of the two choices.

**Complexity:** **Time:** O(2^N) — Overlapping subproblems cause exponential repeated work because we evaluate nodes multiple times. | **Space:** O(H) — The recursion stack goes as deep as the height of the tree H.

**Code:**
```cpp
#include <iostream>
#include <algorithm>

using namespace std;

// Definition for a binary tree node.
struct TreeNode {
    int val;
    TreeNode *left;
    TreeNode *right;
    TreeNode() : val(0), left(nullptr), right(nullptr) {}
    TreeNode(int x) : val(x), left(nullptr), right(nullptr) {}
    TreeNode(int x, TreeNode *left, TreeNode *right) : val(x), left(left), right(right) {}
};

class Solution {
public:
    int rob(TreeNode* root) {
        if (!root) return 0;
        
        // Option 1: Rob current node + grandchildren
        int val_if_robbed = root->val;
        if (root->left) {
            val_if_robbed += rob(root->left->left) + rob(root->left->right);
        }
        if (root->right) {
            val_if_robbed += rob(root->right->left) + rob(root->right->right);
        }
        
        // Option 2: Skip current node + rob children
        int val_if_skipped = rob(root->left) + rob(root->right);
        
        return max(val_if_robbed, val_if_skipped);
    }
};
```

### Better (Memoization / Top-Down DP)

**Intuition:** The brute-force approach recalculates the maximum money for the same tree nodes repeatedly. By storing the answer for each TreeNode* in a hash map, we can return the cached result in O(1) time whenever we encounter a previously solved node.

**Complexity:** **Time:** O(N) — Each node in the tree is processed exactly once. | **Space:** O(N) — The hash map stores an entry for every node, and the recursion stack takes up to O(H) space.

**Code:**
```cpp
#include <iostream>
#include <algorithm>
#include <unordered_map>

using namespace std;

class Solution {
private:
    unordered_map<TreeNode*, int> memo;

public:
    int rob(TreeNode* root) {
        if (!root) return 0;
        if (memo.count(root)) return memo[root];
        
        int val_if_robbed = root->val;
        if (root->left) {
            val_if_robbed += rob(root->left->left) + rob(root->left->right);
        }
        if (root->right) {
            val_if_robbed += rob(root->right->left) + rob(root->right->right);
        }
        
        int val_if_skipped = rob(root->left) + rob(root->right);
        
        return memo[root] = max(val_if_robbed, val_if_skipped);
    }
};
```

### Optimal (Tree DP / Post-order Traversal)

**Intuition:** Instead of looking down to grandchildren or querying a hash map, we can change the DFS function to return two states for every node: the maximum money if we rob it, and the maximum money if we skip it. This allows us to compute the answer cleanly from the bottom up.

**Explanation:**

1. Perform a post-order traversal using a helper function dfs that returns a pair<int, int> representing {max_if_robbed, max_if_skipped}.
2. Base case: If the current node is NULL, return {0, 0} because there is no money to extract.
3. Recursively call dfs on node->left and node->right to get the optimal states for the left and right subtrees.
4. Calculate robbed: If we rob the current node, we must skip both direct children. We add node->val to the skip values of both the left and right children.
5. Calculate not_robbed: If we skip the current node, we can freely choose to either rob or skip its children, whichever yields more money. We sum the maximum of the left child's states with the maximum of the right child's states.
6. Return the {robbed, not_robbed} pair back up the tree. Finally, return the maximum of the two states from the root.

**Dry Run on small example:** Let the tree be [3, 2, 3, null, 3, null, 1].

1. The leaf 3 (left grandchild) returns {3, 0}.
2. The leaf 1 (right grandchild) returns {1, 0}.
3. Moving up to node 2: if robbed, it takes 2 + 0 = 2; if skipped, it takes max(3, 0) = 3. Returns {2, 3}.
4. Node 3 (right child of root) returns {3, 1}.
5. At the root (value 3): if robbed, it takes 3 + 3 (left skipped) + 1 (right skipped) = 7. If skipped, it takes max(2, 3) + max(3, 1) = 3 + 3 = 6. The max of 7 and 6 is 7.

**Complexity:** **Time:** O(N) — We visit every node in the binary tree exactly once during the post-order traversal. | **Space:** O(H) — No hash map is needed; auxiliary space is strictly bounded by the recursion stack depth, which matches the height of the tree H.

**Code:**
```cpp
#include <iostream>
#include <algorithm>
#include <utility>

using namespace std;

class Solution {
private:
    // Returns a pair: {max_if_robbed, max_if_skipped}
    pair<int, int> dfs(TreeNode* node) {
        if (!node) {
            return {0, 0};
        }
        
        // Post-order: get states of left and right subtrees
        pair<int, int> left = dfs(node->left);
        pair<int, int> right = dfs(node->right);
        
        // If we rob the current node, we cannot rob its children
        int rob_this = node->val + left.second + right.second;
        
        // If we skip the current node, we take the best outcome from its children (either robbed or skipped)
        int skip_this = max(left.first, left.second) + max(right.first, right.second);
        
        return {rob_this, skip_this};
    }

public:
    int rob(TreeNode* root) {
        pair<int, int> result = dfs(root);
        // Return the maximum of robbing the root vs skipping the root
        return max(result.first, result.second);
    }
};
```
