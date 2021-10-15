#include <vector>

using namespace std;

class Solution {
public:
    void moveZeroes(vector<int>& nums) {
        using std::swap;
        for (int last = 0, cur = 0; cur < nums.size(); ++cur) {
            if (nums[cur] != 0) {
                swap(nums[last++], nums[cur]);
            }
        }
    }
};