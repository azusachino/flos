#include <map>
#include <sstream>
#include <string>

using namespace std;

class Solution {
public:
    static bool wordPattern(string pattern, const string &s) {
        map<char, int> p2i;
        map<string, int> w2i;
        istringstream in(s);
        int i = 0;
        size_t n = pattern.size();
        for (string word; in >> word; ++i) {
            if (i == n || p2i[pattern[i]] != w2i[word]) {
                return false;
            }
            p2i[pattern[i]] = w2i[word] = i + 1;
        }
        return i == n;
    }
};