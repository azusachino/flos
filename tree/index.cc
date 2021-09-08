#include "common.h"

void traverse(const TreeNode* root) {
    // preorder
    traverse(root->left);
    // inorder
    traverse(root->right);
    // postorder
}
