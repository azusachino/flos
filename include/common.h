#include <vector>

using namespace std;

class ListNode {
public:
    int val;
    ListNode *next;

    ListNode() = default;

    explicit ListNode(int val) : val(val), next(nullptr) {}

    ListNode(int x, ListNode *next) : val(x), next(next) {}
};

class TrieNode {
public:
    bool isWord;
    TrieNode *children[];
};

class Trie {
public:
    TrieNode root;
};

class TreeNode {
public:
    int val;
    TreeNode *left;
    TreeNode *right;

    explicit TreeNode(int val) : val(val), left(nullptr), right(nullptr) {}
};

class Node {
public:
    int val;
    Node children[];

    Node() = default;

    explicit Node(int _val) : val(_val), children(nullptr) {}

    Node(int _val, Node &_children[]) : val(_val), children(_children) {}
};
