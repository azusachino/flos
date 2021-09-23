//
// @date: 2021-09-23
// @link: https://leetcode.com/problems/kth-smallest-element-in-a-bst/
#include "vector"
#include "common.h"

class Solution {
public:
    int kthSmallest(TreeNode *root, int k) {
        return find(root, k);
    }

    int find(TreeNode *node, int &k) {
        if (node) {
            int x = find(node->left, k);
            return !k ? x : !--k ? node->val : find(node->right, k);
        }
        return -1;
    }
};

class S2 {
public:
    int kthSmallest(TreeNode *root, int k) {
        vector<int> v;
        helper(root, k, v);
        return v[k - 1];
    }

    void helper(TreeNode *root, int k, vector<int> &v) {
        if (!root) {
            return;
        }
        if (root->left) {
            helper(root->left, k, v);
        }
        v.push_back(root->val);
        if (root->right) {
            helper(root->right, k, v);
        }
    }
};