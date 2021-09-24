//
// @date: 2021-09-24
// @link: https://leetcode.com/problems/lowest-common-ancestor-of-a-binary-tree/

#include "common.h"

class Solution {
public:
    TreeNode *lowestCommonAncestor(TreeNode *root, TreeNode *p, TreeNode *q) {
        if (!root || root == p || root == q) {
            return root;
        }
        TreeNode *left = lowestCommonAncestor(root->left, p, q);
        TreeNode *right = lowestCommonAncestor(root->right, p, q);
        return !left ? right : !right ? left : root;
    }
};