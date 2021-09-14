#include <string>
#include <vector>

using namespace std;

class Solution {
public:
    static vector<int> partitionLabels(string s) {
        vector<int> charIdx(26, 0);
        for (int i = 0; i < s.size(); i++) {
            charIdx[s[i] - 'a'] = i;
        }

        vector<int> results;

        int maxIdx = -1, lastIdx = 0;
        for (int i = 0; i < s.size(); i++) {
            maxIdx = max(maxIdx, charIdx[s[i] - 'a']);
            if (i == maxIdx) {
                results.push_back(maxIdx - lastIdx + 1);
                lastIdx = i + 1;
            }
        }
        return results;
    }
};