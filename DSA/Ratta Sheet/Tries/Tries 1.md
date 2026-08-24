---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-08-05
---
# Tries

> [!abstract] Standard questions for tries — memorise the patterns, don't reinvent during interviews.

## Maximum XOR of Two Numbers in an Array (Striver A2Z, Tries, problems)

**Problem Statement:** Given an integer array nums, return the maximum result of nums[i] XOR nums[j], where 0 \le i \le j < n.

### Brute

Intuition: Iterate through all possible pairs (i, j) in the array using two nested loops, compute their XOR, and keep a running track of the maximum value found.

Complexity: Time: O(N^2) | Space: O(1)

### Better (Bit Manipulation & Hash Set)

Intuition: Build the maximum XOR bit by bit from the most significant bit (MSB) to the least significant bit (LSB). For each bit, assume we can set it to 1 in our max_xor. We use a Hash Set to store the prefixes of all numbers up to the current bit and check if any two prefixes XOR together to give this assumed max_xor (using the property: if A \oplus B = C, then A \oplus C = B).

Explanation:

The Core Mathematical Property: The entire logic rests on the XOR property: If A \oplus B = C, then A \oplus C = B. Here, A and B are prefixes of numbers in our array, and C is the maximum XOR we are hoping to achieve.

Building Bit by Bit: We want to build our answer (max_xor) greedily, starting from the most significant bit (31) down to the least significant bit (0). For each bit, we want to see if we can set it to 1.

Using a Mask: To focus only on the bits from the 31st down to the current i-th bit, we maintain a mask.
- Initially, mask = 0.
- In each iteration i (from 31 down to 0), we update mask = mask | (1 << i).
- For example, at bit 31, the mask is 1000...000. At bit 30, it becomes 1100...000, and so on. Applying num & mask gives us the "prefix" of the number up to the i-th bit.

The Hash Set: For a specific bit i, we iterate through all numbers in the array, apply the mask, and store these prefixes in a Hash Set. This gives us all available prefixes at the current bit level.

The Greedy Guess: We tentatively assume we can turn on the i-th bit of our max_xor. We create a variable greedy_match = max_xor | (1 << i).

Validating the Guess: Now we need to check if any two prefixes in our Hash Set can XOR together to create this greedy_match.
- Using the property A \oplus B = C \implies A \oplus C = B, we say: let C be greedy_match, and let A be a prefix from our set.
- We iterate through every prefix in the set. If prefix ^ greedy_match is also present in the Hash Set (which represents B), it means a valid pair exists!

Updating the Answer: If a valid pair exists, our guess was correct. We permanently update max_xor = greedy_match and move to the next bit. If not, we don't update max_xor, meaning the i-th bit remains 0.

Complexity: Time: O(N \cdot 32) | Space: O(N)

Coding Implementation:

```cpp
#include <vector>
#include <unordered_set>
#include <algorithm>

using namespace std;

class Solution {
public:
    int findMaximumXOR(vector<int>& nums) {
        int max_xor = 0;
        int mask = 0;
        
        // Build the max_xor bit by bit from MSB (31) down to LSB (0)
        for (int i = 31; i >= 0; i--) {
            // mask accumulates the current MSBs. e.g., 100.., 110.., 111..
            mask = mask | (1 << i);
            
            unordered_set<int> prefixes;
            for (int num : nums) {
                // Insert the prefix of each number up to the i-th bit
                prefixes.insert(num & mask);
            }
            
            // Tentatively guess that we can set the i-th bit of max_xor to 1
            int greedy_match = max_xor | (1 << i);
            
            // Verify if two prefixes can XOR to form this greedy_match
            for (int prefix : prefixes) {
                if (prefixes.count(greedy_match ^ prefix)) {
                    max_xor = greedy_match; // Validated! Update max_xor
                    break;
                }
            }
        }
        
        return max_xor;
    }
};
```

### Optimal (Trie)

Intuition: Store the 32-bit binary representation of all numbers in a Trie. For each number, we can find its maximum XOR pair by traversing the Trie and greedily trying to pick the opposite bit at each step (starting from the MSB) to maximize the final XOR result.

Explanation:

Build the Trie: Insert every number into a Trie. The depth of the Trie will be at most 32. The root represents the 31st bit (MSB), and the leaves represent the 0th bit (LSB). Each node has two children representing bit 0 and bit 1.

Greedy Traversal: To maximize the XOR for a given number num, we want to XOR it with a number that has the opposite bits, starting from the most significant bit. (Because 1 \oplus 0 = 1 and 0 \oplus 1 = 1, which gives us a set bit at a higher power of 2).
- For each number in the array, traverse the Trie from the root down to the leaves.
- At the i-th bit of num, check if a node exists for the opposite bit (1 - bit).
- If the opposite bit exists, move to that node and add 1 \ll i (which is 2^i) to the current maximum XOR value for this number.
- If the opposite bit does not exist, we have no choice but to move to the node of the same bit. The XOR for this position will be 0.
- Keep track of the absolute maximum XOR value obtained across all numbers in the array.

Complexity: Time: O(N \cdot 32) | Space: O(N \cdot 32)

Coding Implementation:

```cpp
#include <vector>
#include <algorithm>

using namespace std;

// Trie Node definition
struct Node {
    Node* links[2]; // links[0] for bit 0, links[1] for bit 1
    
    bool containsKey(int bit) {
        return (links[bit] != nullptr);
    }
    
    Node* get(int bit) {
        return links[bit];
    }
    
    void put(int bit, Node* node) {
        links[bit] = node;
    }
};

// Trie class to handle binary insertions and max XOR queries
class Trie {
private:
    Node* root;
    
public:
    Trie() {
        root = new Node();
    }
    
    // Insert a number's binary representation into the Trie
    void insert(int num) {
        Node* node = root;
        // Start from the most significant bit (31st bit) down to 0
        for (int i = 31; i >= 0; i--) {
            int bit = (num >> i) & 1;
            if (!node->containsKey(bit)) {
                node->put(bit, new Node());
            }
            node = node->get(bit);
        }
    }
    
    // Find the maximum XOR for a given number using the Trie
    int getMaxXor(int num) {
        Node* node = root;
        int maxNum = 0;
        
        for (int i = 31; i >= 0; i--) {
            int bit = (num >> i) & 1;
            
            // Greedily try to find the opposite bit to maximize XOR
            if (node->containsKey(1 - bit)) {
                // Opposite bit found: set the i-th bit to 1 in the result
                maxNum = maxNum | (1 << i);
                node = node->get(1 - bit);
            } else {
                // Opposite bit not found: have to take the same bit (XOR is 0)
                node = node->get(bit);
            }
        }
        return maxNum;
    }
};

class Solution {
public:
    int findMaximumXOR(vector<int>& nums) {
        Trie trie;
        
        // Step 1: Insert all numbers into the Trie
        for (int num : nums) {
            trie.insert(num);
        }
        
        int maxi = 0;
        
        // Step 2: Find the maximum XOR for each number
        for (int num : nums) {
            maxi = max(maxi, trie.getMaxXor(num));
        }
        
        return maxi;
    }
};
```
