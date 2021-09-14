#include <algorithm>
#include <list>
#include <vector>

using namespace std;

vector<vector<int>> res = {};

void backtrack(vector<int> &nums, list<int> &track) {
    if (track.size() == nums.size()) {
        vector<int> v;
        std::copy(track.begin(), track.end(), v);
        res.push_back(v);
        return;
    }

    for (int i = 0; i < nums.size(); ++i) {
        if (find(track.begin(), track.end(), nums[i]) != track.end()) {
            continue;
        }
        track.push_back(nums[i]);
        backtrack(nums, track);
        track.pop_back();
    }
}
