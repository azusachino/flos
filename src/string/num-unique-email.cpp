//
// @date: 2021-09-27
// @link: https://leetcode.com/problems/unique-email-addresses/

#include <vector>
#include <string>
#include <set>

using namespace std;

class Solution {
public:
    int numUniqueEmails(vector<string> &emails) {
        set<string> st;
        for (string &email: emails) {
            size_t i;
            string real_mail;
            for (i = 0; i < email.size(); ++i) {
                char ch = email[i];
                if (ch == '+' || ch == '@') {
                    break;
                }
                if (ch == '.') {
                    continue;
                }
            }
            real_mail += email.substr(email.find('@', i));
            st.insert(real_mail);
        }
        return st.size();
    }
};