//
// @date: 2021-09-17
// @link: https://leetcode.com/problems/intersection-of-two-linked-lists

#include "common.h"

class Solution {
public:
    ListNode *getIntersectionNode(ListNode *head1, ListNode *head2) {
        ListNode *p1 = head1;
        ListNode *p2 = head2;
        if (p1 == nullptr || p2 == nullptr) {
            return nullptr;
        }
        while (p1 != nullptr && p2 != nullptr && p1 != p2) {
            p1 = p1->next;
            p2 = p2->next;
            if (p1 == p2) {
                return p1;
            }
            if (p1 == nullptr) {
                p1 = head2;
            }
            if (p2 == nullptr) {
                p2 = head1;
            }
        }
        return p1;
    }
};