---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-08-05
---
# Sliding Window

> [!abstract] Standard questions for sliding window / two pointer — memorise the patterns, don't reinvent during interviews.

## Minimum Window Substring (Striver A2Z, Sliding, Hard)

**Problem Statement:** Given two strings s and t, return the minimum window substring of s such that every character in t (including duplicates) is included in the window. If there is no such substring, return the empty string "".

### Brute

Intuition: Generate every possible substring of s. For each substring, count its characters and check if it contains all the characters required by t. Keep track of the minimum length among all valid substrings.

Complexity: Time: O(N^3) | Space: O(1)

### Better

Intuition: Instead of checking all substrings from scratch, fix the starting point i and expand a right pointer j. Increment character counts dynamically as j moves, stopping as soon as the substring from i to j satisfies the condition, recording its length, and then moving i to the next starting position.

Complexity: Time: O(N^2) | Space: O(1)

### Optimal (Sliding Window using standard Template)

Intuition: Use the standard sliding window template with head and tail pointers. We expand the head to "eat" elements as long as our window is invalid (missing required characters). Once valid, we record the minimum window size, and then shrink by moving the tail forward to check for smaller valid windows.

Explanation:

Frequency Map & Required Count: We map the characters of string t to a frequency array. We keep a required integer representing the total count of characters we still need to match.

Expand the Window (Inner While): Unlike max-window problems that expand while valid, here we expand while invalid (required > 0). We keep eating elements until the window is finally satisfied. Because the loop breaks the exact moment the required condition is met, head rests precisely on the final boundary element that made the window valid. This is why the length formula remains exactly head - tail + 1 without needing any offsets.
- We increment head and insert s[head] into our current window data structure.
- If map[s[head]] > 0, it means we just swallowed a character we actually need, so we decrement required.
- We unconditionally decrement map[s[head]].

Update Answer: Once the inner loop breaks, we check if required == 0 (which confirms the window broke because it became valid, not because it reached the end of the string). If valid, we update minLen and startIndex.

Shrink the Window (Else block): We move the tail pointer forward.
- To remove s[tail] from our window, we increment its value in the map.
- If its value becomes > 0, it means we just lost a character that was crucial for string t, so we must increment required back up.
- Increment tail and repeat the entire process.

Complexity: Time: O(N + M) | Space: O(1) (Array size is fixed to 256)

Coding Implementation:

```cpp
#include <string>
#include <vector>
#include <climits>

using namespace std;

class Solution {
public:
    string minWindow(string s, string t) {
        if (s.empty() || t.empty()) return "";
        
        vector<int> map(256, 0);
        for (char c : t) {
            map[c]++;
        }
        
        int n = s.length();
        int tail = 0, head = -1;
        int required = t.size();
        
        int minLen = INT_MAX;
        int startIndex = -1;
        
        // for every start
        while (tail < n) {
            
            // eat as many elements as possible till its valid (in this case, until required == 0)
            while (head + 1 < n && required > 0) {
                head++;
                // insert ds(head)
                if (map[s[head]] > 0) {
                    required--;
                }
                map[s[head]]--;
            }
            
            // update the answer for current start
            if (required == 0) {
                if (head - tail + 1 < minLen) {
                    minLen = head - tail + 1;
                    startIndex = tail;
                }
            }
            
            // move start one step forward
            if (tail > head) {
                tail++;
                head = tail - 1;
            } else {
                // erase from ds(tail)
                map[s[tail]]++;
                if (map[s[tail]] > 0) {
                    required++;
                }
                tail++;
            }
        }
        
        return startIndex == -1 ? "" : s.substr(startIndex, minLen);
    }
};
```
