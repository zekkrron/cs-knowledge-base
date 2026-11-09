---
tags: [dsa/ratta-sheet, status/draft]
created: 2026-10-27
---
# Binary Search

> [!abstract] Standard questions for binary search — memorise the patterns, don't reinvent during interviews.

## Minimize Max distance to Gas Station (Striver A2Z, Binary Search, BS on Answers)

**Problem Statement:** You are given a sorted integer array `stations` representing the positions of gas stations on an X-axis, and an integer `k`. You need to add `k` new gas stations anywhere on the X-axis to minimize the maximum distance between any two adjacent gas stations. Return this minimum possible maximum distance.

### Brute (Linear Search for Max Gap)

**Intuition:** The most straightforward way to minimize the maximum gap is to place one station at a time into the currently largest gap. If we repeat this `k` times, we will greedily reduce the largest distances.

**Explanation:**

1. We maintain a `howMany` array which counts the number of insertions (new stations) made into each of the original gaps.
2. We do not need to calculate or track the exact coordinates of the newly placed stations. We only need to store and evaluate the gap value, since we assume that we will be spreading the new stations evenly. Thus, each sub-gap within an original gap would be of the same value: `original_gap / (howMany[i] + 1)`.
3. For each of the `k` stations, we linearly scan all gaps, calculate their current sub-gap sizes, find the maximum one, and place a new station there by incrementing its count in `howMany`.

**Complexity:** **Time:** `O(k * n)` — For each of the `k` stations, we scan the `n-1` gaps to find the largest one. | **Space:** `O(n)` — We need an array to keep track of how many stations are placed in each gap.

**Code:**

```cpp
#include <vector>
#include <algorithm>

using namespace std;

class Solution {
public:
    double minmaxGasDist(vector<int>& stations, int k) {
        int n = stations.size();
        vector<int> howMany(n - 1, 0);
        
        for (int i = 0; i < k; ++i) {
            double maxSpace = -1.0;
            int maxIdx = -1;
            
            for (int j = 0; j < n - 1; ++j) {
                double diff = stations[j + 1] - stations[j];
                double space = diff / (double)(howMany[j] + 1);
                if (space > maxSpace) {
                    maxSpace = space;
                    maxIdx = j;
                }
            }
            howMany[maxIdx]++;
        }
        
        double maxAns = -1.0;
        for (int j = 0; j < n - 1; ++j) {
            double diff = stations[j + 1] - stations[j];
            double space = diff / (double)(howMany[j] + 1);
            maxAns = max(maxAns, space);
        }
        
        return maxAns;
    }
};
```

### Better (Priority Queue / Max Heap)

**Intuition:** Instead of linearly scanning all gaps for every single one of the `k` stations, we can maintain the gaps in a Max Heap. This allows us to fetch the largest gap in logarithmic time rather than linear time.

**Complexity:** **Time:** `O(n log n + k log n)` — Building the heap takes `O(n log n)`, and placing `k` stations takes `O(k log n)`. | **Space:** `O(n)` — For storing the gaps and frequencies in the Priority Queue.

**Code:**

```cpp
#include <vector>
#include <queue>
#include <algorithm>

using namespace std;

class Solution {
public:
    double minmaxGasDist(vector<int>& stations, int k) {
        int n = stations.size();
        // Stores {current_gap_distance, original_gap_index}
        priority_queue<pair<double, int>> pq;
        vector<int> howMany(n - 1, 0);
        
        for (int i = 0; i < n - 1; ++i) {
            pq.push({(double)(stations[i + 1] - stations[i]), i});
        }
        
        for (int i = 0; i < k; ++i) {
            auto top = pq.top();
            pq.pop();
            int idx = top.second;
            
            howMany[idx]++;
            double diff = stations[idx + 1] - stations[idx];
            double newSpace = diff / (double)(howMany[idx] + 1);
            
            pq.push({newSpace, idx});
        }
        
        return pq.top().first;
    }
};
```

### Optimal (Binary Search on Answer)

**Intuition:** If a specific maximum distance `mid` is achievable by adding `<= k` stations, then any distance greater than `mid` is obviously achievable. This creates a monotonic boolean search space (False False True True), which perfectly signals Binary Search on Answer.

**Explanation:**

1. Define the search space: The minimum possible distance is `lo = 0.0`, and the maximum is `hi = stations.back() - stations.front()`.
2. Instead of `while (hi - lo > 1e-6)`, we use a fixed iteration loop (`for (int iter = 0; iter < 100; iter++)`). This mathematically limits complexity, avoids IEEE floating-point precision TLEs, and achieves sufficient precision ($2^{-100}$).
3. In our `check(mid)` predicate, we calculate required insertions per gap using `(int)(dist / mid)`.
4. **Crucial detail for Floats:** In integer Binary Search, calculating cuts strictly requires the ceiling formula `(dist - 1) / target`. We use `(int)(dist / mid)` here because, even though it overestimates by 1 on exact multiples (e.g., `10.0 / 5.0` gives 2 instead of 1), the continuous float search space auto-corrects this. The search will simply step to a microscopically larger `mid` (e.g., `5.000000001`), changing the fraction to `1.99999` which accurately truncates to 1.
5. If `required_stations <= k`, it means `mid` is a valid answer, so we shrink the right half (`hi = mid`). Otherwise, `mid` is too small, so we shrink the left half (`lo = mid`).

**Dry Run on `stations = [1, 5, 9]`, `k = 2`:**

- Initial Gaps: 4, 4.
- BS tests `mid = 2.0`. Insertions = `(int)(4/2) + (int)(4/2) = 2 + 2 = 4`.
- Since 4 > k (2), `lo = 2.0`.
- Later BS tests `mid = 2.00001`. Insertions = `(int)(4/2.00001) + (int)(4/2.00001) = 1 + 1 = 2`.
- Since 2 <= k, `hi = 2.00001`.
- It seamlessly slides over the exact boundary!

**Complexity:** **Time:** `O(n * 100)` — The binary search runs a fixed 100 times, and the check function runs in `O(n)`. | **Space:** `O(1)` — Only a few floating-point variables are maintained.

**Code:**

```cpp
#include <vector>
#include <algorithm>

using namespace std;

class Solution {
private:
    // Helper to check if a given max distance 'mid' is possible with <= k stations
    bool check(double mid, const vector<int>& stations, int k) {
        int required = 0;
        for (size_t i = 1; i < stations.size(); ++i) {
            double dist = stations[i] - stations[i - 1];
            // Simple truncation is sufficient due to float BS continuous adjustment
            required += (int)(dist / mid); 
        }
        return required <= k;
    }

public:
    double minmaxGasDist(vector<int>& stations, int k) {
        double lo = 0.0;
        double hi = stations.back() - stations.front();
        
        // 100 iterations dodge all floating-point precision traps (Vivek Gupta Template)
        for (int iter = 0; iter < 100; ++iter) {
            double mid = lo + (hi - lo) / 2.0;
            
            if (check(mid, stations, k)) {
                // If possible, try looking for a strictly smaller max distance
                hi = mid;
            } else {
                // If not possible, the max distance needs to be larger
                lo = mid;
            }
        }
        
        return hi;
    }
};
```
