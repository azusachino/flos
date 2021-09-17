//
// @date: 2021-09-17
// @link: https://leetcode.com/problems/remove-duplicates-from-sorted-list-ii/

#include "common.h"

class Solution {
public:
    ListNode *deleteDuplicates(ListNode *head) {
        if (!head) return nullptr;
        if (!head->next) return head;

        int val = head->val;
        ListNode *p = head->next;

        if (p->val != val) {
            head->next = deleteDuplicates(p);
            return head;
        } else {
            while (p && p->val == val) p = p->next;
            return deleteDuplicates(p);
        }
    }
};