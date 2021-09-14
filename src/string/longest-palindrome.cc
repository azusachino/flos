#include <string>
#include <algorithm>


using namespace std;

class Solution {
public:
    static int longestPalindrome(string s) {
        size_t odds = 0;
        for (char c = 'A'; c <= 'z'; c++)
            odds += count(s.begin(), s.end(), c) & 1;
        return static_cast<int>(s.size() - odds + (odds > 0));
    }
};