import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class BarvanjeParallel {

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

        public String getNumbers() {
            return String.format("n:%d d:%d b:%d c:%d\n", this.n, this.d, this.b, this.c);
        }
    }

    private static class AlgorithmResult {
        final long score;
        final char[][] bestGrid;
        AlgorithmResult(long score, char[][] bestGrid) {
            this.score = score;
            this.bestGrid = bestGrid;
        }
    }

    public static void main(String[] args) throws Exception {
        List<GridCase> allGrids = getGrids("Barvanje.txt");
        // Cases 6–12 (0-based indices 5–11)
        List<GridCase> grids = new ArrayList<>(allGrids.subList(5, allGrids.size()));
        int count = grids.size(); // 7

        // Linear time ramp: case 6 = 1 unit, case 7 = 2 units, ..., case 12 = 7 units.
        // Total = 28 units = 1 week wall time (cases run sequentially).
        long weekMs = 7L * 24 * 3_600_000L;
        long unit = weekMs / 28; // ≈21,600,000 ms ≈6 hours
        long[] limits = new long[count];
        for (int i = 0; i < count; i++) limits[i] = (long)(i + 1) * unit;

        // All available threads work on ONE case at a time, each with a different seed.
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);

        System.out.println("Using " + numThreads + " threads per case.");
        for (int i = 0; i < count; i++)
            System.out.printf("  Case %d: cap = %.1fh%n", i + 6, limits[i] / 3_600_000.0);
        System.out.println();


        for (int k = 0; k < count; k++) {
            GridCase gc = grids.get(k);
            long caseLimit = limits[k];
            long patienceMs = caseLimit / 3; // 1/3 of the case budget
            int caseNum = k + 6;
            System.out.printf("Starting case %d (cap=%.1fh, patience=%.1fh)...%n",
                caseNum, caseLimit / 3_600_000.0, patienceMs / 3_600_000.0);

            long caseStart = System.currentTimeMillis();

            // Launch one solver per thread, each with a unique seed
            List<Future<AlgorithmResult>> futures = new ArrayList<>();
            for (int t = 0; t < numThreads; t++) {
                final long seed = System.nanoTime() + t * 7919L;
                final int threadId = t;
                futures.add(pool.submit(() -> algorithm(gc, caseLimit, patienceMs, seed, threadId)));
            }

            // Collect and pick the best result across all threads
            AlgorithmResult best = null;
            for (int t = 0; t < numThreads; t++) {
                AlgorithmResult r = futures.get(t).get();
                if (best == null || r.score > best.score) best = r;
            }

            long usedMs = System.currentTimeMillis() - caseStart;
            System.out.printf("Case %d done: %d  (%.2fh used / %.2fh cap)%n",
                caseNum, best.score, usedMs / 3_600_000.0, caseLimit / 3_600_000.0);
            System.out.flush();

            // Write best grid back into the GridCase for solution output
            for (int r = 0; r < gc.n; r++) {
                List<Character> row = gc.grid.get(r);
                for (int j = 0; j < gc.n; j++)
                    row.set(j, best.bestGrid[r][j]);
            }

            // Write partial results after each case so a kill doesn't lose everything
            writeSolutions(grids.subList(0, k + 1), "Barvanje_solution2.txt", 6);
            System.out.println("  → Barvanje_solution2.txt updated");
            System.out.flush();
        }

        pool.shutdown();
        System.out.println("All cases done.");
    }

    private static List<GridCase> getGrids(String path) {
        List<GridCase> grids = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            System.out.println(br.readLine());
            int gridCount = Integer.parseInt(br.readLine());

            String line;
            for (int g = 0; g < gridCount; g++) {
                while ((line = br.readLine()) != null && line.trim().isEmpty()) {
                }
                line = br.readLine();
                if (line == null)
                    break;

                String[] parts = line.trim().split("\\s+");
                int n = Integer.parseInt(parts[0]);
                int d = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                int c = Integer.parseInt(parts[3]);

                List<List<Character>> grid = new ArrayList<>();
                for (int r = 0; r < n; r++) {
                    String rowLine = br.readLine();
                    List<Character> row = new ArrayList<>(rowLine.length());
                    for (char ch : rowLine.toCharArray()) {
                        row.add(ch);
                    }
                    grid.add(row);
                }
                grids.add(new GridCase(n, d, b, c, grid));
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        return grids;
    }

    /**
     * Ruin-and-rebuild heuristic — identical to Barvanje.algorithm() but takes
     * an explicit seed and returns an AlgorithmResult instead of modifying gridCase.
     * This makes it safe to call from multiple threads on the same GridCase.
     */
    private static AlgorithmResult algorithm(GridCase gridCase, long timeLimit, long patienceMs, long seed, int threadId) {
        int n = gridCase.n;
        int d = gridCase.d;
        int b = gridCase.b;
        int c = gridCase.c;

        char[][] bestGrid = new char[n][n];
        char[][] grid = new char[n][n];
        // Read-only access to gridCase.grid — safe for concurrent threads
        char[][] origGrid = new char[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                char ch = gridCase.grid.get(i).get(j);
                grid[i][j] = ch;
                bestGrid[i][j] = ch;
                origGrid[i][j] = ch;
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

        long initScore = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (grid[i][j] == '#')
                    initScore += neighBlacks[i][j];
        initScore /= 2;

        Random rand = new Random(seed);
        long startTime = System.currentTimeMillis();
        long bestScore = initScore;
        long currentScore = initScore;
        int iters = 0;

        long restartScale = (long) n * n * Math.max(1, 2 * d + 1);
        long effectivePatience = Math.max(30_000L, Math.min(patienceMs / 3, restartScale / 2));
        long lastBestImprovement = startTime;

        int maxBucket = (2*d+1)*(2*d+1);
        boolean usePropReinsert = (long) n * n * maxBucket < 100_000_000L;

        int[][] blockingCount = new int[n][n];
        boolean[][] isBlocking = new boolean[n][n];

        int initCap = Math.max(16, n * n / maxBucket * 4);
        int[][] bkt = new int[maxBucket][initCap];
        int[] bktSz = new int[maxBucket];

        long resetCost = (long) maxBucket * n * n;
        boolean useReset = resetCost < 200_000_000L;

        boolean timeUp = false;
        int placements = 0;
        long lastImprovementTime = startTime;

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

        // Swap local search: O(n² × (4d+1)²) per sweep — skip for huge windows.
        boolean useSwap = (long) n * n * (4*d+1) * (4*d+1) < 500_000_000L;

        // ── Main ruin-and-rebuild loop ────────────────────────────────────────────
        while (!timeUp && System.currentTimeMillis() - startTime < timeLimit
                       && System.currentTimeMillis() - lastBestImprovement < patienceMs) {
            iters++;

            // ── Patience-based restart ────────────────────────────────────────
            if (System.currentTimeMillis() - lastImprovementTime >= effectivePatience) {
                rand = new Random(System.nanoTime());
                // Usually restart from bestGrid and only partially ruin it.
                // This keeps valuable structure while still escaping local minima.
                boolean restartFromBest = bestScore > initScore;
                if (restartFromBest) {
                    currentScore = bestScore;
                    for (int i = 0; i < n; i++)
                        for (int j = 0; j < n; j++)
                            grid[i][j] = bestGrid[i][j];

                    // Remove a random subset of added black cells (10-35%).
                    double frac = 0.10 + rand.nextDouble() * 0.25;
                    for (int i = 0; i < n; i++)
                        for (int j = 0; j < n; j++)
                            if (grid[i][j] == '#' && origGrid[i][j] != '#' && rand.nextDouble() < frac)
                                grid[i][j] = '.';
                } else {
                    currentScore = initScore;
                    for (int i = 0; i < n; i++)
                        for (int j = 0; j < n; j++)
                            grid[i][j] = origGrid[i][j];
                }

                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++) {
                        int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                        int c0x = Math.max(0,j-d), c1x = Math.min(n-1,j+d);
                        int bCnt = 0, wCnt = 0;
                        for (int k = r0; k <= r1; k++)
                            for (int l = c0x; l <= c1x; l++) {
                                if (k == i && l == j) continue;
                                if (grid[k][l] == '#') bCnt++;
                                else if (grid[k][l] == '.') wCnt++;
                            }
                        neighBlacks[i][j] = bCnt;
                        neighWhites[i][j] = wCnt;
                    }
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++) {
                        blockingCount[i][j] = 0;
                        isBlocking[i][j] = false;
                    }
                currentScore = 0;
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        if (grid[i][j] == '#')
                            currentScore += neighBlacks[i][j];
                currentScore /= 2;

                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        if (grid[i][j] == '#' && (neighBlacks[i][j] >= c || neighWhites[i][j] <= b)) {
                            isBlocking[i][j] = true;
                            int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                            int c0x = Math.max(0,j-d), c1x = Math.min(n-1,j+d);
                            for (int k = r0; k <= r1; k++)
                                for (int l = c0x; l <= c1x; l++) {
                                    if (k==i && l==j) continue;
                                    blockingCount[k][l]++;
                                }
                        }
                lastImprovementTime = System.currentTimeMillis();
            }

            // ── Phase 1: fill the bucket queue ───────────────────────────────────
            for (int p = 0; p < maxBucket; p++) bktSz[p] = 0;
            int topBucket = 0;
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (grid[i][j] == '.') {
                        int p = neighBlacks[i][j];
                        if (bktSz[p] == bkt[p].length) bkt[p] = java.util.Arrays.copyOf(bkt[p], bkt[p].length * 2);
                        bkt[p][bktSz[p]++] = i * n + j;
                        if (p > topBucket) topBucket = p;
                    }

            // ── Phase 2: greedy fill ──────────────────────────────────────────────
            while (true) {
                while (topBucket >= 0 && bktSz[topBucket] == 0) topBucket--;
                if (topBucket < 0) break;

                int idx = rand.nextInt(bktSz[topBucket]);
                int code = bkt[topBucket][idx];
                bkt[topBucket][idx] = bkt[topBucket][--bktSz[topBucket]];
                int pi = code / n, pj = code % n;

                if (grid[pi][pj] != '.') continue;
                if (neighWhites[pi][pj] < b || neighBlacks[pi][pj] > c || blockingCount[pi][pj] > 0) continue;
                if (neighBlacks[pi][pj] != topBucket) {
                    if (!usePropReinsert) {
                        int p = neighBlacks[pi][pj];
                        if (p < maxBucket) {
                            if (bktSz[p] == bkt[p].length) bkt[p] = java.util.Arrays.copyOf(bkt[p], bkt[p].length * 2);
                            bkt[p][bktSz[p]++] = code;
                            if (p > topBucket) topBucket = p;
                        }
                    }
                    continue;
                }

                // ── Place the cell ────────────────────────────────────────────────
                currentScore += neighBlacks[pi][pj];
                grid[pi][pj] = '#';

                if ((++placements & 1023) == 0 && System.currentTimeMillis() - startTime >= timeLimit) {
                    timeUp = true;
                    break;
                }

                int r0 = Math.max(0,pi-d), r1 = Math.min(n-1,pi+d);
                int c0 = Math.max(0,pj-d), c1 = Math.min(n-1,pj+d);

                // ── Self-blocking check ───────────────────────────────────────────
                if (!isBlocking[pi][pj] && (neighBlacks[pi][pj] >= c || neighWhites[pi][pj] <= b)) {
                    isBlocking[pi][pj] = true;
                    for (int x = r0; x <= r1; x++)
                        for (int y = c0; y <= c1; y++) {
                            if (x==pi && y==pj) continue;
                            blockingCount[x][y]++;
                        }
                }

                for (int k = r0; k <= r1; k++)
                    for (int l = c0; l <= c1; l++) {
                        if (k==pi && l==pj) continue;
                        neighBlacks[k][l]++;
                        neighWhites[k][l]--;

                        if (usePropReinsert && grid[k][l] == '.') {
                            int p = neighBlacks[k][l];
                            if (p < maxBucket) {
                                if (bktSz[p] == bkt[p].length) bkt[p] = java.util.Arrays.copyOf(bkt[p], bkt[p].length * 2);
                                bkt[p][bktSz[p]++] = k * n + l;
                                if (p > topBucket) topBucket = p;
                            }
                        }

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
            // ── End greedy fill ───────────────────────────────────────────────────

            // ── Phase 1b: swap-based local search ─────────────────────────────────
            boolean improved = useSwap;
            while (improved) {
                improved = false;
                for (int si = 0; si < n && !timeUp; si++) {
                    // Time check every row to avoid getting stuck
                    if (System.currentTimeMillis() - startTime >= timeLimit) { timeUp = true; break; }
                    for (int sj = 0; sj < n && !timeUp; sj++) {
                        if (grid[si][sj] != '#') continue;
                        int removeLoss = neighBlacks[si][sj];

                        grid[si][sj] = '.';
                        int sr0 = Math.max(0,si-d), sr1 = Math.min(n-1,si+d);
                        int sc0 = Math.max(0,sj-d), sc1 = Math.min(n-1,sj+d);
                        for (int k = sr0; k <= sr1; k++)
                            for (int l = sc0; l <= sc1; l++) {
                                if (k==si && l==sj) continue;
                                neighBlacks[k][l]--;
                                neighWhites[k][l]++;
                            }

                        int bestGain = removeLoss;
                        int bestPi = -1, bestPj = -1;
                        int er0 = Math.max(0,si-2*d), er1 = Math.min(n-1,si+2*d);
                        int ec0 = Math.max(0,sj-2*d), ec1 = Math.min(n-1,sj+2*d);
                        for (int wi = er0; wi <= er1; wi++)
                            for (int wj = ec0; wj <= ec1; wj++) {
                                if (grid[wi][wj] != '.') continue;
                                int wBlacks = neighBlacks[wi][wj];
                                int wWhites = neighWhites[wi][wj];
                                if (wWhites < b || wBlacks > c) continue;
                                boolean blocked = false;
                                int wr0 = Math.max(0,wi-d), wr1 = Math.min(n-1,wi+d);
                                int wc0 = Math.max(0,wj-d), wc1 = Math.min(n-1,wj+d);
                                for (int k = wr0; k <= wr1 && !blocked; k++)
                                    for (int l = wc0; l <= wc1 && !blocked; l++) {
                                        if (k==wi && l==wj) continue;
                                        if (grid[k][l] == '#') {
                                            if (neighBlacks[k][l] >= c) blocked = true;
                                            if (neighWhites[k][l] <= b) blocked = true;
                                        }
                                    }
                                if (blocked) continue;
                                if (wBlacks > bestGain) {
                                    bestGain = wBlacks;
                                    bestPi = wi;
                                    bestPj = wj;
                                }
                            }

                        if (bestPi >= 0) {
                            currentScore -= removeLoss;
                            currentScore += bestGain;
                            if (isBlocking[si][sj]) {
                                isBlocking[si][sj] = false;
                                for (int k = sr0; k <= sr1; k++)
                                    for (int l = sc0; l <= sc1; l++) {
                                        if (k==si && l==sj) continue;
                                        blockingCount[k][l]--;
                                    }
                            }
                            for (int k = sr0; k <= sr1; k++)
                                for (int l = sc0; l <= sc1; l++) {
                                    if (k==si && l==sj) continue;
                                    if (grid[k][l] == '#' && isBlocking[k][l]
                                            && neighBlacks[k][l] < c && neighWhites[k][l] > b) {
                                        isBlocking[k][l] = false;
                                        int r2 = Math.max(0,k-d), r3 = Math.min(n-1,k+d);
                                        int c2 = Math.max(0,l-d), c3 = Math.min(n-1,l+d);
                                        for (int x = r2; x <= r3; x++)
                                            for (int y = c2; y <= c3; y++) {
                                                if (x==k && y==l) continue;
                                                blockingCount[x][y]--;
                                            }
                                    }
                                }
                            grid[bestPi][bestPj] = '#';
                            int pr0 = Math.max(0,bestPi-d), pr1 = Math.min(n-1,bestPi+d);
                            int pc0 = Math.max(0,bestPj-d), pc1 = Math.min(n-1,bestPj+d);
                            if (neighBlacks[bestPi][bestPj] >= c || neighWhites[bestPi][bestPj] <= b) {
                                isBlocking[bestPi][bestPj] = true;
                                for (int x = pr0; x <= pr1; x++)
                                    for (int y = pc0; y <= pc1; y++) {
                                        if (x==bestPi && y==bestPj) continue;
                                        blockingCount[x][y]++;
                                    }
                            }
                            for (int k = pr0; k <= pr1; k++)
                                for (int l = pc0; l <= pc1; l++) {
                                    if (k==bestPi && l==bestPj) continue;
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
                            improved = true;
                        } else {
                            grid[si][sj] = '#';
                            for (int k = sr0; k <= sr1; k++)
                                for (int l = sc0; l <= sc1; l++) {
                                    if (k==si && l==sj) continue;
                                    neighBlacks[k][l]++;
                                    neighWhites[k][l]--;
                                }
                        }

                        if ((++placements & 1023) == 0 && System.currentTimeMillis() - startTime >= timeLimit) {
                            timeUp = true;
                        }
                    }
                } // end row loop
            }

            if (currentScore > bestScore) {
                bestScore = currentScore;
                lastImprovementTime = System.currentTimeMillis();
                lastBestImprovement = lastImprovementTime;
                long elapsed = (lastImprovementTime - startTime) / 1000;
                System.out.println("    [t" + threadId + "] best=" + bestScore + "  (" + elapsed + "s)");
                System.out.flush();
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        bestGrid[i][j] = grid[i][j];
            }

            // ── Phase 3 (every 20 iters): hard reset to best ─────────────────────
            if (useReset && iters % 20 == 0) {
                currentScore = bestScore;
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++) {
                        char prev = grid[i][j];
                        char next = bestGrid[i][j];
                        if (prev == next) continue;
                        if (prev == '#' && isBlocking[i][j]) {
                            isBlocking[i][j] = false;
                            int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                            int c0 = Math.max(0,j-d), c1 = Math.min(n-1,j+d);
                            for (int k = r0; k <= r1; k++)
                                for (int l = c0; l <= c1; l++) {
                                    if (k==i && l==j) continue;
                                    blockingCount[k][l]--;
                                }
                        }
                        grid[i][j] = next;
                        int diff = (next == '#') ? 1 : -1;
                        int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                        int c0 = Math.max(0,j-d), c1 = Math.min(n-1,j+d);
                        for (int k = r0; k <= r1; k++)
                            for (int l = c0; l <= c1; l++) {
                                if (k==i && l==j) continue;
                                neighBlacks[k][l] += diff;
                                neighWhites[k][l] -= diff;
                            }
                    }
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
                continue;
            }

            // ── Phase 4: ruin ─────────────────────────────────────────────────────
            // Alternate between different ruin strategies for diversity:
            //   0 = rectangle ruin (original)
            //   1 = scatter ruin (random fraction of all blacks)
            //   2 = row/column band ruin
            int ruinType = iters % 3;

            if (ruinType == 1) {
                // Scatter ruin: remove a random 5-40% of placed black cells
                double frac = 0.05 + rand.nextDouble() * 0.35;
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        if (grid[i][j] == '#' && origGrid[i][j] != '#' && rand.nextDouble() < frac) {
                            currentScore -= neighBlacks[i][j];
                            if (isBlocking[i][j]) {
                                isBlocking[i][j] = false;
                                int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                                int c0 = Math.max(0,j-d), c1 = Math.min(n-1,j+d);
                                for (int k = r0; k <= r1; k++)
                                    for (int l = c0; l <= c1; l++) {
                                        if (k==i && l==j) continue;
                                        blockingCount[k][l]--;
                                    }
                            }
                            grid[i][j] = '.';
                            int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                            int c0 = Math.max(0,j-d), c1 = Math.min(n-1,j+d);
                            for (int k = r0; k <= r1; k++)
                                for (int l = c0; l <= c1; l++) {
                                    if (k==i && l==j) continue;
                                    neighBlacks[k][l]--;
                                    neighWhites[k][l]++;
                                    if (grid[k][l] == '#' && isBlocking[k][l]
                                            && neighBlacks[k][l] < c && neighWhites[k][l] > b) {
                                        isBlocking[k][l] = false;
                                        int r2 = Math.max(0,k-d), r3 = Math.min(n-1,k+d);
                                        int c2 = Math.max(0,l-d), c3 = Math.min(n-1,l+d);
                                        for (int x = r2; x <= r3; x++)
                                            for (int y = c2; y <= c3; y++) {
                                                if (x==k && y==l) continue;
                                                blockingCount[x][y]--;
                                            }
                                    }
                                }
                        }
            } else {
                // Rectangle ruin (type 0) or band ruin (type 2)
                int minBlock = 1;
                int maxBlock = Math.max(minBlock + 1, n / 2);
                int blockH, blockW, startI, startJ;
                if (ruinType == 2) {
                    // Band: full-width or full-height strip
                    if (rand.nextBoolean()) {
                        blockH = 1 + rand.nextInt(Math.max(1, n / 4));
                        blockW = n;
                    } else {
                        blockH = n;
                        blockW = 1 + rand.nextInt(Math.max(1, n / 4));
                    }
                } else {
                    int blockSize = minBlock + rand.nextInt(maxBlock - minBlock + 1);
                    blockH = blockSize;
                    blockW = blockSize;
                }
                startI = rand.nextInt(Math.max(1, n - blockH + 1));
                startJ = rand.nextInt(Math.max(1, n - blockW + 1));

                for (int i = startI; i < startI + blockH && i < n; i++)
                    for (int j = startJ; j < startJ + blockW && j < n; j++)
                        if (grid[i][j] == '#' && origGrid[i][j] != '#') {
                            currentScore -= neighBlacks[i][j];
                            if (isBlocking[i][j]) {
                                isBlocking[i][j] = false;
                                int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                                int c0 = Math.max(0,j-d), c1 = Math.min(n-1,j+d);
                                for (int k = r0; k <= r1; k++)
                                    for (int l = c0; l <= c1; l++) {
                                        if (k==i && l==j) continue;
                                        blockingCount[k][l]--;
                                    }
                            }
                            grid[i][j] = '.';
                            int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                            int c0 = Math.max(0,j-d), c1 = Math.min(n-1,j+d);
                            for (int k = r0; k <= r1; k++)
                                for (int l = c0; l <= c1; l++) {
                                    if (k==i && l==j) continue;
                                    neighBlacks[k][l]--;
                                    neighWhites[k][l]++;
                                    if (grid[k][l] == '#' && isBlocking[k][l]
                                            && neighBlacks[k][l] < c && neighWhites[k][l] > b) {
                                        isBlocking[k][l] = false;
                                        int r2 = Math.max(0,k-d), r3 = Math.min(n-1,k+d);
                                        int c2 = Math.max(0,l-d), c3 = Math.min(n-1,l+d);
                                        for (int x = r2; x <= r3; x++)
                                            for (int y = c2; y <= c3; y++) {
                                                if (x==k && y==l) continue;
                                                blockingCount[x][y]--;
                                            }
                                    }
                                }
                        }
            }
        }
        // ── End main loop ─────────────────────────────────────────────────────────

        return new AlgorithmResult(bestScore, bestGrid);
    }

    private static void writeSolutions(List<GridCase> grids, String path, int caseOffset) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(path))) {
            pw.println("Barvanje");
            pw.println(grids.size());
            for (int i = 0; i < grids.size(); i++) {
                GridCase g = grids.get(i);
                pw.println();
                pw.println(i + caseOffset);
                pw.println(g.n + " " + g.d + " " + g.b + " " + g.c);
                for (List<Character> row : g.grid) {
                    for (char cell : row) pw.print(cell);
                    pw.println();
                }
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}
