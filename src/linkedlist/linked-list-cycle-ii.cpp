//
// @date: 2021-09-16
// @link: https://leetcode.com/problems/linked-list-cycle-ii/

#include "common.h"

class Solution {
public:
    static ListNode *detectCycle(ListNode *head) {
        if (!head || !head->next) {
            return nullptr;
        }
        ListNode *slow = head, *fast = head, *entry = head;
        while (fast->next && fast->next->next) {
            slow = slow->next;
            fast = fast->next->next;
            if (slow == fast) {
                while (slow != entry) {
                    slow = slow->next;
                    entry = entry->next;
                }
                return entry;
            }
        }
        return nullptr;
    }
};