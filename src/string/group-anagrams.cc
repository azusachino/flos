#include <string>
#include <vector>
#include <unordered_map>
#include <algorithm>

using namespace std;

class Solution {
public:
    static vector<vector<string>> groupAnagrams(vector<string> &strs) {
        unordered_map<string, vector<string>> mp;
        for (const auto &s: strs) {
            string t = s;
            sort(t.begin(), t.end());
            mp[t].push_back(s);
        }
        vector<vector<string>> ans;
        ans.reserve(mp.size());
        for (auto const &p: mp) {
            ans.push_back(p.second);
        }
        return ans;
    }
};
