import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Barvanje {

    private static class GridCase {
        final int n;
        final int d;
        final int b;
        final int c;
        final List<List<Character>> grid;

        GridCase(int n, int d, int b, int c, List<List<Character>> grid) {
            this.n = n; // Grid size
            this.d = d; // Neighbour range
            this.b = b; // Min neighbouring white blocks
            this.c = c; // Max neighbouring black blocks
            this.grid = grid;
        }

        public String getNumbers() {
            return String.format("n:%d d:%d b:%d c:%d\n", this.n, this.d, this.b, this.c);
        }
    }

    public static void main(String[] args) {
        List<GridCase> grids = getGrids("Barvanje.txt");
        int total = grids.size(); // 12
        // Linear ramp: case 1 gets minT, case 12 gets maxT, sum ≈ 300s
        double minT = 5000, maxT = 45000;
        int i = 1;
        for (GridCase gridCase : grids) {
            long caseLimit = (long)(minT + (maxT - minT) * (i - 1.0) / (total - 1));
            // displayGrid(gridCase);
            int score = algorithm(gridCase, caseLimit);
            // displayGrid(gridCase);
            System.out.println(i + ": " + score + "  (" + caseLimit/1000 + "s)");
            i++;
        }
    }

    private static List<GridCase> getGrids(String path) {
        List<GridCase> grids = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            System.out.println(br.readLine()); // optional title
            int gridCount = Integer.parseInt(br.readLine());

            String line;
            for (int g = 0; g < gridCount; g++) {
                while ((line = br.readLine()) != null && line.trim().isEmpty()) {
                    // skip seperators
                }
                line = br.readLine(); // read again to skip the grid indexes
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
     * Ruin-and-rebuild heuristic solver.
     *
     * High-level idea:
     *   1. Greedy fill: repeatedly place the white cell that currently has the most
     *      black neighbours (maximises new pairs created), until no valid placement
     *      remains.
     *   2. Save the grid if this iteration's score beats the best seen so far.
     *   3. Ruin: delete all black cells inside a randomly placed rectangular block,
     *      then go back to step 1.  The ruin forces the algorithm out of local optima
     *      by letting us rebuild a region from scratch with updated surroundings.
     *   4. Every 20 iterations, hard-reset the working grid to the best grid found
     *      so far (another anti-stagnation mechanism).
     *   5. Repeat until the time limit is reached.
     *
     * A "valid" black cell must satisfy:
     *   - neighWhites >= b  (at least b white neighbours within Chebyshev distance d)
     *   - neighBlacks <= c  (at most c black neighbours within Chebyshev distance d)
     *
     * @param gridCase  the input grid, modified in-place to hold the best result
     * @param timeLimit milliseconds budget for this case
     * @return          the best score (number of black–black neighbouring pairs)
     */
    private static int algorithm(GridCase gridCase, long timeLimit) {
        int n = gridCase.n;
        int d = gridCase.d;
        int b = gridCase.b;
        int c = gridCase.c;

        // Working copy of the grid (char[][] is faster than List<List<Character>>).
        // bestGrid stores the highest-scoring state seen across all iterations.
        char[][] bestGrid = new char[n][n];
        char[][] grid = new char[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                grid[i][j] = gridCase.grid.get(i).get(j);
                bestGrid[i][j] = grid[i][j];
            }

        // neighBlacks[i][j] = number of '#' cells within Chebyshev distance d of (i,j),
        //                      NOT counting (i,j) itself.
        // neighWhites[i][j] = same but for '.' cells.
        // Red ('R') cells are ignored — they count as neither black nor white.
        // These arrays are kept in sync incrementally throughout the algorithm
        // (updated whenever a cell changes between '.' and '#').
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

        // Score = number of ordered black–black pairs / 2.
        // Each black cell contributes neighBlacks[i][j] to the sum, but every pair
        // is counted twice (once from each end), so we halve at the end.
        int initScore = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (grid[i][j] == '#')
                    initScore += neighBlacks[i][j];
        initScore /= 2;

        Random rand = new Random(1337); // fixed seed → deterministic, reproducible results
        long startTime = System.currentTimeMillis();
        int bestScore = initScore;
        int currentScore = initScore;
        int iters = 0;

        // maxBucket is the maximum possible neighBlacks value: the full (2d+1)×(2d+1)
        // window minus the cell itself.  Used to size the bucket queue.
        int maxBucket = (2*d+1)*(2*d+1);

        // For small grids/windows (n²×window < 100M ops) we re-insert white neighbours
        // into the bucket queue immediately when their neighBlacks count increases during
        // the fill propagation.  This keeps the queue perfectly up-to-date and removes
        // the need for lazy re-insertion at pop time.
        // For large grids/windows this would flood the queue with billions of entries
        // (each of ~500K placements touching 6000+ neighbours), so we skip it and
        // instead re-insert lazily only when a stale entry is discovered at pop time.
        boolean usePropReinsert = (long) n * n * maxBucket < 100_000_000L;

        // blockingCount[i][j] = how many black cells in (i,j)'s window are "blocking".
        // A black cell is "blocking" if placing another black in its window would push
        // it over the c limit or under the b limit.  A white cell (i,j) with
        // blockingCount > 0 cannot be flipped to black without invalidating an existing
        // black neighbour.
        int[][] blockingCount = new int[n][n];
        boolean[][] isBlocking = new boolean[n][n]; // true if this cell is currently blocking

        // Bucket queue: bkt[p] holds cell codes (i*n+j) of white cells whose
        // current neighBlacks == p.  This gives O(1) insert and pop, replacing the
        // O(log N) PriorityQueue.  topBucket tracks the highest non-empty bucket so
        // we always grab the best candidate first.
        int initCap = Math.max(16, n * n / maxBucket * 4);
        int[][] bkt = new int[maxBucket][initCap];
        int[] bktSz = new int[maxBucket];

        // Periodic full reset is O(n²×window); skip it for large cases where that
        // would cost several seconds per reset.
        long resetCost = (long) maxBucket * n * n;
        boolean useReset = resetCost < 200_000_000L;

        boolean timeUp = false;
        int placements = 0; // counts total placements; used to throttle time checks

        // Compute blockingCount once from the initial grid state.
        // From here on it is maintained incrementally (no full recompute each iteration).
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (grid[i][j] == '#' && (neighBlacks[i][j] >= c || neighWhites[i][j] <= b)) {
                    isBlocking[i][j] = true;
                    int r0 = Math.max(0,i-d), r1 = Math.min(n-1,i+d);
                    int c0 = Math.max(0,j-d), c1 = Math.min(n-1,j+d);
                    // Tell every cell in this black cell's window that it has a blocking neighbour.
                    for (int k = r0; k <= r1; k++)
                        for (int l = c0; l <= c1; l++) {
                            if (k==i && l==j) continue;
                            blockingCount[k][l]++;
                        }
                }

        // ── Main ruin-and-rebuild loop ────────────────────────────────────────────
        while (!timeUp && System.currentTimeMillis() - startTime < timeLimit) {
            iters++;

            // ── Phase 1: fill the bucket queue ───────────────────────────────────
            // Reset all bucket sizes, then insert every white cell into the bucket
            // corresponding to its current neighBlacks count.
            // Encoding: cell (i,j) → code = i*n+j  (decoded: i=code/n, j=code%n).
            for (int p = 0; p < maxBucket; p++) bktSz[p] = 0;
            int topBucket = 0;
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (grid[i][j] == '.') {
                        int p = neighBlacks[i][j];
                        // Grow the bucket's backing array if full (amortised O(1)).
                        if (bktSz[p] == bkt[p].length) bkt[p] = java.util.Arrays.copyOf(bkt[p], bkt[p].length * 2);
                        bkt[p][bktSz[p]++] = i * n + j;
                        if (p > topBucket) topBucket = p;
                    }

            // ── Phase 2: greedy fill ──────────────────────────────────────────────
            // Each iteration we place the white cell with the highest neighBlacks into
            // black, because that maximises the number of new black–black pairs created
            // by this single placement.  We stop when no eligible cell remains.
            while (true) {
                // Walk topBucket downward until we find a non-empty bucket.
                while (topBucket >= 0 && bktSz[topBucket] == 0) topBucket--;
                if (topBucket < 0) break; // no candidates left → done filling

                // Pick a random entry from the top bucket (swap-and-pop, O(1)).
                // Randomness here ensures different greedy paths across iterations
                // even when many cells tie on neighBlacks, which is common.
                int idx = rand.nextInt(bktSz[topBucket]);
                int code = bkt[topBucket][idx];
                bkt[topBucket][idx] = bkt[topBucket][--bktSz[topBucket]];
                int pi = code / n, pj = code % n;

                // ── Lazy deletion ─────────────────────────────────────────────────
                // The queue may contain stale entries because:
                //   (a) the cell was already placed ('#') earlier in this fill pass
                //   (b) a previous placement made it ineligible (too few whites / too
                //       many blacks / a blocker appeared in its window)
                //   (c) its neighBlacks increased after insertion, so there is a newer
                //       entry in a higher bucket (only relevant in lazy-reinsert mode)
                // We discard (a) and (b) silently, and handle (c) by re-inserting at
                // the correct bucket so it can be picked up in a future pop.
                if (grid[pi][pj] != '.') continue;
                if (neighWhites[pi][pj] < b || neighBlacks[pi][pj] > c || blockingCount[pi][pj] > 0) continue;
                if (neighBlacks[pi][pj] != topBucket) {
                    // Case (c): newer/higher-priority entry already exists for this cell
                    // in propagation mode.  In lazy mode we must re-insert it ourselves.
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
                // Adding neighBlacks[pi][pj] to the score is equivalent to: for every
                // existing black neighbour, one new pair is formed.  This is the
                // incremental version of counting pairs — no full recount needed.
                currentScore += neighBlacks[pi][pj];
                grid[pi][pj] = '#';

                // Amortised time check: calling System.currentTimeMillis() on every
                // placement would add significant overhead.  Checking every 1024
                // placements (bitmask trick) is cheap and precise enough.
                if ((++placements & 1023) == 0 && System.currentTimeMillis() - startTime >= timeLimit) {
                    timeUp = true;
                    break;
                }

                // ── Propagate the placement to all cells in the window ────────────
                // Every cell (k,l) within Chebyshev distance d of (pi,pj) now has one
                // more black neighbour and one fewer white neighbour.
                int r0 = Math.max(0,pi-d), r1 = Math.min(n-1,pi+d);
                int c0 = Math.max(0,pj-d), c1 = Math.min(n-1,pj+d);
                for (int k = r0; k <= r1; k++)
                    for (int l = c0; l <= c1; l++) {
                        if (k==pi && l==pj) continue;
                        neighBlacks[k][l]++;
                        neighWhites[k][l]--;

                        // In propagation-reinsert mode: the white cell (k,l) just gained
                        // a black neighbour, so its priority increased.  Re-insert it at
                        // the new (higher) bucket so future pops see the updated priority.
                        // In lazy mode this is skipped — the stale entry will be
                        // re-inserted when it is eventually popped (see lazy deletion above).
                        if (usePropReinsert && grid[k][l] == '.') {
                            int p = neighBlacks[k][l];
                            if (p < maxBucket) {
                                if (bktSz[p] == bkt[p].length) bkt[p] = java.util.Arrays.copyOf(bkt[p], bkt[p].length * 2);
                                bkt[p][bktSz[p]++] = k * n + l;
                                if (p > topBucket) topBucket = p;
                            }
                        }

                        // If an existing black cell (k,l) just became "blocking" (i.e.
                        // its neighBlacks hit the limit c, or its neighWhites hit b),
                        // mark it and increment blockingCount for every cell in its window.
                        // Those cells can no longer be flipped to black without violating
                        // the constraint of (k,l).
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

            if (currentScore > bestScore) {
                bestScore = currentScore;
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        bestGrid[i][j] = grid[i][j];
            }

            // ── Phase 3 (every 20 iters): hard reset to best ─────────────────────
            // After many ruin-rebuild cycles the working grid drifts away from the
            // global best and incremental errors in blockingCount can accumulate.
            // Resetting to bestGrid gives a clean, verified starting point and
            // forces exploration of a different region of the search space.
            // Skipped for large cases (n²×window ≥ 200M) where the full
            // blockingCount recompute below would take too long.
            if (useReset && iters % 20 == 0) {
                currentScore = bestScore;
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++) {
                        char prev = grid[i][j];
                        char next = bestGrid[i][j];
                        if (prev == next) continue;
                        // Before changing the cell, undo its blocking contribution if any,
                        // so blockingCount stays correct while we're mid-reset.
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
                // After touching many cells the incremental blockingCount updates above
                // may have small errors (cells changed order matters for blocking status).
                // Do a full clean recompute from the settled grid state.
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
                continue; // skip ruin this iteration — go straight to next fill
            }

            // ── Phase 4: ruin ─────────────────────────────────────────────────────
            // Erase a random rectangular block of black cells from the working grid.
            // Block size is chosen randomly between n/8 and n/3, giving both fine and
            // coarse perturbations.  Only cells that were added by the algorithm
            // (not pre-existing '#' in the original input) are removed.
            // All neighbour counts and blocking state are updated incrementally.
            int minBlock = Math.max(2, n / 8);
            int maxBlock = Math.max(minBlock + 1, n / 3);
            int blockSize = minBlock + rand.nextInt(maxBlock - minBlock + 1);
            int startI = rand.nextInt(Math.max(1, n - blockSize + 1));
            int startJ = rand.nextInt(Math.max(1, n - blockSize + 1));

            for (int i = startI; i < startI + blockSize && i < n; i++)
                for (int j = startJ; j < startJ + blockSize && j < n; j++)
                    if (grid[i][j] == '#' && gridCase.grid.get(i).get(j) != '#') {
                        // Remove the pair contribution of this cell before erasing it.
                        // neighBlacks[i][j] equals the number of existing black neighbours,
                        // each of which forms one pair with (i,j) — sum = pairs lost.
                        currentScore -= neighBlacks[i][j];

                        // If this cell was blocking its neighbours, undo that contribution
                        // before we remove it so blockingCount stays accurate.
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
                                // A black neighbour (k,l) that was previously blocking
                                // may now be safe again because it just lost a black
                                // neighbour (us).  If it no longer violates either
                                // constraint, un-mark it and decrement blockingCount
                                // for everything in its window.
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
        // ── End main loop ─────────────────────────────────────────────────────────

        // Write the best grid back into gridCase so the caller can display/verify it.
        for (int i = 0; i < n; i++) {
            List<Character> row = gridCase.grid.get(i);
            for (int j = 0; j < n; j++)
                row.set(j, bestGrid[i][j]);
        }
        return bestScore;
    }

    /**
     * Validity per statement: every BLACK cell must have at least b nearby WHITE
     * cells ('.')
     * and at most c nearby BLACK cells ('#') within Chebyshev distance d (excluding
     * itself).
     * Red cells ('R') are ignored (neither white nor black).
     */
    private static boolean checkGrid(GridCase grids) {
        List<List<Character>> grid = grids.grid;
        int n = grids.n;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (grid.get(i).get(j) != '#') {
                    continue;
                }

                int blackNearby = 0;
                int whiteNearby = 0;

                int r0 = Math.max(0, i - grids.d);
                int r1 = Math.min(n - 1, i + grids.d);
                int c0 = Math.max(0, j - grids.d);
                int c1 = Math.min(n - 1, j + grids.d);

                for (int r = r0; r <= r1; r++) {
                    List<Character> row = grid.get(r);
                    for (int c = c0; c <= c1; c++) {
                        if (r == i && c == j) {
                            continue;
                        }
                        char cell = row.get(c);
                        if (cell == '#') {
                            blackNearby++;
                        } else if (cell == '.') {
                            whiteNearby++;
                        }
                    }
                }

                if (whiteNearby < grids.b) {
                    System.out.printf("Not enough white cells around BLACK [%d, %d]: %d < %d\n", i, j, whiteNearby,
                            grids.b);
                    return false;
                }
                if (blackNearby > grids.c) {
                    System.out.printf("Too many black cells around BLACK [%d, %d]: %d > %d\n", i, j, blackNearby,
                            grids.c);
                    return false;
                }
            }
        }
        return true;
    }

    private static long countCloseBlackPairs(GridCase gridCase) {
        List<List<Character>> grid = gridCase.grid;
        int n = gridCase.n;
        long pairs = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (grid.get(i).get(j) != '#') {
                    continue;
                }
                int r0 = Math.max(0, i - gridCase.d);
                int r1 = Math.min(n - 1, i + gridCase.d);
                int c0 = Math.max(0, j - gridCase.d);
                int c1 = Math.min(n - 1, j + gridCase.d);
                for (int r = r0; r <= r1; r++) {
                    List<Character> row = grid.get(r);
                    for (int c = c0; c <= c1; c++) {
                        if (r == i && c == j) {
                            continue;
                        }
                        if (row.get(c) == '#') {
                            pairs++;
                        }
                    }
                }
            }
        }
        return pairs / 2;
    }

    private static void displayGrid(GridCase grids) {
        System.out.printf(grids.getNumbers());
        for (List<Character> row : grids.grid) {
            for (char cell : row) {
                switch (cell) {
                    case '.':
                        System.out.print("⬜ ");
                        break;
                    case '#':
                        System.out.print("\u2B1B ");
                        break;
                    case 'R':
                        System.out.print("🟥 ");
                        break;
                    default:
                        System.out.print(cell + " ");
                }
            }
            System.out.println();
        }
        System.out.println();
    }
}
