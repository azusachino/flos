//
// @date: 2021-09-26
// @link: https://leetcode.com/problems/kth-largest-element-in-an-array/

#include "algorithm"
#include "vector"
#include "queue"

using namespace std;

class Solution {
public:
    int findKthLargest(vector<int> &nums, int k) {
        nth_element(nums.begin(), nums.begin() + k - 1, nums.end(), greater<int>());
        return nums[k - 1];
    }

    int findKthLargest(vector<int> &nums, int k) {
        partial_sort(nums.begin(), nums.begin() + k, nums.end(), greater<int>());
        return nums[k - 1];
    }

    int findKthLargest(vector<int> &nums, int k) {
        priority_queue<int, vector<int>, greater<int>> pq;
        for (int num: nums) {
            pq.push(num);
            if (pq.size() > k) {
                pq.pop();
            }
        }
        return pq.top();
    }

    int findKthLargest(vector<int> &nums, int k) {
        priority_queue<int> pq(nums.begin(), nums.end());
        for (int i = 0; i < k - 1; i++) {
            pq.pop();
        }
        return pq.top();
    }
};