#include <iostream>
#include "common.h"

void traverse(const Trie &trie) {
    if (trie) {
        auto nodes = trie.root.children;
        for (TrieNode *node: nodes) {
            std::cout << node->isWord << endl;
        }
    }
}
