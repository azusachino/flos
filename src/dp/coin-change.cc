#include <vector>
#include <climits>

using namespace std;

class Solution {
public:
    int coinChange(vector<int> &coins, int amount) { return dp(coins, amount); }

    int dp(vector<int> &coins, int amount) {
        if (amount == 0) {
            return 0;
        }
        if (amount < 0) {
            return -1;
        }
        int res = INT_MAX;
        for (int c: coins) {
            int sp = dp(coins, amount - c);
            if (sp == -1) {
                continue;
            }
            res = min(res, 1 + sp);
        }
        if (res == INT_MAX) {
            return -1;
        }
        return res;
    }
};
