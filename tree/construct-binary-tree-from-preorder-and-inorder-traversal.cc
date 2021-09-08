#include "common.h"
#include "limits.h"

// @number: 105
// @link:
// https://leetcode.com/problems/construct-binary-tree-from-preorder-and-inorder-traversal/

class Solution {
public:
    TreeNode* buildTree(vector<int>& preorder, vector<int>& inorder) {
        int inPos  = 0;
        int prePos = 0;
        return build(preorder, inorder, INT_MIN, inPos, prePos);
    }
    TreeNode* build(vector<int>& preorder, vector<int>& inorder, int stop,
                    int& inPos, int& prePos) {
        if (prePos >= preorder.size()) return nullptr;
        if (inorder[inPos] == stop) {
            inPos++;
            return nullptr;
        }
        TreeNode* node = new TreeNode(preorder[prePos]);
        prePos++;
        node->left  = build(preorder, inorder, node->val, inPos, prePos);
        node->right = build(preorder, inorder, stop, inPos, prePos);
        return node;
    }
};
