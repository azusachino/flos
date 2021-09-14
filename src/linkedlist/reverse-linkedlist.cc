#include "common.h"

ListNode *reverseList(ListNode *head) {
    auto *last = static_cast<ListNode *>(nullptr);
    while (head) {
        ListNode *nxt = head->next;
        head->next = last;
        last = head;
        head = nxt;
    }
    return last;
}
