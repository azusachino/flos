#include <string>
#include <algorithm>

using namespace std;

class Solution {
public:
    static string addStrings(string num1, string num2) {
        size_t i = num1.size() - 1;
        size_t j = num2.size() - 1;
        int carry = 0;
        string res;
        while (i >= 0 || j >= 0 || carry) {
            long sum = 0;
            if (i >= 0) {
                sum += (num1[i] - '0');
                i--;
            }
            if (j >= 0) {
                sum += (num2[j] - '0');
                j--;
            }
            sum += carry;
            carry = sum / 10;
            sum = sum % 10;
            res.append(to_string(sum));
        }
        reverse(res.begin(), res.end());
        return res;
    }
};