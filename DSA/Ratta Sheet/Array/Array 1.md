---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-08-05
---
# Array

> [!abstract] Standard questions for arrays — memorise the patterns, don't reinvent during interviews.

## Sort arrays of 0, 1, 2s (Striver A2Z, Array, Medium)

**Problem Statement:** Given an array `nums` with n objects colored red, white, or blue, sort them in-place so that objects of the same color are adjacent, with the colors in the order red, white, and blue. We will use the integers 0, 1, and 2 to represent the color red, white, and blue, respectively.

### Brute (Sorting)

The most straightforward way to group identical elements and put them in ascending order is to use a standard sorting algorithm. We can simply use the built-in sorting function provided by the language standard library. Under the hood, this usually implements a variation of Quick Sort, Merge Sort, or IntroSort.

**Time:** O(N log N) | **Space:** O(log N) or O(N) (depending on the internal implementation of the language's sorting function)

```cpp
#include <vector>
#include <algorithm>

using namespace std;

class Solution {
public:
    void sortColors(vector<int>& nums) {
        // Use standard library sort
        sort(nums.begin(), nums.end());
    }
};
```

### Better (Counting Sort / Two Passes)

Since we only have three distinct, known values (0, 1, and 2), sorting by comparing elements is overkill. Instead, we can just count how many times each number appears and overwrite the array.

- First Pass: Traverse the array and maintain three integer counters: `count0`, `count1`, and `count2`. Increment the respective counter based on the value encountered.
- Second Pass: Iterate through the array again. Overwrite the first `count0` indices with 0. Then overwrite the next `count1` indices with 1. Finally, fill the remaining `count2` indices with 2.

**Time:** O(N) (strictly two passes, so O(2N) which simplifies to O(N)) | **Space:** O(1) (only three integer variables used)

```cpp
#include <vector>

using namespace std;

class Solution {
public:
    void sortColors(vector<int>& nums) {
        int count0 = 0, count1 = 0, count2 = 0;
        
        // Step 1: Count the frequencies
        for (int num : nums) {
            if (num == 0) count0++;
            else if (num == 1) count1++;
            else count2++;
        }
        
        // Step 2: Overwrite the array based on counts
        int index = 0;
        for (int i = 0; i < count0; i++) nums[index++] = 0;
        for (int i = 0; i < count1; i++) nums[index++] = 1;
        for (int i = 0; i < count2; i++) nums[index++] = 2;
    }
};
```

### Optimal (Dutch National Flag Algorithm / One Pass)

We can sort the array in a single pass using three pointers. By maintaining boundaries for the 0s (at the beginning) and the 2s (at the end), we can dynamically swap elements into their correct zones as we iterate through the array.

Pointers: Initialize three pointers:
- `low = 0`: Represents the boundary for 0s. Everything to the left of `low` is 0.
- `mid = 0`: The current element we are evaluating.
- `high = n - 1`: Represents the boundary for 2s. Everything to the right of `high` is 2.

Traversal: Run a loop while `mid <= high`.

- Case 0 (`nums[mid] == 0`): We need to push 0 to the left zone. Swap `nums[low]` and `nums[mid]`. Then increment both `low` and `mid`. Why is it safe to increment `mid`? Because `mid` acts as our scout and only gets ahead of `low` by skipping over 1s (as seen in Case 1). This means every element sitting between `low` and `mid` has already been confirmed to be a 1. When we swap, the element that gets pulled from `low` and lands at `mid` is strictly guaranteed to be a 1 (or a 0 if `low == mid`). Since it is already in its correct state, it requires no further evaluation.
- Case 1 (`nums[mid] == 1`): The element is already in the correct middle zone. Just increment `mid` to evaluate the next element.
- Case 2 (`nums[mid] == 2`): We need to push 2 to the right zone. Swap `nums[mid]` and `nums[high]`. Decrement `high`. Crucially, do not increment `mid`. The element we just swapped from `high` to `mid` has not been evaluated yet (it could be a 0, 1, or 2), so we must check it in the next iteration.

**Time:** O(N) (strictly a single pass) | **Space:** O(1)

```cpp
#include <vector>
#include <algorithm>

using namespace std;

class Solution {
public:
    void sortColors(vector<int>& nums) {
        int low = 0;
        int mid = 0;
        int high = nums.size() - 1;
        
        while (mid <= high) {
            if (nums[mid] == 0) {
                // Swap mid with low, expand the 0 zone, and move forward
                swap(nums[low], nums[mid]);
                low++;
                mid++;
            } 
            else if (nums[mid] == 1) {
                // Already in the correct middle zone, just move forward
                mid++;
            } 
            else { // nums[mid] == 2
                // Swap mid with high, expand the 2 zone
                swap(nums[mid], nums[high]);
                high--;
                // Note: mid is NOT incremented here because the swapped element needs evaluation
            }
        }
    }
};
```

## Majority element (>n/2 times) (Striver A2Z, Array, Medium)

**Problem Statement:** Given an array `nums` of size n, return the majority element. The majority element is the element that appears more than ⌊n / 2⌋ times. You may assume that the majority element always exists in the array.

### Brute (Nested Loops)

For every element in the array, we can iterate through the entire array again to count how many times it appears. If we find an element whose count is strictly greater than N/2, it is our majority element.

**Time:** O(N²) (nested loops to count the frequency of each element) | **Space:** O(1) (only a few variables for counting)

### Better (Hash Map)

Instead of repeatedly traversing the array, we can traverse it just once and keep track of the frequencies of all elements using a Hash Map. As we populate the map, if any element's frequency exceeds N/2, we immediately return it.

**Time:** O(N) average (using an unordered map for frequency counting) | **Space:** O(N) (in the worst case to store the unique elements and their frequencies)

### Optimal (Moore's Voting Algorithm)

If an element occurs more than N/2 times, its frequency is greater than the combined frequency of all other elements in the array. Moore's Voting Algorithm leverages this fact by essentially pairing up distinct elements and cancelling them out. The majority element will always survive this cancellation process.

- Initialize two variables: `count = 0` and `candidate = 0`.
- Iterate through the array one element at a time.
- If `count` is 0, we assume the current element is our new `candidate` and reset `count` to 1.
- If the current element is the same as the `candidate`, we increment `count` (voting for the candidate).
- If the current element is different from the `candidate`, we decrement `count` (cancelling a vote).

Mathematical Proof: Whenever `count` hits 0, it means we have just processed a sub-segment (a prefix) of the array where the elements completely cancelled each other out. Let's say this cancelled prefix has a length of 2K. In this prefix, exactly K elements were the candidate, and K elements were something else.

In the absolute worst-case scenario, the true majority element was involved in this prefix and got cancelled out. Since at most half of the elements in this prefix can be the true majority element, we discarded at most K instances of it.

If the majority element originally appeared M times (where M > N/2), in the remaining suffix of the array (which is of size N - 2K), it must appear at least M - K times.

Is M - K still a majority in the remaining array? Yes! Since M > N/2:
- M - K > (N/2) - K
- M - K > (N - 2K) / 2

Because the majority element retains its > 50% density in every remaining suffix after a reset, there must inevitably be a final, uninterrupted segment of the array. Once the true majority element becomes the candidate in this final stretch, there simply aren't enough rival elements left in the entire rest of the array to drive its count back down to zero.

The problem guarantees a majority element exists, so we directly return the candidate.

**Time:** O(N) (we traverse the array only once) | **Space:** O(1) (only two variables)

```cpp
#include <vector>

using namespace std;

class Solution {
public:
    int majorityElement(vector<int>& nums) {
        int count = 0;
        int candidate = 0;
        
        for (int i = 0; i < nums.size(); i++) {
            if (count == 0) {
                candidate = nums[i];
                count = 1;
            } else if (nums[i] == candidate) {
                count++;
            } else {
                count--;
            }
        }
        
        return candidate;
    }
};
```

## Move zeroes to end (Striver A2Z, Array, Easy)

**Problem Statement:** Given an integer array `nums`, move all 0s to the end of it while maintaining the relative order of the non-zero elements. You must do this in-place without making a copy of the array (for the optimal approaches).

### Brute (Using a Temporary Array)

We can traverse the array and extract all the non-zero elements, storing them in a temporary array. Once we have all the non-zero elements, we overwrite the beginning of the original array with them. Finally, we fill the remaining positions at the end of the original array with zeros.

**Time:** O(N) (traverse the array twice: once to copy non-zeros, once to rewrite the original array) | **Space:** O(N) (for the temporary array)

### Better (Two-Pass In-Place)

We can avoid the O(N) extra space by doing a two-pass operation directly on the array. In the first pass, we maintain an insertion index (starting at 0) and whenever we see a non-zero element, we place it at this insertion index and increment it. In the second pass, we simply iterate from the final insertion index to the end of the array, filling all remaining spots with zeros.

**Time:** O(N) (two passes over the array) | **Space:** O(1) (modified in-place)

### Optimal (Single-Pass Two Pointers / Swapping)

We can optimize the two-pass approach into a single pass by using the Two Pointers technique. By keeping a pointer that always points to the first available zero, we can simply swap non-zero elements with that zero as soon as we find them. This naturally pushes all the zeros to the end like a snowball.

- Initialize a pointer `j = 0`. This pointer will keep track of the index where the next non-zero element should be placed.
- Iterate through the array with a second pointer `i` from 0 to N - 1.
- The Lockstep Phase: If there are no zeros initially, `i` and `j` move together. `nums[i]` swaps with itself (doing nothing), and both pointers increment.
- The Zero Gap Phase: When `i` encounters a zero, it skips it, but `j` does not increment. `j` is now stuck pointing to the first zero. The gap between `i` and `j` precisely represents the block of zeros encountered so far.
- The Swap: When `i` eventually finds a non-zero element, it swaps `nums[i]` with `nums[j]`. Because `j` is pointing to the start of the zero-block and `i` is at the end, this swap effectively pulls the non-zero number to the front of the block and pushes a zero to the back.
- After swapping, increment `j` by 1 so it points to the new "first available zero."

**Time:** O(N) (we only traverse the array exactly once) | **Space:** O(1) (modified strictly in-place)

```cpp
#include <vector>
#include <algorithm>

using namespace std;

class Solution {
public:
    void moveZeroes(vector<int>& nums) {
        int j = 0; // Pointer for the next non-zero insertion
        
        for (int i = 0; i < nums.size(); i++) {
            if (nums[i] != 0) {
                swap(nums[i], nums[j]);
                j++;
            }
        }
    }
};
```

## Left rotate by D places (Striver A2Z, Array, Easy)

**Problem Statement:** Given an integer array `nums` and an integer d, left rotate the array by d steps.

### Brute (Step-by-step Shifting)

We can left rotate the array by one position by saving the first element, shifting all other elements one position to the left, and placing the saved element at the end. We repeat this single-shift process exactly D times.

**Time:** O(N · D) (where N is array size, as a single shift takes O(N) and we do it D times) | **Space:** O(1) (modified in-place)

### Better (Using Temporary Array)

Instead of shifting one by one, we can extract the first D elements and temporarily store them in a separate array. Then, we shift the remaining N-D elements to the front of the original array, and finally append the stored D elements to the back. (Note: We must compute D = D mod N first to handle cases where D is greater than the array size).

**Time:** O(N) (one pass to copy to temp, one pass to shift, one pass to put back) | **Space:** O(D) (for the temporary array)

### Optimal (Reversal Algorithm)

We can achieve this in O(N) time and O(1) extra space by cleverly reversing segments of the array. Left rotating by D places essentially means the first D elements move to the back, and the remaining elements move to the front. We can achieve this exact rearrangement without extra space by reversing the two parts individually, and then reversing the entire array.

- Modulo Operation: First, take the modulo of D with N (D = D mod N). Rotating an array of size N by N times results in the exact same array, so this removes redundant rotations. If D becomes 0, we can just return.
- Reverse First Part: Reverse the first segment of the array from index 0 to D - 1.
- Reverse Second Part: Reverse the remaining segment of the array from index D to N - 1.
- Reverse All: Finally, reverse the entire array from index 0 to N - 1.

Note on Left vs. Right Rotation: For a left rotation by D places, we target the first D elements (the left end) as our initial segment to reverse. Conversely, if the problem asked for a right rotation by D places, we would target the last D elements (the right end) to reverse first, followed by reversing the remaining prefix.

Example trace on [1, 2, 3, 4, 5] with D = 2 (Left Rotate):
- Reverse 0 to 1: [2, 1, 3, 4, 5]
- Reverse 2 to 4: [2, 1, 5, 4, 3]
- Reverse All: [3, 4, 5, 1, 2] (Correct!)

**Time:** O(N) (each element is swapped exactly twice, making it a linear time operation) | **Space:** O(1) (modified strictly in-place)

```cpp
#include <vector>
#include <algorithm>

using namespace std;

class Solution {
public:
    void rotateLeft(vector<int>& nums, int d) {
        int n = nums.size();
        if (n == 0) return;
        
        // Step 1: Remove redundant rotations
        d = d % n; 
        if (d == 0) return;
        
        // Step 2: Reverse the first d elements
        reverse(nums.begin(), nums.begin() + d);
        
        // Step 3: Reverse the remaining n - d elements
        reverse(nums.begin() + d, nums.end());
        
        // Step 4: Reverse the entire array
        reverse(nums.begin(), nums.end());
    }
};
```
