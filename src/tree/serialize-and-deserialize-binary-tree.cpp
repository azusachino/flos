//
// @date: 2021-09-24
// @link: https://leetcode.com/problems/serialize-and-deserialize-binary-tree/

#include "common.h"
#include "sstream"

class Codec {
public:

    // Encodes a tree to a single string.
    string serialize(TreeNode *root) {
        ostringstream out;
        serialize(root, out);
        return out.str();
    }

    // Decodes your encoded data to tree.
    TreeNode *deserialize(const string &data) {
        istringstream in(data);
        return deserialize(in);
    }

private:
    void serialize(TreeNode *node, ostringstream &out) {
        if (node) {
            out << node->val << ' ';
            serialize(node->left, out);
            serialize(node->right, out);
        } else {
            cout << "# ";
        }
    }

    TreeNode *deserialize(istringstream &in) {
        string val;
        in >> val;
        if (val == "#") {
            return nullptr;
        }
        auto *root = new TreeNode(stoi(val));
        root->left = deserialize(in);
        root->right = deserialize(in);
        return root;
    }
};