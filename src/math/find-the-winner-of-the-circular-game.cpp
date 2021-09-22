//
// @date: 2021-09-22
// @link: https://leetcode.com/problems/find-the-winner-of-the-circular-game/

class Solution {
public:
    static int findTheWinner(int n, int k) {
        int res = 0;
        for (auto i = 1; i <= n; ++i) {
            res = (res + k) % i;
        }
        return res + 1;
    }
};