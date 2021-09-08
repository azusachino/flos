#include <tr1/memory>

struct Node {
public:
    int                 key;
    int                 val;
    std::auto_ptr<Node> next;

    Node(int k, int v, Node* n) {
        this->key  = k;
        this->val  = v;
        this->next = std::auto_ptr<Node>(n);
    }
};

class MyHashMap {
public:
    const static int    size = 19997;
    const static int    mult = 12582917;
    std::auto_ptr<Node> data[size];
    /** Initialize your data structure here. */
    MyHashMap() {}  // default constructor

    int hash(int key) { return (int)((long)key * mult % size); }
    /** value will always be non-negative. */
    void put(int key, int value) {
        remove(key);
        int                 h = hash(key);
        std::auto_ptr<Node> node =
            std::auto_ptr<Node>(new Node(key, value, data[h].get()));
        data[h] = node;
    }

    /** Returns the value to which the specified key is mapped, or -1 if this
     * map contains no mapping for the key */
    int get(int key) {
        int                 h = hash(key);
        std::auto_ptr<Node> n = data[h];
        for (; n.get() != nullptr; n = n->next) {
            if (n->key == key) {
                return n->val;
            }
        }
        return -1;
    }

    /** Removes the mapping of the specified value key if this map contains a
     * mapping for the key */
    void remove(int key) {
        int                 h = hash(key);
        std::auto_ptr<Node> n = data[h];
        if (n.get() == nullptr) {
            return;
        }
        if (n->key == key) {
            data[h] = n->next;
        } else {
            for (; n->next.get() != nullptr; n = n->next) {
                if (n->next->key == key) {
                    n->next = n->next->next;
                    return;
                }
            }
        }
    }
};
