#include <vector>

using namespace std;

class Solution {
public:
    static vector<vector<int>> generateMatrix(int n) {
        vector<vector<int>> r(n, vector<int>(n));
        int k = 1, i = 0;
        while (k <= n * n) {
            int j = i;
            // four steps
            while (j < n - i)  // 1. horizontal, left to right
                r[i][j++] = k++;
            j = i + 1;
            while (j < n - i)  // 2. vertical, top to bottom
                r[j++][n - i - 1] = k++;
            j = n - i - 2;
            while (j > i)  // 3. horizontal, right to left
                r[n - i - 1][j--] = k++;
            j = n - i - 1;
            while (j > i)  // 4. vertical, bottom to  top
                r[j--][i] = k++;
            i++;  // next loop
        }
        return r;
    }
};