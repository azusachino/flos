//
// @date: 2021-09-22
// @link: https://leetcode.com/problems/binary-tree-right-side-view/

#include <queue>
#include <vector>
#include "common.h"


using namespace std;


class Solution {
public:
    static vector<int> rightSideView(TreeNode *root) {
        vector<int> res;
        if (!root) {
            return res;
        }
        queue<TreeNode *> q;
        q.push(root);
        while (!q.empty()) {
            size_t size = q.size();
            for (auto i = 0; i < size; ++i) {
                if (i == 0) {
                    res.push_back(q.back()->val);
                }
                auto node = q.front();
                q.pop();
                if (node->left) {
                    q.push(node->left);
                }
                if (node->right) {
                    q.push(node->right);
                }
            }
        }
        return res;
    }
};