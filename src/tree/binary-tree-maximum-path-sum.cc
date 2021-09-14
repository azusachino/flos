#include "common.h"
#include <climits>

// @link: https://leetcode.com/problems/binary-tree-maximum-path-sum/

class Solution {
public:
    int ans = INT_MIN;

    int maxPathSum(TreeNode *root) {
        oneSideMax(root);
        return ans;
    }

    int oneSideMax(TreeNode *root) {
        if (root == nullptr) {
            return 0;
        }
        int left = max(0, oneSideMax(root->left));
        int right = max(0, oneSideMax(root->right));

        ans = max(ans, left + right + root->val);
        return max(left, right) + root->val;
    }
};
