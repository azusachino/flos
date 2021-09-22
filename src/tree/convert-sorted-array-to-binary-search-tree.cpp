//
// @date: 2021-09-22
// @link: https://leetcode.com/problems/convert-sorted-array-to-binary-search-tree/

#include "common.h"
#include <vector>

using namespace std;

class Solution {
public:
    static TreeNode *sortedArrayToBST(vector<int> &nums) {
        if (nums.empty()) {
            return nullptr;
        }
        TreeNode *head = helper(nums, 0, static_cast<int>(nums.size() - 1));
        return head;
    }

private:
    static TreeNode *helper(vector<int> &nums, int low, int high) {
        if (low > high) {
            return nullptr;
        }
        int mid = low + (high - low) / 2;
        auto *node = new TreeNode(nums[mid]);
        node->left = helper(nums, low, mid - 1);
        node->right = helper(nums, mid + 1, high);
        return node;
    }
};