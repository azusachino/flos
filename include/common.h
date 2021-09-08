#include <vector>

using namespace std;

class ListNode {
public:
    int       val;
    ListNode *next;

    ListNode() {}
    ListNode(int val) : val(val), next(nullptr) {}
    ListNode(int x, ListNode *next) : val(x), next(next) {}
};

class TrieNode {
public:
    bool      isWord;
    TrieNode *children[];
};

class Trie {
public:
    TrieNode root;
};

class TreeNode {
public:
    int       val;
    TreeNode *left;
    TreeNode *right;
    TreeNode(int val) : val(val) {}
};

class Node {
public:
    int            val;
    vector<Node *> children;

    Node() {}

    Node(int _val) { val = _val; }

    Node(int _val, vector<Node *> _children) {
        val      = _val;
        children = _children;
    }
};
