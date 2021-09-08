#include "common.h"
#include "limits.h"

// simple wrong version, didn't cover whole tree
bool isValidBST(const TreeNode* const root) {
    if (!root) {
        return true;
    }
    if (root->left && root->val <= root->left->val) {
        return false;
    }
    if (root->right && root->val >= root->right->val) {
        return false;
    }
    return isValidBST(root->left) && isValidBST(root->right);
}

bool _isValidBST(const TreeNode* const root) {
    return helper(root, nullptr, nullptr);
}

bool helper(const TreeNode* const root, const TreeNode* const l,
            const TreeNode* const r) {
    if (!root) {
        return true;
    }
    if (l && root->val <= l->val) {
        return false;
    }
    if (r && root->val >= r->val) {
        return false;
    }
    return helper(root->left, l, root) && helper(root->right, root, r);
}
