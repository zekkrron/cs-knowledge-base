---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-08-05
---
# Stack and Queue

> [!abstract] Standard questions for stacks and queues — memorise the patterns, don't reinvent during interviews.

## Largest Rectangle in a Histogram (Striver A2Z, Stack n Queue, Monotonic)

**Problem Statement:** Given an array of integers `heights` representing a histogram where the width of each bar is 1, return the area of the largest rectangle that can be formed within the bounds of the histogram.

### Brute

For every bar, treat it as the minimum height. Expand left and right linearly until you hit a bar strictly smaller. Calculate area = height × width. Track the max.

**Time:** O(N²) | **Space:** O(1)

### Better (Two-Pass Stack)

Use a monotonic stack to precompute two arrays:
- `leftSmall[i]` — index of Previous Smaller Element
- `rightSmall[i]` — index of Next Smaller Element

Then one final pass: `area = heights[i] * (rightSmall[i] - leftSmall[i] - 1)`.

```cpp
int n = heights.size();
vector<int> leftSmall(n, -1);
vector<int> rightSmall(n, n);
stack<int> st;

// 1. Find Previous Smaller Element (leftSmall)
for (int i = 0; i < n; i++) {
    while (!st.empty() && heights[st.top()] >= heights[i]) {
        st.pop();
    }
    if (!st.empty()) leftSmall[i] = st.top();
    st.push(i);
}

while (!st.empty()) st.pop();

// 2. Find Next Smaller Element (rightSmall)
for (int i = n - 1; i >= 0; i--) {
    while (!st.empty() && heights[st.top()] >= heights[i]) {
        st.pop();
    }
    if (!st.empty()) rightSmall[i] = st.top();
    st.push(i);
}

// 3. Calculate Max Area
// maxArea = max(maxArea, heights[i] * (rightSmall[i] - leftSmall[i] - 1));
```

**Time:** O(N) | **Space:** O(N)

### Optimal (One-Pass Stack)

Combine  both passes into one. Maintain a stack of indices representing a strictly increasing sequence of heights.

- If current bar is shorter than stack top → current bar is the **NSE** for the top.
- Pop the top. After popping, the new stack top is the **PSE** for the popped element.
- Width = `i - stack.top() - 1` (or just `i` if stack is empty, meaning it extends to the start).
- Area = height × width. Update max.
- After the loop, flush remaining stack elements (their NSE is effectively index N).

```cpp
int largestRectangleArea(vector<int>& heights) {
    int n = heights.size();
    stack<int> st;
    int maxArea = 0;

    for (int i = 0; i <= n; i++) {
        int currentHeight = (i == n) ? 0 : heights[i];

        while (!st.empty() && currentHeight < heights[st.top()]) {
            int height = heights[st.top()];
            st.pop();
            int width = st.empty() ? i : i - st.top() - 1;
            maxArea = max(maxArea, height * width);
        }
        st.push(i);
    }
    return maxArea;
}
```

**Time:** O(N) | **Space:** O(N)

## Trapping Rainwater (Striver A2Z, Stack n Queue, Monotonic)

**Problem Statement:** Given an array of n non-negative integers `height` representing an elevation map where the width of each bar is 1, compute how much water it can trap after raining.

### Brute

Water trapped above any bar `i` is `min(leftMax, rightMax) - height[i]`. For every index, run a loop left to find `leftMax` and a loop right to find `rightMax`, then compute water.

**Time:** O(N²) | **Space:** O(1)

### Better (Prefix/Suffix Arrays)

Precompute `leftMax[]` and `rightMax[]` in two O(N) passes. Then a third pass computes `min(leftMax[i], rightMax[i]) - height[i]` for each index in O(1).

**Time:** O(N) | **Space:** O(N)

### Optimal (Two Pointers)

Track `leftMax` and `rightMax` dynamically with two pointers from both ends — eliminates the O(N) arrays. Always move the pointer at the smaller height.

**Why this works — the "stuck pointer" proof:**
- If `height[left] <= height[right]`: evaluate the left side. Since we always move the smaller side, `leftMax` is guaranteed to be ≤ the true right-side maximum — it can never be greater, because if it were, the left pointer would still be stuck on that peak while the right pointer moved instead. So `leftMax` is the real bottleneck here.
  - If it's a new peak (`height[left] >= leftMax`), update `leftMax`, no water trapped.
  - Otherwise, water trapped = `leftMax - height[left]`.
- If `height[left] > height[right]`: symmetric case. `rightMax` is guaranteed ≤ the true left maximum, so it's the bottleneck.
  - If new peak, update `rightMax`.
  - Otherwise, water trapped = `rightMax - height[right]`.

```cpp
int trap(vector<int>& height) {
    int n = height.size();
    if (n == 0) return 0;

    int left = 0, right = n - 1;
    int leftMax = 0, rightMax = 0;
    int trappedWater = 0;

    while (left < right) {
        if (height[left] <= height[right]) {
            if (height[left] >= leftMax) {
                leftMax = height[left];
            } else {
                trappedWater += leftMax - height[left];
            }
            left++;
        } else {
            if (height[right] >= rightMax) {
                rightMax = height[right];
            } else {
                trappedWater += rightMax - height[right];
            }
            right--;
        }
    }
    return trappedWater;
}
```

**Time:** O(N) | **Space:** O(1)
