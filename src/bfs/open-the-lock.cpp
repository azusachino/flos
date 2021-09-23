#include <vector>
#include <string>
#include <set>
#include <queue>
#include <unordered_set>

using namespace std;

class Solution {
public:
    static int openLock(vector<string> &deadends, const string &target) {
        set<string> deads(deadends.begin(), deadends.end());
        set<string> visited;
        queue<string> q;
        int step = 0;
        q.push("0000");
        visited.insert("0000");
        while (!q.empty()) {
            size_t sz = q.size();
            for (auto i = 0; i < sz; ++i) {
                string s = q.front();
                q.pop();
                if (deads.find(s) != deads.end()) {
                    continue;
                }
                if (target == s) {
                    return step;
                }
                for (auto j = 0; j < 4; ++j) {
                    string u = up(s, j);
                    if (visited.find(u) == visited.end()) {
                        q.push(u);
                        visited.insert(u);
                    }
                    string d = down(s, j);
                    if (visited.find(d) == visited.end()) {
                        q.push(d);
                        visited.insert(d);
                    }
                }
            }
            step++;
        }
        return step;
    }

private:
    static string &up(string &s, int index) {
        if (s[index] == '9') {
            s[index] = '0';
        } else {
            s[index] += 1;
        }
        return s;
    }

    static string &down(string &s, int index) {
        if (s[index] == '0') {
            s[index] = '9';
        } else {
            s[index] -= 1;
        }
        return s;
    }
};

class S2 {
public:
    static int openLock(vector<string> &deadends, const string &target) {
        unordered_set<string> dds(deadends.begin(), deadends.end());
        unordered_set<string> q1, q2, pass, visited;
        string init = "0000";
        if (dds.find(init) != dds.end() || dds.find(target) != dds.end()) return -1;
        visited.insert("0000");
        q1.insert("0000"), q2.insert(target);
        int res = 0;
        while (!q1.empty() && !q2.empty()) {
            if (q1.size() > q2.size()) swap(q1, q2);
            pass.clear();
            for (const auto &ss: q1) {
                vector<string> nbrs = nbrStrs(ss);
                for (const auto &s: nbrs) {
                    if (q2.find(s) != q2.end()) return res + 1;
                    if (visited.find(s) != visited.end()) continue;
                    if (dds.find(s) == dds.end()) {
                        pass.insert(s);
                        visited.insert(s);
                    }
                }
            }
            swap(q1, pass);
            res++;
        }
        return -1;
    }

private:
    static vector<string> nbrStrs(string key) {
        vector<string> res;
        for (int i = 0; i < 4; i++) {
            string tmp = key;
            tmp[i] = (key[i] - '0' + 1) % 10 + '0';
            res.push_back(tmp);
            tmp[i] = (key[i] - '0' - 1 + 10) % 10 + '0';
            res.push_back(tmp);
        }
        return res;
    }
};