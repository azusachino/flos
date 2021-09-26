//
// @date: 2021-09-26
// @link: https://leetcode.com/problems/keys-and-rooms/

#include "vector"
#include "stack"
#include "set"

using namespace std;

class Solution {
public:
    bool canVisitAllRooms(vector<vector<int>> &rooms) {
        stack<int> dfs;
        dfs.push(0);
        set<int> seen = {0};
        while (!dfs.empty()) {
            int i = dfs.top();
            dfs.pop();
            for (int j: rooms[i])
                if (seen.count(j) == 0) {
                    dfs.push(j);
                    seen.insert(j);
                    if (rooms.size() == seen.size()) return true;
                }
        }
        return rooms.size() == seen.size();
    }
};