//
// @date: 2021-09-22
// @link: https://leetcode.com/problems/binary-tree-zigzag-level-order-traversal/

#include <vector>
#include <queue>
#include "common.h"

using namespace std;

class Solution {
public:
    static vector<vector<int>> zigzagLevelOrder(TreeNode *root) {
        vector<vector<int>> res;
        if (!root) {
            return res;
        }
        bool dir = true;
        queue<TreeNode *> q;
        q.push(root);
        while (!q.empty()) {
            size_t size = q.size();
            vector<int> row;
            for (auto i = 0; i < size; ++i) {
                auto node = q.front();
                q.pop();
                if (node->left) {
                    q.push(node->left);
                }
                if (node->right) {
                    q.push(node->right);
                }
                if (dir) {
                    row.push_back(node->val);
                } else {
                    row.insert(row.begin(), node->val);
                };
            }
            res.push_back(row);
            dir ^= 1;
        }
        return res;
    }
};