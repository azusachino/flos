//
// @date: 2021-09-27
// @link: https://leetcode.com/problems/sort-characters-by-frequency/

#include <unordered_map>
#include <string>
#include "vector"

using namespace std;

class Solution {
public:
    string frequencySort(string s) {
        unordered_map<char, int> freq;
        vector<string> bucket(s.size() + 1, "");
        string res;

        //count frequency of each character
        for (char c: s) freq[c]++;
        //put character into frequency bucket
        for (auto &it: freq) {
            int n = it.second;
            char c = it.first;
            bucket[n].append(n, c);
        }
        //form descending sorted string
        for (int i = s.size(); i > 0; i--) {
            if (!bucket[i].empty())
                res.append(bucket[i]);
        }
        return res;
    }
};