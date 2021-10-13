//
// @date: 2021-10-13
// @link: https://leetcode.com/problems/first-bad-version/

class Solution {
public:
    int firstBadVersion(int n) {
        int lower = 1, upper = n, mid;
        while (lower < upper) {
            mid = lower + (upper - lower) / 2;
            if (!isBadVersion(mid)) lower = mid + 1;   /* Only one call to API */
            else upper = mid;
        }
        return lower;   /* Because there will always be a bad version, return lower here */
    }
};