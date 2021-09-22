//
// @date: 2021-09-19
// @link: https://leetcode.com/problems/reverse-nodes-in-k-group/

#include "common.h"


class Solution {
public:
    static ListNode *reverse(ListNode *first, ListNode *last) {
        ListNode *prev = last;

        while (first != last) {
            auto tmp = first->next;
            first->next = prev;
            prev = first;
            first = tmp;
        }

        return prev;
    }

    ListNode *reverseKGroup(ListNode *head, int k) {
        auto node = head;
        for (int i = 0; i < k; ++i) {
            if (!node)
                return head; // nothing to do list too sort
            node = node->next;
        }

        auto new_head = reverse(head, node);
        head->next = reverseKGroup(node, k);
        return new_head;
    }
};