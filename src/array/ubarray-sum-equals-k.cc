#include <vector>
#include <unordered_map>

using namespace std;

int subarraySum(vector<int> &nums, int k) {
    int sum = 0, result = 0;
    unordered_map<int, int> m;
    m[0] = 1;
    for (int num: nums) {
        sum += num;
        if (m.find(sum - k) != m.end()) {
            result += m[sum - k];
        }
        if (m.find(sum) != m.end()) {
            m[sum] = m[sum] + 1;
        } else {
            m[sum] = 0;
        }
    }
    return result;
}