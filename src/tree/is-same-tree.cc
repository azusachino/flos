#include "common.h"

bool isSameTree(const TreeNode *t1, const TreeNode *t2) {
    if (!t1 && !t2) {
        return true;
    }
    if (!t1 || !t2) {
        return false;
    }
    if (t1->val != t2->val) {
        return false;
    }
    return isSameTree(t1->left, t2->left) && isSameTree(t1->right, t2->right);
}
