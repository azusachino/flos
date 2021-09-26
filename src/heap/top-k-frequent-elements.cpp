//
// @date: 2021-09-26
// @link: https://leetcode.com/problems/top-k-frequent-elements/

#include "vector"
#include "unordered_map"
#include "queue"
#include "functional"

using namespace std;

class Solution {
public:
    vector<int> topKFrequent(vector<int> &nums, int k) {
        using map_iterator = unordered_map<int, size_t>::iterator;
        unordered_map<int, size_t> freqCount; // num -> frequency
        for (int num: nums) { ++freqCount[num]; }
        priority_queue<map_iterator, vector<map_iterator>, function<bool(map_iterator, map_iterator)>> topkHeap(
                [](map_iterator lhs, map_iterator rhs) {
                    return lhs->second > rhs->second; // build an min-heap!
                },
                vector<map_iterator>()
        ); // frequency -> num
        for (auto iter = freqCount.begin(); iter != freqCount.end(); ++iter) {
            topkHeap.push(iter);
            if (topkHeap.size() > k) { topkHeap.pop(); } // remove the min value currently
        }
        // now heap hold the elements that elements less than them are all removed.
        vector<int> result(k);
        for (int i = k - 1; i >= 0; --i) {
            auto iter = topkHeap.top();
            result[i] = iter->first;
            topkHeap.pop();
        }
        return result;
    }
};