---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-10-27
---
# Backtracking

> [!abstract] Standard questions for backtracking — memorise the patterns, don't reinvent during interviews.

## Word Search (Striver A2Z, Recursion, Backtracking or Hard)

**Problem Statement:** Given an `m x n` grid of characters `board` and a string `word`, return true if `word` exists in the grid. The word can be constructed from letters of sequentially adjacent cells, where adjacent cells are horizontally or vertically neighboring. The same letter cell may not be used more than once.

### Brute (DFS Backtracking with Extra Space)

**Intuition:** We can search for the word by starting a Depth-First Search (DFS) from any cell that matches the first letter. To ensure we do not reuse the same cell in a single path, we maintain a separate 2D `visited` matrix to track our trail.

**Complexity:** **Time:** `O(m * n * 3^L)` — For each of the `m * n` cells, we explore up to 3 valid directions (excluding the backward path) for a word of length `L`. | **Space:** `O(m * n + L)` — The `visited` matrix takes `O(m * n)` space and the recursion stack takes `O(L)`.

**Code:**

```cpp
#include <iostream>
#include <vector>
#include <string>

using namespace std;

class Solution {
private:
    bool dfs(vector<vector<char>>& board, string& word, int i, int j, int index, vector<vector<bool>>& visited) {
        if (index == word.size()) return true;
        
        if (i < 0 || i >= board.size() || j < 0 || j >= board[0].size() || visited[i][j] || board[i][j] != word[index]) {
            return false;
        }
        
        visited[i][j] = true;
        
        bool found = dfs(board, word, i + 1, j, index + 1, visited) ||
                     dfs(board, word, i - 1, j, index + 1, visited) ||
                     dfs(board, word, i, j + 1, index + 1, visited) ||
                     dfs(board, word, i, j - 1, index + 1, visited);
                     
        visited[i][j] = false;
        
        return found;
    }

public:
    bool exist(vector<vector<char>>& board, string word) {
        int m = board.size();
        int n = board[0].size();
        vector<vector<bool>> visited(m, vector<bool>(n, false));
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (board[i][j] == word[0] && dfs(board, word, i, j, 0, visited)) {
                    return true;
                }
            }
        }
        return false;
    }
};
```

### Better

Same as Optimal / not applicable. The brute force DFS approach correctly explores the solution space, but it can be strictly optimized in terms of space by modifying the board instead of using a separate `visited` matrix.

### Optimal (DFS Backtracking In-Place)

**Intuition:** Instead of using an extra `O(m * n)` matrix to keep track of visited cells, we can modify the board itself. By temporarily changing the current cell's character to a special symbol like `'#'`, we prevent revisiting it, and we backtrack by restoring the original character afterward.

**Explanation:**

1. Iterate over every cell `(i, j)` in the board to find a starting point that matches `word[0]`.
2. Once a match is found, trigger the recursive `dfs` function to search for the rest of the string.
3. Base cases for DFS: If `index == word.length()`, we successfully found all characters (return true). If out of bounds or `board[i][j] != word[index]`, return false.
4. Store the current character in a temporary variable and overwrite `board[i][j]` with `'#'` to mark it as visited (an important trick discussed to avoid re-traversing the same cell in the current path).
5. Recursively call `dfs` on the 4 adjacent neighbors (up, down, left, right) looking for the next character at `index + 1`.
6. **Backtracking Step:** Restore the original character to `board[i][j]` from the temporary variable so that other starting points or alternative paths can use this cell.

**Dry Run on small example:** Let `board = [['A','B'],['C','D']]`, `word = "AB"`.

- Start at `(0, 0)` -> `'A'`. Target `'B'` at index 1.
- Mark `board[0][0] = '#'`.
- Check neighbors: down is `'C'` (fails), right is `(0, 1)` -> `'B'`.
- Matches target, index becomes 2, which equals `word.size()`, returning true.

**Complexity:** **Time:** `O(m * n * 3^L)` — Iterate through grid `O(m * n)`, and for each, the DFS tree branches 3 ways up to depth `L`. | **Space:** `O(L)` — No extra matrix is used; auxiliary space is strictly bounded by the maximum recursion depth `L`.

**Code:**

```cpp
#include <iostream>
#include <vector>
#include <string>

using namespace std;

class Solution {
private:
    bool dfs(vector<vector<char>>& board, string& word, int i, int j, int index) {
        // All characters matched successfully
        if (index == word.size()) {
            return true;
        }
        
        // Check bounds and if the current character matches the word's required character
        if (i < 0 || i >= board.size() || j < 0 || j >= board[0].size() || board[i][j] != word[index]) {
            return false;
        }
        
        // Temporarily store the character and mark the cell as visited in-place
        char temp = board[i][j];
        board[i][j] = '#';
        
        // Explore all 4 adjacent directions
        bool found = dfs(board, word, i + 1, j, index + 1) ||
                     dfs(board, word, i - 1, j, index + 1) ||
                     dfs(board, word, i, j + 1, index + 1) ||
                     dfs(board, word, i, j - 1, index + 1);
                     
        // Backtrack: Restore the cell's original character
        board[i][j] = temp;
        
        return found;
    }

public:
    bool exist(vector<vector<char>>& board, string word) {
        int m = board.size();
        int n = board[0].size();
        
        // Try starting the DFS from every cell
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (board[i][j] == word[0] && dfs(board, word, i, j, 0)) {
                    return true;
                }
            }
        }
        
        return false;
    }
};
```
