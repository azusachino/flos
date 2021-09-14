#include "common.h"

void traverse(const TreeNode *root) {
    if (!root) {
        return;
    }
    // preorder
    traverse(root->left);
    // inorder
    traverse(root->right);
    // postorder
}
