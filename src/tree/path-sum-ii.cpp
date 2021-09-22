//
// @date: 2021-09-22
// @link: https://leetcode.com/problems/path-sum-ii/

#include <vector>
#include "common.h"


using namespace std;

class Solution {
public:
    vector<vector<int>> pathSum(TreeNode *root, int targetSum) {
        vector<vector<int>> res;
        vector<int> path;
        findPaths(root, targetSum, path, res);
        return res;
    }

    void findPaths(TreeNode *node, int sum, vector<int> &path, vector<vector<int>> &res) {
        if (!node) {
            return;
        }
        path.push_back(node->val);
        if (!(node->left) && !(node->right) && sum == node->val) {
            res.push_back(path);
        }
        findPaths(node->left, sum - node->val, path, res);
        findPaths(node->right, sum - node->val, path, res);
        path.pop_back();
    }
};