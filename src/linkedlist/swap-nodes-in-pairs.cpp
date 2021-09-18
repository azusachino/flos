//
// @date: 2021-09-18
// @link: https://leetcode.com/problems/swap-nodes-in-pairs/

#include "common.h"

class Solution {
public:
    static ListNode *swapPairs(ListNode *head) {
        ListNode **pp = &head, *a, *b;
        while ((a = *pp) && (b = a->next)) {
            a->next = b->next;
            b->next = a;
            *pp = b;
            pp = &(a->next);
        }
        return head;
    }
};