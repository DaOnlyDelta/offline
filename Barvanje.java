import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class Barvanje {

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

    public static void main(String[] args) {
        List<GridCase> grids = getGrids("Barvanje.txt");
        for (GridCase gridCase : grids) {
            System.out.println("Initial grid:");
            if (!checkGrid(gridCase)) {
                System.out.println("Initial grid is invalid!");
                continue;
            }
            displayGrid(gridCase);

            secondTry(gridCase);

            System.out.println("After:");
            displayGrid(gridCase);
            boolean afterValid = checkGrid(gridCase);
            System.out.printf("After valid: %s\n", afterValid);
            System.out.printf("After close-black pairs: %d\n\n", countCloseBlackPairs(gridCase));
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

    /**
     * Greedy heuristic:
     * - each time, pick a WHITE cell that can be flipped to BLACK while keeping the
     * grid valid
     * - prefer the flip that creates the most new close-black pairs (i.e. most
     * nearby blacks)
     *
     * Uses incremental neighbor counts so each accepted flip costs O((2d+1)^2).
     */
    private static void firstTry(GridCase gridCase) {
        int n = gridCase.n;
        int d = gridCase.d;
        int b = gridCase.b;
        int c = gridCase.c;

        List<List<Character>> grid = gridCase.grid;

        int[][] blackNearby = new int[n][n];
        int[][] whiteNearby = new int[n][n];

        // Initialize neighbor counts (Chebyshev distance <= d, excluding self)
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int r0 = Math.max(0, i - d);
                int r1 = Math.min(n - 1, i + d);
                int c0 = Math.max(0, j - d);
                int c1 = Math.min(n - 1, j + d);
                int bn = 0;
                int wn = 0;
                for (int r = r0; r <= r1; r++) {
                    List<Character> row = grid.get(r);
                    for (int col = c0; col <= c1; col++) {
                        if (r == i && col == j) {
                            continue;
                        }
                        char cell = row.get(col);
                        if (cell == '#') {
                            bn++;
                        } else if (cell == '.') {
                            wn++;
                        }
                    }
                }
                blackNearby[i][j] = bn;
                whiteNearby[i][j] = wn;
            }
        }

        class Candidate {
            final int i;
            final int j;
            final int score;

            Candidate(int i, int j, int score) {
                this.i = i;
                this.j = j;
                this.score = score;
            }
        }

        PriorityQueue<Candidate> pq = new PriorityQueue<>(Comparator.comparingInt((Candidate x) -> x.score).reversed());
        boolean[][] dead = new boolean[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (grid.get(i).get(j) == '.') {
                    pq.add(new Candidate(i, j, blackNearby[i][j]));
                }
            }
        }

        int flips = 0;
        while (!pq.isEmpty()) {
            Candidate cand = pq.poll();
            int i = cand.i;
            int j = cand.j;

            if (dead[i][j]) {
                continue;
            }
            if (grid.get(i).get(j) != '.') {
                continue;
            }

            int currentScore = blackNearby[i][j];
            if (currentScore != cand.score) {
                pq.add(new Candidate(i, j, currentScore));
                continue;
            }

            // New black cell must satisfy its own constraints
            if (whiteNearby[i][j] < b || blackNearby[i][j] > c) {
                dead[i][j] = true;
                continue;
            }

            // Existing black cells in neighborhood must remain valid after this flip
            int r0 = Math.max(0, i - d);
            int r1 = Math.min(n - 1, i + d);
            int c0 = Math.max(0, j - d);
            int c1 = Math.min(n - 1, j + d);

            boolean ok = true;
            outer: for (int r = r0; r <= r1; r++) {
                List<Character> row = grid.get(r);
                for (int col = c0; col <= c1; col++) {
                    if (r == i && col == j) {
                        continue;
                    }
                    if (row.get(col) != '#') {
                        continue;
                    }
                    if (whiteNearby[r][col] - 1 < b) {
                        ok = false;
                        break outer;
                    }
                    if (blackNearby[r][col] + 1 > c) {
                        ok = false;
                        break outer;
                    }
                }
            }

            if (!ok) {
                dead[i][j] = true;
                continue;
            }

            // Apply flip: '.' -> '#'
            grid.get(i).set(j, '#');
            flips++;

            // Update counts for all cells that consider (i,j) as neighbor
            for (int r = r0; r <= r1; r++) {
                for (int col = c0; col <= c1; col++) {
                    if (r == i && col == j) {
                        continue;
                    }
                    blackNearby[r][col] += 1;
                    whiteNearby[r][col] -= 1;
                    if (!dead[r][col] && grid.get(r).get(col) == '.') {
                        // Reinsert with updated score (lazy PQ update)
                        pq.add(new Candidate(r, col, blackNearby[r][col]));
                    }
                }
            }
        }

        System.out.printf("firstTry flips applied: %d\n", flips);
        System.out.printf("firstTry valid: %s\n", checkGrid(gridCase));
    }

    /**
     * Different approach: randomized greedy (GRASP-style).
     *
     * Instead of always taking the single best move, we repeatedly:
     * - form a small Restricted Candidate List (RCL) of top-scoring feasible-ish
     * moves
     * - pick a random move from that RCL
     *
     * Repeat the whole construction multiple times and keep the best solution
     * found.
     */
    private static void secondTry(GridCase gridCase) {
        int n = gridCase.n;
        int d = gridCase.d;
        int b = gridCase.b;
        int c = gridCase.c;

        // Snapshot initial grid (so we can do many independent runs)
        char[][] base = new char[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                base[i][j] = gridCase.grid.get(i).get(j);
            }
        }

        class Candidate {
            final int idx;
            final int score;

            Candidate(int idx, int score) {
                this.idx = idx;
                this.score = score;
            }
        }

        // Parameters you can tune
        int iterations = 200;
        int rclSize = 8;
        Random rng = new Random(System.nanoTime());

        long bestPairs = -1;
        char[][] bestGrid = null;
        int bestFlips = 0;

        // Helper to compute neighbor counts for a given grid
        // (counts are for all cells, but constraints are checked only for black cells)
        for (int it = 0; it < iterations; it++) {
            char[][] g = new char[n][n];
            for (int i = 0; i < n; i++) {
                System.arraycopy(base[i], 0, g[i], 0, n);
            }

            int[][] blackNearby = new int[n][n];
            int[][] whiteNearby = new int[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int r0 = Math.max(0, i - d);
                    int r1 = Math.min(n - 1, i + d);
                    int c0 = Math.max(0, j - d);
                    int c1 = Math.min(n - 1, j + d);
                    int bn = 0;
                    int wn = 0;
                    for (int r = r0; r <= r1; r++) {
                        for (int col = c0; col <= c1; col++) {
                            if (r == i && col == j) {
                                continue;
                            }
                            char cell = g[r][col];
                            if (cell == '#') {
                                bn++;
                            } else if (cell == '.') {
                                wn++;
                            }
                        }
                    }
                    blackNearby[i][j] = bn;
                    whiteNearby[i][j] = wn;
                }
            }

            PriorityQueue<Candidate> pq = new PriorityQueue<>(
                    Comparator.comparingInt((Candidate x) -> x.score).reversed());
            boolean[] dead = new boolean[n * n];

            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (g[i][j] == '.') {
                        int idx = i * n + j;
                        pq.add(new Candidate(idx, blackNearby[i][j]));
                    }
                }
            }

            // Used to avoid duplicates while pulling an RCL
            int[] seenStamp = new int[n * n];
            int stamp = 1;

            int flips = 0;
            while (!pq.isEmpty()) {
                stamp++;
                if (stamp == Integer.MAX_VALUE) {
                    stamp = 1;
                    for (int k = 0; k < seenStamp.length; k++) {
                        seenStamp[k] = 0;
                    }
                }

                ArrayList<Candidate> rcl = new ArrayList<>(rclSize);
                ArrayList<Candidate> pulled = new ArrayList<>(rclSize);

                // Pull up to rclSize distinct current-best candidates
                while (!pq.isEmpty() && rcl.size() < rclSize) {
                    Candidate cand = pq.poll();
                    pulled.add(cand);

                    int idx = cand.idx;
                    if (dead[idx]) {
                        continue;
                    }
                    if (seenStamp[idx] == stamp) {
                        continue;
                    }
                    int i = idx / n;
                    int j = idx % n;
                    if (g[i][j] != '.') {
                        continue;
                    }

                    int currentScore = blackNearby[i][j];
                    if (currentScore != cand.score) {
                        pq.add(new Candidate(idx, currentScore));
                        continue;
                    }
                    seenStamp[idx] = stamp;
                    rcl.add(cand);
                }

                // Push back non-used pulled elements (except ones we already reinserted with
                // updated score)
                for (Candidate cand : pulled) {
                    // If it's still eligible, it will already be in pq; pushing duplicates is ok
                    // (lazy PQ).
                    // We only need to ensure we don't lose candidates by emptying pq too
                    // aggressively.
                    pq.add(cand);
                }

                if (rcl.isEmpty()) {
                    break;
                }

                Candidate chosen = rcl.get(rng.nextInt(rcl.size()));
                int idx = chosen.idx;
                int i = idx / n;
                int j = idx % n;

                if (dead[idx] || g[i][j] != '.') {
                    continue;
                }

                // Basic feasibility for the new black cell itself
                if (whiteNearby[i][j] < b || blackNearby[i][j] > c) {
                    dead[idx] = true;
                    continue;
                }

                // Check neighborhood black cells constraints after flip
                int r0 = Math.max(0, i - d);
                int r1 = Math.min(n - 1, i + d);
                int c0 = Math.max(0, j - d);
                int c1 = Math.min(n - 1, j + d);
                boolean ok = true;
                outer: for (int r = r0; r <= r1; r++) {
                    for (int col = c0; col <= c1; col++) {
                        if (r == i && col == j) {
                            continue;
                        }
                        if (g[r][col] != '#') {
                            continue;
                        }
                        if (whiteNearby[r][col] - 1 < b) {
                            ok = false;
                            break outer;
                        }
                        if (blackNearby[r][col] + 1 > c) {
                            ok = false;
                            break outer;
                        }
                    }
                }
                if (!ok) {
                    dead[idx] = true;
                    continue;
                }

                // Apply flip
                g[i][j] = '#';
                flips++;

                // Update counts for all cells that see (i,j) as neighbor
                for (int r = r0; r <= r1; r++) {
                    for (int col = c0; col <= c1; col++) {
                        if (r == i && col == j) {
                            continue;
                        }
                        blackNearby[r][col] += 1;
                        whiteNearby[r][col] -= 1;
                        if (!dead[r * n + col] && g[r][col] == '.') {
                            pq.add(new Candidate(r * n + col, blackNearby[r][col]));
                        }
                    }
                }
            }

            // Score this run
            long pairs = 0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (g[i][j] != '#') {
                        continue;
                    }
                    int r0 = Math.max(0, i - d);
                    int r1 = Math.min(n - 1, i + d);
                    int c0 = Math.max(0, j - d);
                    int c1 = Math.min(n - 1, j + d);
                    for (int r = r0; r <= r1; r++) {
                        for (int col = c0; col <= c1; col++) {
                            if (r == i && col == j) {
                                continue;
                            }
                            if (g[r][col] == '#') {
                                pairs++;
                            }
                        }
                    }
                }
            }
            pairs /= 2;

            if (pairs > bestPairs) {
                bestPairs = pairs;
                bestFlips = flips;
                bestGrid = g;
            }
        }

        // Write best found solution back into the List<List<Character>> grid
        if (bestGrid != null) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    gridCase.grid.get(i).set(j, bestGrid[i][j]);
                }
            }
        }

        System.out.printf("secondTry best close-black pairs: %d\n", bestPairs);
        System.out.printf("secondTry valid: %s\n", checkGrid(gridCase));
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