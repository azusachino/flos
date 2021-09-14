#include <vector>

using namespace std;

class Solution {
public:
    static vector<int> plusOne(vector<int> &digits) {
        for (size_t i = digits.size() - 1; i >= 0; --i) {
            if (digits[i] < 9) {
                digits[i]++;
                return digits;
            }
            digits[i] = 0;
        }

        // all 9 case
        digits[0] = 1;
        digits.push_back(0);
        return digits;
    }
};
