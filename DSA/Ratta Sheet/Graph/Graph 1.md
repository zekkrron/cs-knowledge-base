---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-08-05
---
# Graph

> [!abstract] Standard questions for graphs — memorise the patterns, don't reinvent during interviews.

## Word Ladder 1 (Striver A2Z, Graph, BFS/DFS)

**Problem Statement:** Given two words, `beginWord` and `endWord`, and a dictionary `wordList`, return the number of words in the shortest transformation sequence from `beginWord` to `endWord`. In each step, you can only change one letter, and the transformed word must exist in the `wordList`. If no such sequence exists, return 0.

### Brute (DFS / Backtracking)

Explore all possible valid transformation paths from `beginWord` to `endWord` using Depth-First Search (DFS). Keep track of the shortest path found.

- We start at the `beginWord` and recursively try to move to every other unvisited word in the `wordList`.
- A move is only valid if the two words differ by exactly one character.
- We maintain a visited set so we don't get trapped in infinite cycles (e.g., "hit" → "hot" → "hit").
- If we successfully reach `endWord`, we compare the number of steps taken with our global minimum and update it if it's shorter.
- After exploring a path, we backtrack by removing the word from the visited set to allow other paths to use it.

**Time:** O(N! · L) (exploring all permutations of paths in the worst-case complete graph) | **Space:** O(N) (for the recursion stack and visited set)

```cpp
#include <iostream>
#include <vector>
#include <string>
#include <unordered_set>
#include <climits>
#include <algorithm>

using namespace std;

class Solution {
private:
    bool diffByOne(const string& a, const string& b) {
        int diffCount = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a[i] != b[i]) diffCount++;
            if (diffCount > 1) return false;
        }
        return diffCount == 1;
    }

    void dfs(string currentWord, string& endWord, vector<string>& wordList, 
             unordered_set<string>& visited, int steps, int& minSteps) {
        if (currentWord == endWord) {
            minSteps = min(minSteps, steps);
            return;
        }

        for (const string& nextWord : wordList) {
            if (visited.find(nextWord) == visited.end() && diffByOne(currentWord, nextWord)) {
                visited.insert(nextWord);
                dfs(nextWord, endWord, wordList, visited, steps + 1, minSteps);
                visited.erase(nextWord); // Backtrack
            }
        }
    }

public:
    int ladderLength(string beginWord, string endWord, vector<string>& wordList) {
        unordered_set<string> visited;
        int minSteps = INT_MAX;
        
        dfs(beginWord, endWord, wordList, visited, 1, minSteps);
        
        return minSteps == INT_MAX ? 0 : minSteps;
    }
};
```

### Better (BFS with Explicit Adjacency List)

Since we need the shortest path in an unweighted graph, Breadth-First Search (BFS) is the mathematically optimal traversal. Here, we build the complete graph explicitly by comparing every word to every other word to form an adjacency list, then run BFS.

- Build the Graph: Iterate through all possible pairs of words in `wordList` (including `beginWord`). Compare them character by character. If they differ by exactly one character, add a bidirectional edge between them in an adjacency list.
- Unvisited Set: Insert all words into an unvisited Hash Set. To maintain consistency and save space, we will mark nodes as visited by instantly removing them from this set.
- BFS Traversal: Initialize a queue with `beginWord` and a steps counter starting at 1. Erase `beginWord` from the unvisited set.
- Traverse level by level. For the current word, check all its neighbors from the adjacency list. If a neighbor is still in the unvisited set, push it to the queue and instantly erase it from the set.
- The moment we pop `endWord` from the queue, we return the step count. Because BFS expands uniformly, the first time we hit the target, it is guaranteed to be the shortest path.

**Time:** O(N² · L) to build the adjacency list, plus O(V + E) for the BFS traversal | **Space:** O(N²) for the dense adjacency list

```cpp
#include <vector>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <queue>
#include <algorithm>

using namespace std;

class Solution {
private:
    bool diffByOne(const string& a, const string& b) {
        int diffCount = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a[i] != b[i]) diffCount++;
            if (diffCount > 1) return false;
        }
        return diffCount == 1;
    }

public:
    int ladderLength(string beginWord, string endWord, vector<string>& wordList) {
        // Add beginWord to a combined list for graph building
        vector<string> allWords = wordList;
        if (find(allWords.begin(), allWords.end(), beginWord) == allWords.end()) {
            allWords.push_back(beginWord);
        }
        
        // Step 1: Build Adjacency List
        unordered_map<string, vector<string>> adj;
        for (int i = 0; i < allWords.size(); i++) {
            for (int j = i + 1; j < allWords.size(); j++) {
                if (diffByOne(allWords[i], allWords[j])) {
                    adj[allWords[i]].push_back(allWords[j]);
                    adj[allWords[j]].push_back(allWords[i]);
                }
            }
        }
        
        // Step 2: BFS Initialization
        queue<pair<string, int>> q;
        unordered_set<string> unvisited(allWords.begin(), allWords.end());
        
        q.push({beginWord, 1});
        unvisited.erase(beginWord); // Mark as visited by removing
        
        while (!q.empty()) {
            string word = q.front().first;
            int steps = q.front().second;
            q.pop();
            
            if (word == endWord) return steps;
            
            for (const string& neighbor : adj[word]) {
                // If neighbor is still in the unvisited set
                if (unvisited.find(neighbor) != unvisited.end()) {
                    unvisited.erase(neighbor); // Instantly remove to mark as visited
                    q.push({neighbor, steps + 1});
                }
            }
        }
        
        return 0;
    }
};
```

### Optimal (BFS with Implicit Graph / On-the-Fly Mutation)

We can bypass the massive O(N²) cost of building an explicit graph. Instead of asking "Does an edge exist between these two words out of N?", we simply mutate our current word character by character and use a Hash Set to instantly ask "Does this valid mutation exist in our dictionary?".

- Hash Set: Insert all words from `wordList` into an `unordered_set`. This utilizes hashing to give us an average O(1) lookup time.
- Queue: Initialize a queue for BFS that stores pairs of {current_word, steps}. Start by pushing {beginWord, 1}.
- BFS Traversal: Pop the front element. If it's the `endWord`, return steps.
- On-the-fly Edges: Iterate through each character index of the current_word. Temporarily replace it with every letter from 'a' to 'z'.
- Validation: If the newly formed string exists in our Hash Set, it's a valid neighbor! We push {new_string, steps + 1} into the queue.
- Marking Visited: Crucially, we must immediately erase this new string from the Hash Set. Removing it acts as our visited marking, preventing infinite cycles without needing a separate visited data structure.

**Time:** O(N · L · 26) (where N is the number of words, L is word length; creating the mutated string takes O(L), making it effectively O(N · L² · 26), which strictly outperforms O(N²) when N is large) | **Space:** O(N · L) (for the Hash Set and Queue)

```cpp
#include <vector>
#include <string>
#include <unordered_set>
#include <queue>

using namespace std;

class Solution {
public:
    int ladderLength(string beginWord, string endWord, vector<string>& wordList) {
        // Step 1: Insert all words into a hash set for O(1) lookups
        unordered_set<string> wordSet(wordList.begin(), wordList.end());
        
        // If endWord is not even in the dictionary, return 0
        if (wordSet.find(endWord) == wordSet.end()) {
            return 0;
        }
        
        // Queue stores {word, steps}
        queue<pair<string, int>> q;
        q.push({beginWord, 1});
        
        // Remove beginWord from set to prevent revisiting
        wordSet.erase(beginWord);
        
        while (!q.empty()) {
            string word = q.front().first;
            int steps = q.front().second;
            q.pop();
            
            // If we reached the target word, return the steps
            if (word == endWord) {
                return steps;
            }
            
            // Step 4: Mutate the word to dynamically find valid neighbors
            for (int i = 0; i < word.length(); i++) {
                char originalChar = word[i];
                
                // Try replacing with every character from 'a' to 'z'
                for (char ch = 'a'; ch <= 'z'; ch++) {
                    word[i] = ch;
                    
                    // Step 5: If valid neighbor found in dictionary
                    if (wordSet.find(word) != wordSet.end()) {
                        q.push({word, steps + 1});
                        wordSet.erase(word); // Instantly remove to mark as visited
                    }
                }
                
                // Restore the original character before mutating the next index
                word[i] = originalChar;
            }
        }
        
        return 0;
    }
};
```

## Bridges in graph (Striver A2Z, Graph, Other)

**Problem Statement:** Given an undirected graph of V vertices and E edges, find all the bridges. A bridge (or critical connection) is an edge whose removal increases the number of connected components in the graph (i.e., it disconnects a previously connected portion of the graph).

### Brute

For every single edge in the graph, we can temporarily remove it and then run a standard Depth-First Search (DFS) or Breadth-First Search (BFS) to check if the graph becomes disconnected (i.e., we cannot reach all vertices). If removing the edge breaks the graph into more components, that edge is a bridge.

**Time:** O(E · (V + E)) (for each of the E edges, we potentially traverse the entire graph of V vertices and E edges) | **Space:** O(V + E) (to store the adjacency list and visited array)

### Better

For this specific problem, there is no intermediate "Better" approach that bridges the gap between the naive edge-removal simulation and the optimal single-pass traversal. We move directly to the mathematically optimal solution.

**Time:** N/A | **Space:** N/A

### Optimal (Tarjan's Algorithm)

We can find all bridges in a single DFS traversal using Tarjan's Algorithm. We maintain two timestamps for each node: `tin` (the time it was first discovered) and `low` (the lowest discovery time reachable from it). An edge u - v is a bridge if the lowest reachable time of v is strictly greater than the discovery time of u. This means there is no other path (back-edge) from v or its descendants back to u or any of u's ancestors.

- Initialize a `timer = 1` and arrays `tin[]` (time of insertion) and `low[]` (lowest reachable time) to 0, along with a visited array.
- Start a DFS traversal. When visiting node u, mark it visited, set `tin[u] = low[u] = timer`, and increment the timer.
- Iterate through all neighbors v of u:
- If v is the parent: Skip it to avoid moving backwards over the edge we just traversed.
- If v is already visited (The Back Edge): This means we found a backdoor to a node currently waiting in the recursion stack. Because DFS explores greedily, it traveled down one path first and only later found this connection back up to an ancestor. We update `low[u] = min(low[u], low[v])`. This back edge establishes a backup route for the graph.
- If v is not visited (The Tree Edge): Recursively call DFS on v.
- The Bubbling Up: Once the recursive DFS for v finishes, we update u's lowest reachable time: `low[u] = min(low[u], low[v])`. Because a child node v can reach whatever its descendants can reach, if v found a back edge, that knowledge successfully "bubbles up" the DFS tree to u.
- The Absence of Cross Edges: In an undirected graph, cross edges (edges connecting completely separate branches) are mathematically impossible. If a connection existed between two branches, the greedy DFS would have crossed it immediately, merging them into a single branch. Therefore, the only possible escape route for v to remain connected to the rest of the graph is a back edge pointing straight up the ancestral chain.
- The Bridge Condition: Finally, check if `low[v] > tin[u]`. Because cross edges cannot exist, if v's lowest reachable time is strictly greater than u's discovery time, it proves v and its descendants failed to find any back edge to u or above. Since there are no side-routes or upward escape routes, cutting the u - v edge will completely trap v. Thus, u - v is a bridge.
- Structural Mapping vs. Backtracking: Notice that when we mark a node as visited, we never un-visit it (no backtracking). This is because our goal is to map the physical structure of the graph to find weaknesses (bridges). If our goal was to exhaustively find all possible sequences or paths, we would un-visit nodes to let other paths use them. But here, once a node is fully processed, its structural role in the graph is resolved. Un-marking it would result in infinite loops and destroy our ability to detect back edges.

**Time:** O(V + E) (a single DFS traversal of the graph) | **Space:** O(V + E) (for the adjacency list, recursion stack, and the tin, low, and visited arrays)

```cpp
#include <vector>
#include <algorithm>

using namespace std;

class Solution {
private:
    int timer = 1;
    void dfs(int node, int parent, vector<int>& vis, vector<int> adj[], 
             int tin[], int low[], vector<vector<int>>& bridges) {
        
        vis[node] = 1;
        tin[node] = low[node] = timer;
        timer++;
        
        for (auto it : adj[node]) {
            if (it == parent) continue;
            
            if (!vis[it]) {
                dfs(it, node, vis, adj, tin, low, bridges);
                
                // After DFS finishes for the neighbor, knowledge bubbles up
                low[node] = min(low[node], low[it]);
                
                // Check if the edge is a bridge
                if (low[it] > tin[node]) {
                    bridges.push_back({node, it});
                }
            } else {
                // Back-edge found: update lowest reachable time
                low[node] = min(low[node], low[it]);
            }
        }
    }
    
public:
    vector<vector<int>> criticalConnections(int n, vector<vector<int>>& connections) {
        vector<int> adj[n];
        for (auto it : connections) {
            adj[it[0]].push_back(it[1]);
            adj[it[1]].push_back(it[0]);
        }
        
        vector<int> vis(n, 0);
        int tin[n];
        int low[n];
        vector<vector<int>> bridges;
        
        // Start DFS from the 0th node 
        // (Assuming the graph is fully connected as per standard problem)
        dfs(0, -1, vis, adj, tin, low, bridges);
        
        return bridges;
    }
};
```

## Clone Graph LC: 133 (Grind 75, Graph, BFS/DFS)

**Problem Statement:** Given a reference of a node in a connected undirected graph, return a deep copy (clone) of the graph. The input node contains an integer val and a list of neighbors (a vector of node pointers).

### Brute (Breadth-First Search)

**Intuition:** We can traverse the original graph level by level using a queue while building the new graph. To prevent infinite loops caused by cycles, we use a hash map to link the original nodes to their cloned counterparts, ensuring each node is copied only once.

**Complexity:** **Time:** O(V + E) — every vertex and edge is processed exactly once by the queue | **Space:** O(V) — the queue and hash map store at most V nodes

**Code:**
```cpp
#include <vector>
#include <unordered_map>
#include <queue>

using namespace std;

// Definition for a Node.
class Node {
public:
    int val;
    vector<Node*> neighbors;
    Node() {
        val = 0;
        neighbors = vector<Node*>();
    }
    Node(int _val) {
        val = _val;
        neighbors = vector<Node*>();
    }
    Node(int _val, vector<Node*> _neighbors) {
        val = _val;
        neighbors = _neighbors;
    }
};

class Solution {
public:
    Node* cloneGraph(Node* node) {
        if (!node) return nullptr;
        
        unordered_map<Node*, Node*> visited;
        queue<Node*> q;
        
        Node* clone = new Node(node->val);
        visited[node] = clone;
        q.push(node);
        
        while (!q.empty()) {
            Node* curr = q.front();
            q.pop();
            
            for (Node* neighbor : curr->neighbors) {
                if (visited.find(neighbor) == visited.end()) {
                    visited[neighbor] = new Node(neighbor->val);
                    q.push(neighbor);
                }
                visited[curr]->neighbors.push_back(visited[neighbor]);
            }
        }
        return clone;
    }
};
```

### Better

Same as Optimal / not applicable. There is no intermediate approach because any graph cloning algorithm must visit all vertices and edges, which standard BFS/DFS already accomplish in optimal linear time.

### Optimal (Depth-First Search)

**Intuition:** A recursive DFS is generally cleaner and more intuitive to write for this problem. As we dive deep into the graph, we instantly cache each newly created clone in a hash map; this instantly resolves undirected cycles and lets us seamlessly build the neighbors array as the recursion unwinds.

**Explanation:**

1. Base Case: If the input node is nullptr, return nullptr.
2. Hash Map Check: Check if node is already in our visited map. If it is, return the cached cloned node immediately to avoid infinite recursion on cycles.
3. Clone the Node: Create a new cloned node using node->val. Crucially, store it in the visited map before iterating through its neighbors.
4. Traverse Neighbors: Iterate through the neighbors array of the original node. For each neighbor, recursively call the DFS function and append the returned clone to the current clone's neighbors list.
5. Return: Pass back the fully constructed cloned node.

**Dry Run:** Consider a graph 1 -- 2. Start with node = 1, visited = {}.

1. DFS(1): Clone 1, set visited[1] = clone1. Neighbors of 1 are [2].
2. DFS(2): Clone 2, set visited[2] = clone2. Neighbors of 2 are [1].
3. DFS(1) called from 2: 1 is in visited. Return clone1.
4. clone2's neighbors become [clone1]. Return clone2.
5. clone1's neighbors become [clone2]. Return clone1.

**Complexity:** **Time:** O(V + E) — recursively visits every vertex and explores every edge exactly once | **Space:** O(V) — the recursion call stack and hash map take up to V space

**Code:**
```cpp
#include <vector>
#include <unordered_map>

using namespace std;

// Definition for a Node is identical to above, omitted here for brevity 
// but assumed to be present in the LeetCode environment.

class Solution {
    // Hash map to map original nodes to their cloned counterparts
    unordered_map<Node*, Node*> visited;
    
public:
    Node* cloneGraph(Node* node) {
        // Step 1: Base case for empty graph
        if (!node) return nullptr;
        
        // Step 2: If the node is already cloned, return the clone to avoid cycles
        if (visited.find(node) != visited.end()) {
            return visited[node];
        }
        
        // Step 3: Clone the current node and save it in the map immediately
        Node* clone = new Node(node->val);
        visited[node] = clone;
        
        // Step 4: Traverse neighbors and recursively clone them
        for (Node* neighbor : node->neighbors) {
            clone->neighbors.push_back(cloneGraph(neighbor));
        }
        
        // Step 5: Return the fully constructed clone
        return clone;
    }
};
```

## Most stones removed with same row or column (Striver A2Z, Graph, DSU)

**Problem Statement:** Given a 2D array of stones where stones[i] = [x_i, y_i], a stone can be removed if it shares either the same row or the same column as another stone that has not been removed. Return the maximum number of stones that can be removed.

### Brute (Adjacency List & DFS/BFS)

**Intuition:** We can treat each stone as a node. Two nodes share an edge if they have the same x or y coordinate. If a connected group has K stones, we can systematically remove K - 1 stones, leaving exactly 1 behind. Therefore, the answer is Total Stones - Total Connected Components. We can find the components by building a full graph and using DFS or BFS to traverse it.

**Complexity:** **Time:** O(N^2) — comparing every pair of stones to build the graph and traversing it | **Space:** O(N^2) — storing the dense adjacency list for up to N nodes

**Code:**
```cpp
#include <vector>

using namespace std;

class Solution {
public:
    void dfs(int node, vector<vector<int>>& adj, vector<bool>& visited) {
        visited[node] = true;
        for(int neighbor : adj[node]) {
            if(!visited[neighbor]) {
                dfs(neighbor, adj, visited);
            }
        }
    }
    
    int removeStones(vector<vector<int>>& stones) {
        int n = stones.size();
        vector<vector<int>> adj(n);
        
        // Build the graph: O(N^2) time complexity
        for(int i = 0; i < n; i++) {
            for(int j = i + 1; j < n; j++) {
                if(stones[i][0] == stones[j][0] || stones[i][1] == stones[j][1]) {
                    adj[i].push_back(j);
                    adj[j].push_back(i);
                }
            }
        }
        
        int components = 0;
        vector<bool> visited(n, false);
        
        // Count connected components
        for(int i = 0; i < n; i++) {
            if(!visited[i]) {
                components++;
                dfs(i, adj, visited);
            }
        }
        
        return n - components;
    }
};
```

### Better

Same as Optimal / not applicable. Building the $O(N^2)$ graph is the fundamental bottleneck here; the only way to improve the time is to completely change our perspective on what the "nodes" actually are, jumping straight to the Optimal approach.

### Optimal (Disjoint Set Union)

**Intuition:** Instead of treating the stones as nodes (which forces an $O(N^2)$ comparison), we treat the rows and columns as the nodes. A stone at (r, c) simply acts as an edge uniting row r and column c. By using a Disjoint Set Union (DSU), we instantly group these coordinates. As we discussed previously, standard BFS goes wide via a queue and DFS plunges deep via recursion—but DSU elegantly bypasses manual graph traversal entirely, determining components solely through parent pointers!

**Explanation:**

1. Identify the Nodes: Treat rows and columns as distinct entities. To avoid index collisions (since a row can be 0 and a col can also be 0), shift all column indices by maxRow + 1.
2. Setup DSU: Initialize a Disjoint Set structure with enough space to hold maxRow + maxCol + 1 nodes.
3. Unite Coords: Iterate through each stone (r, c). Perform a DSU union operation connecting node r and node c + maxRow + 1.
4. Track Active Nodes: Use a hash map to remember which specific rows and columns actually have stones placed on them.
5. Count Components: Iterate through the hash map. If a node is its own parent (i.e., ds.findUPar(node) == node), it is the ultimate root of a connected component. Tally these up.
6. Result: The maximum stones we can remove is Total Stones - Number of Components.

**Dry Run:** Stones: [[0,0], [0,1], [1,0]]. maxRow is 1. Shift columns by 1 + 1 = 2.

1. Stone 1 [0,0]: Union row 0 and col 2. Component {0, 2}.
2. Stone 2 [0,1]: Union row 0 and col 3. Component {0, 2, 3}.
3. Stone 3 [1,0]: Union row 1 and col 2. Component {1, 0, 2, 3}.
4. Active nodes: 0, 1, 2, 3. They all trace back to the same ultimate parent.
5. Components = 1. Answer = 3 (stones) - 1 = 2.

**Complexity:** **Time:** O(N * α(M)) — where M is the maximum coordinate value, iterating over stones and doing near O(1) union-find operations | **Space:** O(M) — where M is the max matrix dimensions for the parent and size arrays in DSU

**Code:**
```cpp
#include <vector>
#include <unordered_map>
#include <algorithm>

using namespace std;

class DisjointSet {
    vector<int> parent, size;
public:
    DisjointSet(int n) {
        parent.resize(n + 1);
        size.resize(n + 1, 1);
        for (int i = 0; i <= n; i++) {
            parent[i] = i;
        }
    }
    
    int findUPar(int node) {
        if (node == parent[node]) return node;
        return parent[node] = findUPar(parent[node]); // Path compression
    }
    
    void unionBySize(int u, int v) {
        int ulp_u = findUPar(u);
        int ulp_v = findUPar(v);
        
        if (ulp_u == ulp_v) return;
        
        if (size[ulp_u] < size[ulp_v]) {
            parent[ulp_u] = ulp_v;
            size[ulp_v] += size[ulp_u];
        } else {
            parent[ulp_v] = ulp_u;
            size[ulp_u] += size[ulp_v];
        }
    }
};

class Solution {
public:
    int removeStones(vector<vector<int>>& stones) {
        int maxRow = 0;
        int maxCol = 0;
        
        // Find the boundary to shift column indices safely
        for (auto it : stones) {
            maxRow = max(maxRow, it[0]);
            maxCol = max(maxCol, it[1]);
        }
        
        DisjointSet ds(maxRow + maxCol + 1);
        unordered_map<int, int> stoneNodes;
        
        // Connect rows and columns based on stone placements
        for (auto it : stones) {
            int nodeRow = it[0];
            int nodeCol = it[1] + maxRow + 1;
            
            ds.unionBySize(nodeRow, nodeCol);
            
            // Mark these coordinates as containing a stone
            stoneNodes[nodeRow] = 1;
            stoneNodes[nodeCol] = 1;
        }
        
        int components = 0;
        // Count ultimate parents to find number of disjoint sets
        for (auto it : stoneNodes) {
            if (ds.findUPar(it.first) == it.first) {
                components++;
            }
        }
        
        return stones.size() - components;
    }
};
```
