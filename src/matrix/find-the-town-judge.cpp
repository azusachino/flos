//
// @date: 2021-09-26
// @link: https://leetcode.com/problems/find-the-town-judge/

#include "vector"

using namespace std;

class Solution {
public:
    int findJudge(int n, vector<vector<int>> &trust) {
        vector<int> v(n + 1);
        for (auto tr: trust) {
            v[tr[0]]--;
            v[tr[1]]++;
        }

        for (int i = 1; i <= n; ++i) {
            if (v[i] == n - 1) {
                return i;
            }
        }
        return -1;
    }
};