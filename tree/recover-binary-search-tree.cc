#include "common.h"
#include "limits.h"

// @number: 99
// @link: https://leetcode.com/problems/recover-binary-search-tree/

class Solution {
public:
    void recoverTree(TreeNode* root) {
        using std::swap;
        TreeNode* pre    = nullptr;
        TreeNode* first  = nullptr;
        TreeNode* second = nullptr;
        TreeNode* temp   = nullptr;
        while (root) {
            if (root->left != nullptr) {
                temp = root->left;
                while (temp->right && temp->right != root) {
                    temp = temp->right;
                }
                if (temp->right != nullptr) {
                    if (pre != nullptr && pre->val > root->val) {
                        if (!first) {
                            first = pre;
                        }
                        second = root;
                    }
                    pre         = root;
                    temp->right = nullptr;
                    root        = root->right;
                } else {
                    temp->right = root;
                    root        = root->left;
                }
            } else {
                if (pre && pre->val > root->val) {
                    if (!first) {
                        first = pre;
                    }
                    second = root;
                }
                pre  = root;
                root = root->right;
            }
        }
        if (first && second) {
            swap(first, second);
        }
    }
};
