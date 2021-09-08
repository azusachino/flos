#include "common.h"

bool isInBST(const TreeNode* const root, int target) {
    if (!root) {
        return false;
    }
    if (root->val == target) {
        return true;
    } else if (root->val < target) {
        return isInBST(root->right, target);
    } else {
        return isInBST(root->left, target);
    }
}
