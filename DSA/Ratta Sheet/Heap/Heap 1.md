---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-08-05
---
# Heap

> [!abstract] Standard questions for heaps — memorise the patterns, don't reinvent during interviews.

## K-th Largest Element in an Array (Striver A2Z, heap, medium)

**Problem Statement:** Given an integer array nums and an integer k, return the k-th largest element in the array. Note that it is the k-th largest element in the sorted order, not the k-th distinct element.

### Brute (Sorting)

Intuition: Simply sort the array in descending order and return the element at the k - 1 index.

Complexity: Time: O(N log N) | Space: O(1) (or O(N) depending on the sorting algorithm used under the hood)

### Better (Max-Heap)

Intuition: Insert all N elements of the array into a Max-Heap. Then, extract (pop) the maximum element k - 1 times. The element that remains at the top of the heap will be the k-th largest.

Complexity: Time: O(N + K log N) | Space: O(N)

### Optimal (Min-Heap of size K)

Intuition: Instead of storing all N elements, we can maintain a Min-Heap of strictly size k. As we iterate through the array, we keep adding elements to the heap. If the heap size exceeds k, we drop the smallest element. By the end, the heap holds only the top k largest elements, and the k-th largest will be conveniently sitting at the top.

Explanation:

Data Structure: Initialize a Min-Heap (in C++, this is a `priority_queue` with `greater<int>`).

Iterate & Push: Loop through each number in the array and push it into the Min-Heap.

Maintain Size K: Every time you push an element, check the size of the heap. If heap.size() > k, pop the top element.

Why this works: The top of a Min-Heap is always its smallest element. When the size exceeds k, the smallest element among those k + 1 items cannot possibly be part of the top k largest elements overall. Dropping it ensures we only keep the "candidates" for the largest elements. Specifically, our actions are to remove the smallest number in the k (or k+1) numbers after every iteration because we remove the min of the min heap.

Result: This makes sure that at any iteration, the k elements in the heap are the top k elements seen so far, and out of them our .top() points to the min => the min of the top k elements in the whole array is the kth largest element. After processing the entire array, the Min-Heap contains exactly the k largest elements. Because it's a Min-Heap, the smallest of these k elements—which is exactly the k-th largest overall—is at the top.

Complexity: Time: O(N log K) | Space: O(K)

Coding Implementation:

```cpp
#include <vector>
#include <queue>

using namespace std;

class Solution {
public:
    int findKthLargest(vector<int>& nums, int k) {
        // Min-Heap to store the top k largest elements
        priority_queue<int, vector<int>, greater<int>> minHeap;
        
        for (int num : nums) {
            minHeap.push(num);
            
            // If the heap has more than k elements, pop the smallest one
            if (minHeap.size() > k) {
                minHeap.pop();
            }
        }
        
        // The root of the Min-Heap is the k-th largest element
        return minHeap.top();
    }
};
```

## Find Median from Data Stream (Striver A2Z, heap, hard)

**Problem Statement:** Design a data structure that supports adding integer numbers from a continuous data stream (addNum(int num)) and returning the median of all elements seen so far (findMedian()).

### Brute

Intuition: Store incoming numbers in a dynamic array. Whenever the median is requested, sort the entire array and pick the middle element (or the average of the two middle elements if the length is even).

Complexity: Time: O(1) for addNum, O(N log N) for findMedian | Space: O(N)

### Better (Insertion Sort / Binary Search)

Intuition: Keep the array continually sorted. During addNum, use binary search to find the correct insertion index and insert the element (which shifts subsequent elements). Finding the median is then just an instant index lookup.

Complexity: Time: O(N) for addNum, O(1) for findMedian | Space: O(N)

### Optimal (Two Heaps)

Intuition: Use two heaps—a max-heap for the smaller half of numbers and a min-heap for the larger half—to dynamically maintain the middle elements without ever fully sorting the data stream.

Explanation:

Data Structures: Maintain a maxHeap (to store the lower half of the numbers) and a minHeap (to store the upper half).

Rule 1 (Ordering): Every element in maxHeap must be lesser than or equal to every element in minHeap.

Rule 2 (Balancing): The sizes of the heaps must be balanced. We enforce a rule where maxHeap can either be equal in size to minHeap (if total elements are even) or have exactly 1 more element than minHeap (if total elements are odd).

Insertion (addNum) & Why the 3-Step Dance Works:

Steps 1 & 2 (Maintaining Ordering): When a new number arrives, we don't know if it belongs in the lower half or the upper half. We blindly push it into maxHeap first. To ensure the upper half strictly gets the largest elements, we immediately take the maximum element from maxHeap (maxHeap.top()) and push it to minHeap. Why? If the new number was huge, it bubbles to the top of maxHeap and gets transferred. If it was small, it stays in maxHeap, and some older, larger number gets transferred instead. This guarantees Rule 1 without needing if/else conditions to compare elements.

Step 3 (Maintaining Balance): Because Step 2 unconditionally adds an element to minHeap, minHeap might now have more elements than maxHeap, violating Rule 2. To fix this, if minHeap.size() > maxHeap.size(), we simply pull the smallest element of the upper half (minHeap.top()) back into maxHeap. This ensures maxHeap always has equal or exactly one more element than minHeap.

Finding Median (findMedian):
- If the total number of elements is even (both heaps are the same size), the median is the average of the tops of both heaps.
- If the total number of elements is odd, maxHeap has the extra element, so the median is simply the top of maxHeap.

Complexity: Time: O(log N) for addNum, O(1) for findMedian | Space: O(N)

Coding Implementation:

```cpp
#include <queue>
#include <vector>

using namespace std;

class MedianFinder {
private:
    // Max-heap for the lower half of numbers
    priority_queue<int> maxHeap; 
    
    // Min-heap for the upper half of numbers
    priority_queue<int, vector<int>, greater<int>> minHeap;

public:
    MedianFinder() {
    }
    
    void addNum(int num) {
        // Step 1: Add to maxHeap blindly
        maxHeap.push(num);
        
        // Step 2: Ensure max elements go to upper half (Maintains Rule 1)
        minHeap.push(maxHeap.top());
        maxHeap.pop();
        
        // Step 3: Pull back if minHeap got too large (Maintains Rule 2)
        if (minHeap.size() > maxHeap.size()) {
            maxHeap.push(minHeap.top());
            minHeap.pop();
        }
    }
    
    double findMedian() {
        // If sizes are equal, even number of elements
        if (maxHeap.size() == minHeap.size()) {
            return (maxHeap.top() + minHeap.top()) / 2.0;
        }
        // Otherwise, odd number of elements (maxHeap has the extra element)
        return maxHeap.top();
    }
};
```
