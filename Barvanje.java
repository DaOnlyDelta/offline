import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

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
        // Extract grid dimension
        int n = gridCase.n;
        // Extract distance constraint (how far to look for neighbors)
        int d = gridCase.d;
        // Extract minimum white neighbors constraint
        int b = gridCase.b;
        // Extract maximum black neighbors constraint
        int c = gridCase.c;

        // Create a snapshot of the initial grid as a 2D char array
        // This will be used as the starting point for each independent iteration
        char[][] base = new char[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                base[i][j] = gridCase.grid.get(i).get(j);
            }
        }

        // Inner class to represent a candidate cell that can be flipped to black
        class Candidate {
            // The linear index of the cell (i*n + j)
            final int idx;
            // The score of this candidate (how many black neighbors it already has)
            final int score;

            Candidate(int idx, int score) {
                this.idx = idx;
                this.score = score;
            }
        }

        // Number of complete construction iterations to run
        int iterations = 200;
        // Maximum size of the Restricted Candidate List (top candidates to pick from)
        int rclSize = 8;

        // Track the best solution found across all iterations
        // Initialize to -1 (no solution found yet)
        long bestPairs = -1;
        // The grid that achieved the best score
        char[][] bestGrid = null;

        // Run the randomized construction process 200 times
        for (int it = 0; it < iterations; it++) {
            // Create a fresh copy of the base grid for this iteration
            char[][] g = new char[n][n];
            // Loop through each row
            for (int i = 0; i < n; i++) {
                // Use System.arraycopy to efficiently copy the entire row at once
                System.arraycopy(base[i], 0, g[i], 0, n);
            }

            // 2D array to track how many black cells are near each cell (within distance d)
            int[][] blackNearby = new int[n][n];
            // 2D array to track how many white cells are near each cell (within distance d)
            int[][] whiteNearby = new int[n][n];
            // Loop through each row
            for (int i = 0; i < n; i++) {
                // Loop through each column
                for (int j = 0; j < n; j++) {
                    // Calculate the top boundary of the search region (don't go above row 0)
                    int r0 = Math.max(0, i - d);
                    // Calculate the bottom boundary of the search region (don't go below row n-1)
                    int r1 = Math.min(n - 1, i + d);
                    // Calculate the left boundary of the search region (don't go left of column 0)
                    int c0 = Math.max(0, j - d);
                    // Calculate the right boundary of the search region (don't go right of column n-1)
                    int c1 = Math.min(n - 1, j + d);
                    // Counter for black cells in the neighborhood
                    int bn = 0;
                    // Counter for white cells in the neighborhood
                    int wn = 0;
                    // Loop through all rows in the search region
                    for (int r = r0; r <= r1; r++) {
                        // Loop through all columns in the search region
                        for (int col = c0; col <= c1; col++) {
                            // Skip the cell itself (we don't count it as a neighbor)
                            if (r == i && col == j) {
                                continue;
                            }
                            // Get the character at this neighboring cell
                            char cell = g[r][col];
                            // If it's a black cell, increment the black counter
                            if (cell == '#') {
                                bn++;
                            }
                            // If it's a white cell, increment the white counter
                            else if (cell == '.') {
                                wn++;
                            }
                        }
                    }
                    // Store the count of black neighbors for cell (i,j)
                    blackNearby[i][j] = bn;
                    // Store the count of white neighbors for cell (i,j)
                    whiteNearby[i][j] = wn;
                }
            }

            // Create a priority queue that orders candidates by score (highest first)
            PriorityQueue<Candidate> pq = new PriorityQueue<>(
                    // Comparator that compares candidates by their score in descending order
                    Comparator.comparingInt((Candidate x) -> x.score).reversed());
            // Array to mark cells that are permanently infeasible (dead)
            boolean[] dead = new boolean[n * n];

            // Loop through each row
            for (int i = 0; i < n; i++) {
                // Loop through each column
                for (int j = 0; j < n; j++) {
                    // Only add white cells as candidates for flipping to black
                    if (g[i][j] == '.') {
                        // Convert 2D coordinates to linear index
                        int idx = i * n + j;
                        // Add the candidate with score = number of black neighbors it has
                        pq.add(new Candidate(idx, blackNearby[i][j]));
                    }
                }
            }

            // Array that tracks which cells have been seen in the current RCL extraction
            // Uses a "stamping" technique to avoid resetting the whole array each iteration
            int[] seenStamp = new int[n * n];
            // Current "stamp" value - incremented each time we build a new RCL
            int stamp = 1;

            // Main loop: continue until priority queue is empty or no valid moves remain
            while (!pq.isEmpty()) {
                // Increment the stamp to mark a new RCL extraction phase
                stamp++;
                // If stamp reaches max integer value, reset it to avoid overflow
                if (stamp == Integer.MAX_VALUE) {
                    // Set stamp back to 1
                    stamp = 1;
                    // Reset all seen stamps to 0
                    for (int k = 0; k < seenStamp.length; k++) {
                        seenStamp[k] = 0;
                    }
                }

                // Create the Restricted Candidate List (RCL) for this iteration
                ArrayList<Candidate> rcl = new ArrayList<>(rclSize);
                // Track all candidates we pulled from the queue (to push back later)
                ArrayList<Candidate> pulled = new ArrayList<>(rclSize);

                // Extract up to rclSize best candidates from the priority queue
                while (!pq.isEmpty() && rcl.size() < rclSize) {
                    // Pull the highest-scoring candidate from the queue
                    Candidate cand = pq.poll();
                    // Track that we pulled this candidate
                    pulled.add(cand);

                    // Get the linear index of this candidate
                    int idx = cand.idx;
                    // If this cell is marked as dead (infeasible), skip it
                    if (dead[idx]) {
                        continue;
                    }
                    // If this cell is already in the current RCL, skip it (avoid duplicates)
                    if (seenStamp[idx] == stamp) {
                        continue;
                    }
                    // Convert linear index back to 2D coordinates
                    int i = idx / n;
                    // Convert linear index back to 2D coordinates
                    int j = idx % n;
                    // If the cell is no longer white, skip it (must have been changed)
                    if (g[i][j] != '.') {
                        continue;
                    }

                    // Get the current score of this cell (number of black neighbors now)
                    int currentScore = blackNearby[i][j];
                    // If the candidate's score is stale (doesn't match current state)
                    if (currentScore != cand.score) {
                        // Re-insert it with the updated current score
                        pq.add(new Candidate(idx, currentScore));
                        // Skip adding to RCL; we'll reconsider it on next pull
                        continue;
                    }
                    // Mark this cell as seen in this RCL extraction
                    seenStamp[idx] = stamp;
                    // Add the valid candidate to the RCL
                    rcl.add(cand);
                }

                // Push all pulled candidates back into the priority queue
                for (Candidate cand : pulled) {
                    // Add back to queue (duplicates in PQ are acceptable; they just get skipped when pulled)
                    pq.add(cand);
                }

                // If the RCL is empty, no more valid moves are available
                if (rcl.isEmpty()) {
                    // Exit the main loop
                    break;
                }

                // Randomly pick one candidate from the RCL (the randomization step)
                Candidate chosen = rcl.get((int)(Math.random() * rcl.size()));
                // Get the linear index of the chosen candidate
                int idx = chosen.idx;
                // Convert to 2D row coordinate
                int i = idx / n;
                // Convert to 2D column coordinate
                int j = idx % n;

                // Double-check the cell is still valid before flipping
                if (dead[idx] || g[i][j] != '.') {
                    // If not, skip this iteration of the main loop
                    continue;
                }

                // Check if the new black cell itself satisfies constraints:
                // Must have at least b white neighbors
                // Must have at most c black neighbors
                if (whiteNearby[i][j] < b || blackNearby[i][j] > c) {
                    // Mark this cell as dead (permanently infeasible)
                    dead[idx] = true;
                    // Skip the flip
                    continue;
                }

                // Calculate the search region for nearby cells
                // Top boundary
                int r0 = Math.max(0, i - d);
                // Bottom boundary
                int r1 = Math.min(n - 1, i + d);
                // Left boundary
                int c0 = Math.max(0, j - d);
                // Right boundary
                int c1 = Math.min(n - 1, j + d);
                // Flag to track if all neighboring black cells remain valid after flip
                boolean ok = true;
                // Labeled loop (so we can break out of nested loops)
                outer: for (int r = r0; r <= r1; r++) {
                    // Loop through all columns in the region
                    for (int col = c0; col <= c1; col++) {
                        // Skip the cell being flipped
                        if (r == i && col == j) {
                            continue;
                        }
                        // Only check cells that are currently black
                        if (g[r][col] != '#') {
                            continue;
                        }
                        // After we flip (i,j) to black, this neighbor will lose one white neighbor
                        // Check if it will still have at least b white neighbors
                        if (whiteNearby[r][col] - 1 < b) {
                            // It won't; flip is infeasible
                            ok = false;
                            // Break out of both loops
                            break outer;
                        }
                        // After we flip (i,j) to black, this neighbor will gain one black neighbor
                        // Check if it will still have at most c black neighbors
                        if (blackNearby[r][col] + 1 > c) {
                            // It won't; flip is infeasible
                            ok = false;
                            // Break out of both loops
                            break outer;
                        }
                    }
                }
                // If any neighboring black cell would be violated
                if (!ok) {
                    // Mark this cell as dead
                    dead[idx] = true;
                    // Skip the flip
                    continue;
                }

                // All checks passed; now apply the flip
                // Change the cell from white to black
                g[i][j] = '#';

                // Update neighbor counts for all cells near (i,j)
                // Loop through all rows in the affected region
                for (int r = r0; r <= r1; r++) {
                    // Loop through all columns in the affected region
                    for (int col = c0; col <= c1; col++) {
                        // Skip the cell being flipped
                        if (r == i && col == j) {
                            continue;
                        }
                        // Increment black neighbor count (since we added a black cell nearby)
                        blackNearby[r][col] += 1;
                        // Decrement white neighbor count (since we removed a white cell nearby)
                        whiteNearby[r][col] -= 1;
                        // If this is a valid white cell candidate and not dead
                        if (!dead[r * n + col] && g[r][col] == '.') {
                            // Re-add it to the queue with updated score
                            pq.add(new Candidate(r * n + col, blackNearby[r][col]));
                        }
                    }
                }
            }

            // Now score this iteration by counting close-black pairs
            // Initialize pair counter to zero
            long pairs = 0;
            // Loop through each row
            for (int i = 0; i < n; i++) {
                // Loop through each column
                for (int j = 0; j < n; j++) {
                    // Only count for black cells
                    if (g[i][j] != '#') {
                        continue;
                    }
                    // Calculate search region boundaries
                    int r0 = Math.max(0, i - d);
                    // Calculate search region boundaries
                    int r1 = Math.min(n - 1, i + d);
                    // Calculate search region boundaries
                    int c0 = Math.max(0, j - d);
                    // Calculate search region boundaries
                    int c1 = Math.min(n - 1, j + d);
                    // Loop through all rows in the region
                    for (int r = r0; r <= r1; r++) {
                        // Loop through all columns in the region
                        for (int col = c0; col <= c1; col++) {
                            // Skip the cell itself
                            if (r == i && col == j) {
                                continue;
                            }
                            // If the neighbor is also black, increment pair count
                            if (g[r][col] == '#') {
                                pairs++;
                            }
                        }
                    }
                }
            }
            // Divide by 2 because each pair is counted twice (once from each endpoint)
            pairs /= 2;

            // If this iteration's score is better than the best so far
            if (pairs > bestPairs) {
                // Update best score
                bestPairs = pairs;
                // Update best grid (store reference to this grid)
                bestGrid = g;
            }
        }

        // Write the best solution found back into the original grid structure
        if (bestGrid != null) {
            // Loop through each row
            for (int i = 0; i < n; i++) {
                // Loop through each column
                for (int j = 0; j < n; j++) {
                    // Copy the char array value into the List<List<Character>> structure
                    gridCase.grid.get(i).set(j, bestGrid[i][j]);
                }
            }
        }

        // Print the best score found
        System.out.printf("secondTry best close-black pairs: %d\n", bestPairs);
        // Verify that the result is a valid grid
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