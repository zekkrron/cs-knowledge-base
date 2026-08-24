---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-08-05
---
# Binary Tree

> [!abstract] Standard questions for binary trees — memorise the patterns, don't reinvent during interviews.

## Iterative Preorder Traversal of Binary Tree (Striver A2Z, BT, traversal)

**Problem Statement:** Given the root of a binary tree, return the preorder traversal of its nodes' values using an iterative approach (without using the built-in system recursion stack).

### Brute (Recursive Approach)

Intuition: The standard way to traverse a tree is by relying on the system's call stack. We simply visit the current node, make a recursive call to the left child, and then a recursive call to the right child.

Complexity: Time: $O(N)$ | Space: $O(N)$ (Auxiliary space for the recursion stack in the worst-case skewed tree)

### Optimal (Iterative using Stack)

Intuition: We can eliminate the system call stack by simulating it with our own explicit data structure (std::stack). Since preorder traversal strictly follows a Root $\rightarrow$ Left $\rightarrow$ Right order, and a stack is a Last-In-First-Out (LIFO) data structure, we process the root, and then push the Right child first followed by the Left child. This ensures the Left child stays on top and gets processed next.

Explanation:

Edge Case: If the tree is empty (root == nullptr), simply return an empty array.

Initialization: Create a stack to hold TreeNode* pointers and push the root node onto it.

Processing the Stack: Run a while loop until the stack becomes empty.
- Process Root: In each iteration, pop the node at the top of the stack and add its value to your preorder answer array.
- Push Right Child: Check if the popped node has a right child. If yes, push it onto the stack.
- Push Left Child: Check if the popped node has a left child. If yes, push it onto the stack.
- Why Right then Left? Because of the LIFO nature of a stack, pushing the right child before the left child guarantees that the left child will be popped and processed first in the immediate next iteration.

Complexity: Time: $O(N)$ | Space: $O(H) \approx O(N)$ (Where $H$ is the height of the tree. In the worst case of a completely unbalanced tree, the stack can hold $O(N)$ elements. In a balanced tree, it holds $O(\log N)$).

Coding Implementation:

```cpp
#include <vector>
#include <stack>

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
    vector<int> preorderTraversal(TreeNode* root) {
        vector<int> preorder;
        if (root == nullptr) {
            return preorder;
        }
        
        stack<TreeNode*> st;
        st.push(root);
        
        while (!st.empty()) {
            // Process the top node (Root)
            TreeNode* curr = st.top();
            st.pop();
            preorder.push_back(curr->val);
            
            // Push Right child first so it is processed AFTER the Left child
            if (curr->right != nullptr) {
                st.push(curr->right);
            }
            
            // Push Left child second so it sits at the top of the stack
            if (curr->left != nullptr) {
                st.push(curr->left);
            }
        }
        
        return preorder;
    }
};
```

(Note: There is a strictly $O(1)$ space algorithm called Morris Traversal for preorder traversal, but it requires temporarily mutating the tree's structure (creating threading). For the standard "Iterative Preorder" pattern expected in interviews, the Stack approach is the primary intended solution.)

## Morris Preorder Traversal of a Binary Tree (Striver A2Z, BT, hard)

**Problem Statement:** Given the root of a binary tree, return the preorder traversal of its nodes' values using an $O(1)$ auxiliary space algorithm (Morris Traversal).

### Brute (Recursive)

Intuition: Use the system's call stack to recursively traverse the tree in a Root $\rightarrow$ Left $\rightarrow$ Right order.

Complexity: Time: $O(N)$ | Space: $O(N)$ (Auxiliary space for the recursion stack in the worst-case skewed tree)

### Better (Iterative using Stack)

Intuition: Eliminate the system call stack by simulating it with an explicit std::stack. Process the root, then push the right child, followed by the left child to ensure the left subtree is processed first.

Complexity: Time: $O(N)$ | Space: $O(N)$ (Stack space)

### Optimal (Morris Traversal)

Intuition: To achieve strict $O(1)$ auxiliary space, we must eliminate all stacks. Morris Traversal does this by temporarily altering the tree's structure. It creates a temporary "thread" (link) from the rightmost node of the left subtree (the inorder predecessor) back to the current node. This allows the algorithm to return to the parent node after traversing the left subtree without needing a stack to remember the way back.

Explanation:

Initialization: Start with a pointer curr pointing to the root.

Traversal Loop: Continue as long as curr != nullptr.

Case 1: No Left Child: If curr->left == nullptr, there is no left subtree to process. Following Preorder (Root $\rightarrow$ Left $\rightarrow$ Right), we process the current node (add to answer), and then move to the right child (curr = curr->right).

Case 2: Left Child Exists: If there is a left subtree, we need to find the current node's "inorder predecessor". This is the rightmost node in the left subtree. We use a prev pointer, starting at curr->left, and move right (prev = prev->right) until prev->right is either nullptr or equals curr.
- Case 2a: Creating the Thread (First Visit): If prev->right == nullptr, it means we are visiting this part of the tree for the very first time. Because this is a Preorder traversal, we must process the root before moving into its left subtree.
  - Add curr->val to our answer.
  - Create the thread back to the root: prev->right = curr.
  - Move to the left child: curr = curr->left.
- Case 2b: Cutting the Thread (Second Visit): If prev->right == curr, it means the thread already exists. This indicates we have completely finished traversing the left subtree and have used the thread to return to the root.
  - We must restore the tree to its original state by breaking the thread: prev->right = nullptr.
  - Since the root and left subtree are done, we move to the right child: curr = curr->right.

Complexity: Time: $O(N)$ (Amortized, as each edge is traversed at most 3 times) | Space: $O(1)$ (No extra space used other than the output array).

Coding Implementation:

```cpp
#include <vector>

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
    vector<int> preorderTraversal(TreeNode* root) {
        vector<int> preorder;
        TreeNode* curr = root;
        
        while (curr != nullptr) {
            // Case 1: No left child
            if (curr->left == nullptr) {
                preorder.push_back(curr->val); // Process root
                curr = curr->right;            // Move right
            } 
            // Case 2: Left child exists
            else {
                // Find the inorder predecessor (rightmost node in left subtree)
                TreeNode* prev = curr->left;
                while (prev->right != nullptr && prev->right != curr) {
                    prev = prev->right;
                }
                
                // Case 2a: First visit to this node, establish the thread
                if (prev->right == nullptr) {
                    prev->right = curr;             // Create thread
                    preorder.push_back(curr->val);  // Process root (Preorder logic)
                    curr = curr->left;              // Move left
                } 
                // Case 2b: Second visit (returned via thread), remove the thread
                else {
                    prev->right = nullptr;          // Cut thread to restore tree
                    curr = curr->right;             // Move right
                }
            }
        }
        
        return preorder;
    }
};
```

## Morris Inorder Traversal of a Binary Tree (Striver A2Z, BT, hard)

**Problem Statement:** Given the root of a binary tree, return the inorder traversal of its nodes' values using an $O(1)$ auxiliary space algorithm (Morris Traversal).

### Brute (Recursive)

Intuition: Use the system's call stack to recursively traverse the tree in a Left $\rightarrow$ Root $\rightarrow$ Right order.

Complexity: Time: $O(N)$ | Space: $O(N)$ (Auxiliary space for the recursion stack in the worst-case skewed tree)

### Better (Iterative using Stack)

Intuition: Eliminate the system call stack by simulating it with an explicit std::stack. We continuously push left children onto the stack until we hit nullptr, then pop and process the node, and move to its right child.

Complexity: Time: $O(N)$ | Space: $O(N)$ (Stack space)

### Optimal (Morris Traversal)

Intuition: To achieve strict $O(1)$ auxiliary space, we temporarily alter the tree's structure by creating a "thread" (link) from the rightmost node of the left subtree (the inorder predecessor) back to the current node. The core difference between Morris Preorder and Morris Inorder is simply when we add the node's value to our answer array: we add it only after we are done visiting the left subtree.

Explanation:

Initialization: Start with a pointer curr pointing to the root.

Traversal Loop: Continue as long as curr != nullptr.

Case 1: No Left Child: If curr->left == nullptr, there is no left subtree to process. Following Inorder (Left $\rightarrow$ Root $\rightarrow$ Right), since there is no "Left", we process the "Root" (add curr->val to answer) and then move to the "Right" child (curr = curr->right).

Case 2: Left Child Exists: If there is a left subtree, we need to find the current node's "inorder predecessor". This is the rightmost node in the left subtree. We use a prev pointer, starting at curr->left, and move right (prev = prev->right) until prev->right is either nullptr or equals curr.
- Case 2a: Creating the Thread (First Visit): If prev->right == nullptr, it means we are visiting this part of the tree for the very first time and haven't processed the left subtree yet.
  - Create the thread back to the root: prev->right = curr.
  - Move to the left child: curr = curr->left.
  - (Notice we do NOT process the node here, because in Inorder, the left subtree must be processed first).
- Case 2b: Cutting the Thread (Second Visit): If prev->right == curr, it means the thread already exists. This indicates we have completely finished traversing the left subtree and have used the thread to return to the root.
  - We must restore the tree to its original state by breaking the thread: prev->right = nullptr.
  - Now that the left subtree is fully processed, we process the current node: add curr->val to our answer.
  - Finally, move to the right child: curr = curr->right.

Complexity: Time: $O(N)$ (Amortized, as each edge is traversed at most 3 times) | Space: $O(1)$ (No extra space used other than the output array).

Coding Implementation:

```cpp
#include <vector>

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
    vector<int> inorderTraversal(TreeNode* root) {
        vector<int> inorder;
        TreeNode* curr = root;
        
        while (curr != nullptr) {
            // Case 1: No left child
            if (curr->left == nullptr) {
                inorder.push_back(curr->val);  // Process root
                curr = curr->right;            // Move right
            } 
            // Case 2: Left child exists
            else {
                // Find the inorder predecessor (rightmost node in left subtree)
                TreeNode* prev = curr->left;
                while (prev->right != nullptr && prev->right != curr) {
                    prev = prev->right;
                }
                
                // Case 2a: First visit, establish the thread
                if (prev->right == nullptr) {
                    prev->right = curr;             // Create thread
                    // (Do not process the node here, move left first)
                    curr = curr->left;              // Move left
                } 
                // Case 2b: Second visit (returned via thread), remove the thread
                else {
                    prev->right = nullptr;          // Cut thread to restore tree
                    inorder.push_back(curr->val);   // Process root (Left subtree is done)
                    curr = curr->right;             // Move right
                }
            }
        }
        
        return inorder;
    }
};
```
