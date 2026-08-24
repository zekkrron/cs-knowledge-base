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
