//
// @date: 2021-09-23
// @link: https://leetcode.com/problems/binary-search-tree-iterator/

#include "common.h"
#include "stack"

class BSTIterator {
private:
    std::stack<TreeNode*> st;
public:
    explicit BSTIterator(TreeNode *root) {
        find_left(root);
    }

    /** @return whether we have a next smallest number */
    bool hasNext() {
        if (st.empty())
            return false;
        return true;
    }

    /** @return the next smallest number */
    int next() {
        TreeNode* top = st.top();
        st.pop();
        if (top->right != nullptr)
            find_left(top->right);

        return top->val;
    }

    /** put all the left child() of root */
    void find_left(TreeNode* root)
    {
        TreeNode* p = root;
        while (p != nullptr)
        {
            st.push(p);
            p = p->left;
        }
    }
};