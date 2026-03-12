import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Test {

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
        List<GridCase> grids = getGrids("Barvanje_solution.txt");
        for (int i = 0; i < grids.size(); i++) {
            if (!checkGrid(grids.get(i))) {
                System.out.println(i+ " is invalid!");
            }
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
}
