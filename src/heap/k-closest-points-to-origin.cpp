//
// @date: 2021-09-27
// @link: https://leetcode.com/problems/k-closest-points-to-origin/

#include <vector>

using namespace std;

class Solution {
public:
    vector<vector<int>> kClosest(vector<vector<int>> &points, int K) {
        partial_sort(points.begin(), points.begin() + K, points.end(), [](vector<int> &p, vector<int> &q) {
            return p[0] * p[0] + p[1] * p[1] < q[0] * q[0] + q[1] * q[1];
        });
        return vector<vector<int>>(points.begin(), points.begin() + K);
    }
};

