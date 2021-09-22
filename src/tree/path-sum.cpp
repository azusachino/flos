//
// @date: 2021-09-22
// @link: https://leetcode.com/problems/path-sum/

#include "common.h"


using namespace std;

class Solution {
public:
    bool hasPathSum(TreeNode *root, int targetSum) {
        if (!root) {
            return false;
        }
        if (root->val == targetSum) {
            return true;
        }
        return hasPathSum(root->left, targetSum - root->val) || hasPathSum(root->right, targetSum - root->val);
    }
};