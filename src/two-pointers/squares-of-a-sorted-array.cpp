//
// @date: 2021-10-14
// @link: https://leetcode.com/problems/squares-of-a-sorted-array/

#include <vector>
#include <cmath>

using namespace std;

class Solution {
public:
    vector<int> sortedSquares(vector<int> &nums) {
        size_t sz = nums.size();
        vector<int> v(sz);
        int i = 0, j = sz - 1;
        for (auto p = j; p >= 0; --p) {
            if (abs(nums[i]) > abs(nums[j])) {
                v[p] = nums[i] * nums[i];
                i++;
            } else {
                v[p] = nums[j] * nums[j];
                j--;
            }
        }
        return v;
    }
};