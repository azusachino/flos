#include <iostream>

#include "common.h"

using namespace std;

class Solution {
public:
    TreeNode *inorder(TreeNode *root) {
        if (root != nullptr) {
            inorder(root->left);
            cout << root->val << endl;
            inorder(root->right);
        }
    }
};
