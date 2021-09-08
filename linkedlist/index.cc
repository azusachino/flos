#include "common.h"

void traverseIterative(const ListNode* head) {
    for (const ListNode* p = head; p != nullptr; p = p->next) {
        // iterative access
    }
}

void traverse(const ListNode* head) {
    // preorder
    traverse(head);
    // postorder
}
