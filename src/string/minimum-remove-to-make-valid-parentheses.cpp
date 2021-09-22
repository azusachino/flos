//
// @date: 2021-09-22
// @link: https://leetcode.com/problems/minimum-remove-to-make-valid-parentheses/

#include <string>
#include <stack>

using namespace std;

class Solution {
public:
    static string minRemoveToMakeValid(string s) {
        stack<int> stack;
        for (auto i = 0; i < s.size(); ++i) {
            if (s[i] == '(') {
                stack.push(i);
            }
            if (s[i] == ')') {
                if (!stack.empty()) {
                    stack.pop();
                } else {
                    s[i] = '*';
                }
            }
        }
        while (!stack.empty()) {
            s[stack.top()] = '*';
            stack.pop();
        }
        s.erase(remove(s.begin(), s.end(), '*'), s.end());
        return s;
    }
};
