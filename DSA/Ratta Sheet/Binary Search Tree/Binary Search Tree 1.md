---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-11-09
---
# Binary Search Tree

> [!abstract] Standard BST questions: memorise the patterns, don't reinvent during interviews.

## Kth Smallest and Largest element in BST (Striver A2Z, BST, practice)

**Problem Statement:** Given the root of a binary search tree (BST) and an integer k, return the kth smallest element in the tree. To find the kth largest element instead, the exact same logic applies but you traverse the tree in reverse in-order (Right, Root, Left).

### Brute (In-order Traversal with Array)

**Intuition:** An in-order traversal (Left, Root, Right) of a Binary Search Tree always visits the nodes in strictly increasing sorted order. If we store all the visited node values in an array, the kth smallest element will simply sit at index k - 1.

**Complexity:** **Time:** O(N) — We visit all N nodes in the BST to build the array. | **Space:** O(N) — The array stores all N node values, plus O(H) for the recursion stack.

**Code:**
```cpp
#include <iostream>
#include <vector>

using namespace std;

struct TreeNode {
    int val;
    TreeNode *left;
    TreeNode *right;
    TreeNode() : val(0), left(nullptr), right(nullptr) {}
    TreeNode(int x) : val(x), left(nullptr), right(nullptr) {}
    TreeNode(int x, TreeNode *left, TreeNode *right) : val(x), left(left), right(right) {}
};

class Solution {
private:
    void inorder(TreeNode* node, vector<int>& inorderArr) {
        if (!node) return;
        
        inorder(node->left, inorderArr);
        inorderArr.push_back(node->val);
        inorder(node->right, inorderArr);
    }

public:
    int kthSmallest(TreeNode* root, int k) {
        vector<int> inorderArr;
        inorder(root, inorderArr);
        return inorderArr[k - 1];
    }
};
```

### Better

Same as Optimal / not applicable. Storing the entire tree in an array is unnecessary when we can optimize the space by simply counting the nodes as we visit them.

### Optimal (In-order Traversal with Counter)

**Intuition:** Since we only need the kth element, storing the entire sequence is a waste of memory. We can maintain a running counter that increments exactly when we process a node. Once the counter hits k, we lock in our answer and stop further exploration.

**Explanation:**

1. Initialize a counter to 0 and a result variable to store the final answer.
2. Start a recursive in-order traversal (Left, Root, Right). Note: To find the kth largest, you would simply swap the traversal order to Right, Root, Left.
3. Traverse down to the leftmost node. As the recursion unwinds and we "visit" a node, increment the counter.
4. If counter == k, store the current node's value in result.
5. Return early if the result is already found. This prunes the search space and saves us from traversing the rest of the right subtrees once we have our answer.

**Dry Run on small example:** Let root = [3, 1, 4, null, 2], k = 2.

1. Go left to 1. 1 has no left child.
2. Process 1: counter becomes 1. counter != k.
3. Go right from 1 to 2. 2 has no left child.
4. Process 2: counter becomes 2. counter == k, so result = 2.
5. Return early up the stack, ignoring 3 and 4. Final answer is 2.

**Complexity:** **Time:** O(H + k) — We travel down to the leftmost leaf which takes O(H) time, and then process k nodes. In a completely skewed tree, this bounds to O(N). | **Space:** O(H) — No extra array is used; auxiliary space is strictly bounded by the recursion stack depth H.

**Code:**
```cpp
#include <iostream>

using namespace std;

struct TreeNode {
    int val;
    TreeNode *left;
    TreeNode *right;
    TreeNode() : val(0), left(nullptr), right(nullptr) {}
    TreeNode(int x) : val(x), left(nullptr), right(nullptr) {}
    TreeNode(int x, TreeNode *left, TreeNode *right) : val(x), left(left), right(right) {}
};

class Solution {
private:
    void inorder(TreeNode* node, int k, int& counter, int& result) {
        // Base case or early exit if we already found the answer
        if (!node || counter >= k) return;
        
        // Traverse left
        inorder(node->left, k, counter, result);
        
        // Process the current node
        counter++;
        if (counter == k) {
            result = node->val;
            return; // Found it, start returning up the stack
        }
        
        // Traverse right
        inorder(node->right, k, counter, result);
    }

public:
    int kthSmallest(TreeNode* root, int k) {
        int counter = 0;
        int result = -1;
        inorder(root, k, counter, result);
        return result;
    }
};
```
