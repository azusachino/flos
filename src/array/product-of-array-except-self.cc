#include <vector>

using namespace std;

class Solution {
public:
    static vector<int> productExceptSelf(vector<int> &nums) {
        size_t n = nums.size();
        vector<int> fromBegin(n);
        fromBegin[0] = 1;
        vector<int> fromEnd(n);
        fromEnd[0] = 1;

        for (int i = 1; i < n; ++i) {
            fromBegin[i] = fromBegin[i - 1] * nums[i - 1];
            fromEnd[i] = fromEnd[i - 1] * nums[i - 1];
        }
        vector<int> res(n);
        for (int i = 0; i < n; ++i) {
            res[i] = fromBegin[i] * fromEnd[n - 1 - i];
        }
        return res;
    }
};