import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestAlgo {

    private static class GridCase {
        final int n;
        final int d;
        final int b;
        final int c;
        final List<List<Character>> grid;

        GridCase(int n, int d, int b, int c, List<List<Character>> grid) {
            this.n = n;
            this.d = d;
            this.b = b;
            this.c = c;
            this.grid = grid;
        }
    }

    public static void main(String[] args) {
        List<GridCase> grids = getGrids("Barvanje.txt");
        GridCase gridCase = grids.get(0);
        long start = System.currentTimeMillis();
        int score = algorithm(gridCase);
        long end = System.currentTimeMillis();
        System.out.println("Score: " + score + " (Time: " + (end - start) + "ms)");
    }

    private static List<GridCase> getGrids(String path) {
        List<GridCase> grids = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine();
            int gridCount = Integer.parseInt(br.readLine());
            String line;
            for (int g = 0; g < gridCount; g++) {
                while ((line = br.readLine()) != null && line.trim().isEmpty()) {}
                line = br.readLine();
                if (line == null) break;
                String[] parts = line.trim().split("\\s+");
                int n = Integer.parseInt(parts[0]);
                int d = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                int c = Integer.parseInt(parts[3]);
                List<List<Character>> grid = new ArrayList<>();
                for (int r = 0; r < n; r++) {
                    String rowLine = br.readLine();
                    List<Character> row = new ArrayList<>(rowLine.length());
                    for (char ch : rowLine.toCharArray()) row.add(ch);
                    grid.add(row);
                }
                grids.add(new GridCase(n, d, b, c, grid));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return grids;
    }

    private static int algorithm(GridCase gridCase) {
        int n = gridCase.n;
        int d = gridCase.d;
        int b = gridCase.b;
        int c = gridCase.c;

        char[][] bestGrid = new char[n][n];
        char[][] grid = new char[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                grid[i][j] = gridCase.grid.get(i).get(j);
                bestGrid[i][j] = grid[i][j];
            }

        int[][] neighBlacks = new int[n][n];
        int[][] neighWhites = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int r0 = Math.max(0, i-d), r1 = Math.min(n-1, i+d);
                int c0 = Math.max(0, j-d), c1 = Math.min(n-1, j+d);
                int bCnt = 0, wCnt = 0;
                for (int k = r0; k <= r1; k++)
                    for (int l = c0; l <= c1; l++) {
                        if (k == i && l == j) continue;
                        if (grid[k][l] == '#') bCnt++;
                        else if (grid[k][l] == '.') wCnt++;
                    }
                neighBlacks[i][j] = bCnt;
                neighWhites[i][j] = wCnt;
            }
        }

        // Compute initial score from starting grid
        int initScore = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (grid[i][j] == '#')
                    initScore += neighBlacks[i][j];
        initScore /= 2;

        Random rand = new Random(1337);
        long startTime = System.currentTimeMillis();
        long timeLimit = 5000;
        int bestScore = initScore;
        int currentScore = initScore;
        int iters = 0;

        int[][] blockingCount = new int[n][n];
        boolean[][] isBlocking = new boolean[n][n];

        while (System.currentTimeMillis() - startTime < timeLimit) {
            iters++;

            // Reset and recompute blockingCount from current grid state
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) {
                    blockingCount[i][j] = 0;
                    isBlocking[i][j] = false;
                }
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (grid[i][j] == '#' && (neighBlacks[i][j] >= c || neighWhites[i][j] <= b)) {
                        isBlocking[i][j] = true;
                        int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                        int c0 = Math.max(0,j-d), c1 = Math.min(n-1,j+d);
                        for (int k = r0; k <= r1; k++)
                            for (int l = c0; l <= c1; l++) {
                                if (k==i && l==j) continue;
                                blockingCount[k][l]++;
                            }
                    }

            // Greedy fill
            while (true) {
                int bestI = -1, bestJ = -1;
                double bestVal = -1;

                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++) {
                        if (grid[i][j] != '.') continue;
                        if (neighWhites[i][j] < b) continue;
                        if (neighBlacks[i][j] > c) continue;
                        if (blockingCount[i][j] > 0) continue;
                        double val = neighBlacks[i][j] + rand.nextDouble() * 0.1;
                        if (val > bestVal) {
                            bestVal = val;
                            bestI = i; bestJ = j;
                        }
                    }

                if (bestI == -1) break;

                // Place black cell — increment score by its current black neighbors
                currentScore += neighBlacks[bestI][bestJ];
                grid[bestI][bestJ] = '#';

                int r0 = Math.max(0,bestI-d), r1 = Math.min(n-1,bestI+d);
                int c0 = Math.max(0,bestJ-d), c1 = Math.min(n-1,bestJ+d);
                for (int k = r0; k <= r1; k++)
                    for (int l = c0; l <= c1; l++) {
                        if (k==bestI && l==bestJ) continue;
                        neighBlacks[k][l]++;
                        neighWhites[k][l]--;
                        if (grid[k][l] == '#' && !isBlocking[k][l]) {
                            if (neighBlacks[k][l] >= c || neighWhites[k][l] <= b) {
                                isBlocking[k][l] = true;
                                int r2 = Math.max(0,k-d), r3 = Math.min(n-1,k+d);
                                int c2 = Math.max(0,l-d), c3 = Math.min(n-1,l+d);
                                for (int x = r2; x <= r3; x++)
                                    for (int y = c2; y <= c3; y++) {
                                        if (x==k && y==l) continue;
                                        blockingCount[x][y]++;
                                    }
                            }
                        }
                    }
            }

            if (currentScore > bestScore) {
                bestScore = currentScore;
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        bestGrid[i][j] = grid[i][j];
                System.out.println("New best: " + bestScore + " at iter " + iters);
            }

            // Ruin: remove a n/5 x n/5 block
            int blockSize = Math.max(2, n / 5);
            int startI = rand.nextInt(Math.max(1, n - blockSize));
            int startJ = rand.nextInt(Math.max(1, n - blockSize));
            int removeCount = 0;

            for (int i = startI; i < startI + blockSize && i < n; i++)
                for (int j = startJ; j < startJ + blockSize && j < n; j++)
                    if (grid[i][j] == '#' && gridCase.grid.get(i).get(j) != '#') {
                        currentScore -= neighBlacks[i][j];
                        grid[i][j] = '.';
                        int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                        int c0 = Math.max(0,j-d), c1 = Math.min(n-1,j+d);
                        for (int k = r0; k <= r1; k++)
                            for (int l = c0; l <= c1; l++) {
                                if (k==i && l==j) continue;
                                neighBlacks[k][l]--;
                                neighWhites[k][l]++;
                            }
                        removeCount++;
                    }

            if (removeCount == 0) break;
        }
        System.out.println("Iterations: " + iters);

        for (int i = 0; i < n; i++) {
            List<Character> row = gridCase.grid.get(i);
            for (int j = 0; j < n; j++)
                row.set(j, bestGrid[i][j]);
        }
        return bestScore;
    }
}
