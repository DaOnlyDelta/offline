import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        int i = 1;
        for (GridCase gridCase : grids) {
            System.out.println("Initial grid:");
            if (!checkGrid(gridCase)) {
                System.out.println("Initial grid is invalid!");
                continue;
            }
            displayGrid(gridCase);
            algorithm(gridCase);
            displayGrid(gridCase);
            System.out.println(i + ": " + countCloseBlackPairs(gridCase));
            return;
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

    private static void algorithm(GridCase gridCase) {
        int n = gridCase.n; // Grid size
        int d = gridCase.d; // Neighbour range
        int b = gridCase.b; // Min neighbouring white blocks
        int c = gridCase.c; // Max neighbouring black blocks

        char[][] grid = new char[n][n];
        for (int i = 0; i < n; i++) {
            List<Character> row = gridCase.grid.get(i);
            for (int j = 0; j < n; j++) {
                grid[i][j] = row.get(j);
            }
        }

        int[][] neighBlacks = new int[n][n];
        int[][] neighWhites = new int[n][n];
        int upperLimit, lowerLimit, leftLimit, rightLimit, bCounter, wCounter, totalPairs = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                // Calculate the neighbour limits
                upperLimit = Math.max(0, i - d);
                lowerLimit = Math.min(n - 1, i + d);
                leftLimit = Math.max(0, j - d);
                rightLimit = Math.min(n - 1, j + d);
                bCounter = 0;
                wCounter = 0;

                for (int k = upperLimit; k <= lowerLimit; k++) {
                    for (int l = leftLimit; l <= rightLimit; l++) {
                        if (k == i && l == j)
                            continue; // Skip itself
                        switch (grid[k][l]) {
                            case 'R' -> {/* ignore */}
                            case '#' -> {bCounter++;}
                            case '.' -> {wCounter++;}
                        }
                    }
                }

                neighBlacks[i][j] = bCounter;
                neighWhites[i][j] = wCounter;
            }
        }

        int bestI, bestJ, bestBlackNeighbors;
        boolean canFlip;
        while (true) {
            bestI = -1;
            bestJ = -1;
            bestBlackNeighbors = -1;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    // Skip non-white cells
                    if (grid[i][j] != '.') continue;

                    // Skip cells that don't have enough white neighbors
                    if (neighWhites[i][j] < b) continue;

                    // Skip cells that already have too many black neighbors
                    if (neighBlacks[i][j] > c) continue;

                    // Check if flipping would violate neighbors' constraints
                    canFlip = true;
                    upperLimit = Math.max(0, i - d);
                    lowerLimit = Math.min(n - 1, i + d);
                    leftLimit = Math.max(0, j - d);
                    rightLimit = Math.min(n - 1, j + d);

                    for (int k = upperLimit; k <= lowerLimit && canFlip; k++) {
                        for (int l = leftLimit; l <= rightLimit && canFlip; l++) {
                            if (grid[k][l] == '#') {
                                // After flip, this black neighbor gains one black neighbor
                                // and loses one white neighbor
                                if (neighBlacks[k][l] + 1 > c || neighWhites[k][l] - 1 < b) {
                                    canFlip = false;
                                }
                            }
                        }
                    }

                    if (!canFlip) continue;

                    // Choose cell with most black neighbors (maximizes pair gain)
                    if (neighBlacks[i][j] > bestBlackNeighbors) {
                        bestBlackNeighbors = neighBlacks[i][j];
                        bestI = i;
                        bestJ = j;
                    }
                }
            }

            // Flip the best candidate if found
            if (bestI == -1) break;

            grid[bestI][bestJ] = '#';

            // Update neighbor counts for affected cells
            upperLimit = Math.max(0, bestI - d);
            lowerLimit = Math.min(n - 1, bestI + d);
            leftLimit = Math.max(0, bestJ - d);
            rightLimit = Math.min(n - 1, bestJ + d);

            for (int k = upperLimit; k <= lowerLimit; k++) {
                for (int l = leftLimit; l <= rightLimit; l++) {
                    if (k == bestI && l == bestJ) continue;
                    neighBlacks[k][l]++;
                    neighWhites[k][l]--;
                }
            }

            // Add pairs created by this flip
            totalPairs += bestBlackNeighbors;
        }

        // copy back to gridCase.grid
        for (int i = 0; i < n; i++) {
            List<Character> row = gridCase.grid.get(i);
            for (int j = 0; j < n; j++) {
                row.set(j, grid[i][j]);
            }
        }
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